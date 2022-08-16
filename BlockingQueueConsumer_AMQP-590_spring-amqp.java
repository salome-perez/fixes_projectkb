public class BlockingQueueConsumer {
	public BackOffExecution getBackOffExecution() {
		return this.backOffExecution;
	}

	public void stop() {
		this.cancelled.set(true);
		if (this.consumer != null && this.consumer.getChannel() != null && this.consumerTags.size() > 0
				&& !this.cancelReceived.get()) {
			try {
				RabbitUtils.closeMessageConsumer(this.consumer.getChannel(), this.consumerTags.keySet(),
						this.transactional);
			}
			catch (Exception e) {
				if (logger.isDebugEnabled()) {
					logger.debug("Error closing consumer", e);
				}
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Closing Rabbit Channel: " + this.channel);
		}
		RabbitUtils.setPhysicalCloseRequired(true);
		ConnectionFactoryUtils.releaseResources(this.resourceHolder);
		this.deliveryTags.clear();
		this.consumer = null;
	}

	public void rollbackOnExceptionIfNecessary(Throwable ex) throws Exception {

		boolean ackRequired = !this.acknowledgeMode.isAutoAck() && !this.acknowledgeMode.isManual();
		try {
			if (this.transactional) {
				if (logger.isDebugEnabled()) {
					logger.debug("Initiating transaction rollback on application exception: " + ex);
				}
				RabbitUtils.rollbackIfNecessary(this.channel);
			}
			if (ackRequired) {
				// We should always requeue if the container was stopping
				boolean shouldRequeue = this.defaultRequeuRejected ||
						ex instanceof MessageRejectedWhileStoppingException;
				Throwable t = ex;
				while (shouldRequeue && t != null) {
					if (t instanceof AmqpRejectAndDontRequeueException) {
						shouldRequeue = false;
					}
					t = t.getCause();
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Rejecting messages (requeue=" + shouldRequeue + ")");
				}
				for (Long deliveryTag : this.deliveryTags) {
					// With newer RabbitMQ brokers could use basicNack here...
					this.channel.basicReject(deliveryTag, shouldRequeue);
				}
				if (this.transactional) {
					// Need to commit the reject (=nack)
					RabbitUtils.commitIfNecessary(this.channel);
				}
			}
		} catch (Exception e) {
			logger.error("Application exception overridden by rollback exception", ex);
			throw e;
		} finally {
			this.deliveryTags.clear();
		}
	}

	private void checkShutdown() {
		if (this.shutdown != null) {
			throw Utility.fixStackTrace(this.shutdown);
		}
	}

	public Message nextMessage(long timeout) throws InterruptedException, ShutdownSignalException {
		if (logger.isDebugEnabled()) {
			logger.debug("Retrieving delivery for " + this);
		}
		checkShutdown();
		if (this.missingQueues.size() > 0) {
			checkMissingQueues();
		}
		Message message = handle(this.queue.poll(timeout, TimeUnit.MILLISECONDS));
		if (message == null && this.cancelReceived.get()) {
			throw new ConsumerCancelledException();
		}
		return message;
	}

		public BasicProperties getProperties() {
			return this.properties;
		}

	private Message handle(Delivery delivery) throws InterruptedException {
		if ((delivery == null && this.shutdown != null)) {
			throw this.shutdown;
		}
		if (delivery == null) {
			return null;
		}
		byte[] body = delivery.getBody();
		Envelope envelope = delivery.getEnvelope();

		MessageProperties messageProperties = this.messagePropertiesConverter.toMessageProperties(
				delivery.getProperties(), envelope, "UTF-8");
		messageProperties.setMessageCount(0);
		messageProperties.setConsumerTag(delivery.getConsumerTag());
		messageProperties.setConsumerQueue(this.consumerTags.get(delivery.getConsumerTag()));
		Message message = new Message(body, messageProperties);
		if (logger.isDebugEnabled()) {
			logger.debug("Received message: " + message);
		}
		this.deliveryTags.add(messageProperties.getDeliveryTag());
		return message;
	}

		public Envelope getEnvelope() {
			return this.envelope;
		}

	public void start() throws AmqpException {
		if (logger.isDebugEnabled()) {
			logger.debug("Starting consumer " + this);
		}
		try {
			this.resourceHolder = ConnectionFactoryUtils.getTransactionalResourceHolder(this.connectionFactory, this.transactional);
			this.channel = this.resourceHolder.getChannel();
		}
		catch (AmqpAuthenticationException e) {
			throw new FatalListenerStartupException("Authentication failure", e);
		}
		this.consumer = new InternalConsumer(this.channel);
		this.deliveryTags.clear();
		this.activeObjectCounter.add(this);

		// mirrored queue might be being moved
		int passiveDeclareRetries = this.declarationRetries;
		do {
			try {
				attemptPassiveDeclarations();
				if (passiveDeclareRetries < this.declarationRetries && logger.isInfoEnabled()) {
					logger.info("Queue declaration succeeded after retrying");
				}
				passiveDeclareRetries = 0;
			}
			catch (DeclarationException e) {
				if (passiveDeclareRetries > 0 && this.channel.isOpen()) {
					if (logger.isWarnEnabled()) {
						logger.warn("Queue declaration failed; retries left=" + (passiveDeclareRetries), e);
						try {
							Thread.sleep(this.failedDeclarationRetryInterval);
						}
						catch (InterruptedException e1) {
							Thread.currentThread().interrupt();
						}
					}
				}
				else if (e.getFailedQueues().size() < this.queues.length) {
					if (logger.isWarnEnabled()) {
						logger.warn("Not all queues are available; only listening on those that are - configured: "
								+ Arrays.asList(this.queues) + "; not available: " + e.getFailedQueues());
					}
					this.missingQueues.addAll(e.getFailedQueues());
					this.lastRetryDeclaration = System.currentTimeMillis();
				}
				else {
					this.activeObjectCounter.release(this);
					throw new QueuesNotAvailableException("Cannot prepare queue for listener. "
							+ "Either the queue doesn't exist or the broker will not allow us to use it.", e);
				}
			}
		}
		while (passiveDeclareRetries-- > 0);

		if (!this.acknowledgeMode.isAutoAck()) {
			// Set basicQos before calling basicConsume (otherwise if we are not acking the broker
			// will send blocks of 100 messages)
			try {
				this.channel.basicQos(this.prefetchCount);
			}
			catch (IOException e) {
				this.activeObjectCounter.release(this);
				throw new AmqpIOException(e);
			}
		}


		try {
			for (String queueName : this.queues) {
				if (!this.missingQueues.contains(queueName)) {
					consumeFromQueue(queueName);
				}
			}
		}
		catch (IOException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
	}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
				throws IOException {
			if (logger.isDebugEnabled()) {
				logger.debug("Storing delivery for " + BlockingQueueConsumer.this);
			}
			try {
				BlockingQueueConsumer.this.queue.put(new Delivery(consumerTag, envelope, properties, body));
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

	}

	private static class Delivery {

		private final String consumerTag;

		private final Envelope envelope;

		private final AMQP.BasicProperties properties;

		private final byte[] body;

		public Delivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {//NOSONAR
			this.consumerTag = consumerTag;
			this.envelope = envelope;
			this.properties = properties;
			this.body = body;
		}

		public String getConsumerTag() {
			return this.consumerTag;
		}

		public Envelope getEnvelope() {
			return this.envelope;
		}

		public BasicProperties getProperties() {
			return this.properties;
		}

		public byte[] getBody() {
			return this.body;
		}
	}

	@SuppressWarnings("serial")
	private static class DeclarationException extends AmqpException {

		private DeclarationException() {
			super("Failed to declare queue(s):");
		}

		private DeclarationException(Throwable t) {
			super("Failed to declare queue(s):", t);
		}

		private final List<String> failedQueues = new ArrayList<String>();

		private void addFailedQueue(String queue) {
			this.failedQueues.add(queue);
		}

		private List<String> getFailedQueues() {
			return this.failedQueues;
		}

		@Override
		public String getMessage() {
			return super.getMessage() + this.failedQueues.toString();
		}

	}

	@Override
	public String toString() {
		return "Consumer: tags=[" + (this.consumerTags.toString()) + "], channel=" + this.channel
				+ ", acknowledgeMode=" + this.acknowledgeMode + " local queue size=" + this.queue.size();
	}

	public void rollbackOnExceptionIfNecessary(Throwable ex) throws Exception {

		boolean ackRequired = !this.acknowledgeMode.isAutoAck() && !this.acknowledgeMode.isManual();
		try {
			if (this.transactional) {
				if (logger.isDebugEnabled()) {
					logger.debug("Initiating transaction rollback on application exception: " + ex);
				}
				RabbitUtils.rollbackIfNecessary(this.channel);
			}
			if (ackRequired) {
				// We should always requeue if the container was stopping
				boolean shouldRequeue = this.defaultRequeuRejected ||
						ex instanceof MessageRejectedWhileStoppingException;
				Throwable t = ex;
				while (shouldRequeue && t != null) {
					if (t instanceof AmqpRejectAndDontRequeueException) {
						shouldRequeue = false;
					}
					t = t.getCause();
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Rejecting messages (requeue=" + shouldRequeue + ")");
				}
				for (Long deliveryTag : this.deliveryTags) {
					// With newer RabbitMQ brokers could use basicNack here...
					this.channel.basicReject(deliveryTag, shouldRequeue);
				}
				if (this.transactional) {
					// Need to commit the reject (=nack)
					RabbitUtils.commitIfNecessary(this.channel);
				}
			}
		} catch (Exception e) {
			logger.error("Application exception overridden by rollback exception", ex);
			throw e;
		} finally {
			this.deliveryTags.clear();
		}
	}

	public boolean commitIfNecessary(boolean locallyTransacted) throws IOException {

		if (this.deliveryTags.isEmpty()) {
			return false;
		}

		try {

			boolean ackRequired = !this.acknowledgeMode.isAutoAck() && !this.acknowledgeMode.isManual();

			if (ackRequired) {

				if (this.transactional && !locallyTransacted) {

					// Not locally transacted but it is transacted so it
					// could be synchronized with an external transaction
					for (Long deliveryTag : this.deliveryTags) {
						ConnectionFactoryUtils.registerDeliveryTag(this.connectionFactory, this.channel, deliveryTag);
					}

				} else {
					long deliveryTag = new ArrayList<Long>(this.deliveryTags).get(this.deliveryTags.size() - 1);
					this.channel.basicAck(deliveryTag, true);
				}
			}

			if (locallyTransacted) {
				// For manual acks we still need to commit
				RabbitUtils.commitIfNecessary(this.channel);
			}

		}
		finally {
			this.deliveryTags.clear();
		}

		@Override
		public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
			if (logger.isDebugEnabled()) {
				if (RabbitUtils.isNormalShutdown(sig)) {
					logger.debug("Received shutdown signal for consumer tag=" + consumerTag + ": " + sig.getMessage());
				}
				else {
					logger.debug("Received shutdown signal for consumer tag=" + consumerTag, sig);
				}
			}
			BlockingQueueConsumer.this.shutdown = sig;
			// The delivery tags will be invalid if the channel shuts down
			BlockingQueueConsumer.this.deliveryTags.clear();
			BlockingQueueConsumer.this.activeObjectCounter.release(BlockingQueueConsumer.this);
		}

		public String getConsumerTag() {
			return this.consumerTag;
		}

	public Channel getChannel() {
		return this.channel;
	}

	public String toString() {
		return "Consumer: tags=[" + (this.consumerTags.toString()) + "], channel=" + this.channel
				+ ", acknowledgeMode=" + this.acknowledgeMode + " local queue size=" + this.queue.size();
	}

	public boolean commitIfNecessary(boolean locallyTransacted) throws IOException {

		if (this.deliveryTags.isEmpty()) {
			return false;
		}

		try {

			boolean ackRequired = !this.acknowledgeMode.isAutoAck() && !this.acknowledgeMode.isManual();

			if (ackRequired) {

				if (this.transactional && !locallyTransacted) {

					// Not locally transacted but it is transacted so it
					// could be synchronized with an external transaction
					for (Long deliveryTag : this.deliveryTags) {
						ConnectionFactoryUtils.registerDeliveryTag(this.connectionFactory, this.channel, deliveryTag);
					}

				} else {
					long deliveryTag = new ArrayList<Long>(this.deliveryTags).get(this.deliveryTags.size() - 1);
					this.channel.basicAck(deliveryTag, true);
				}
			}

			if (locallyTransacted) {
				// For manual acks we still need to commit
				RabbitUtils.commitIfNecessary(this.channel);
			}

		}
		finally {
			this.deliveryTags.clear();
		}

		return true;

	}

		public byte[] getBody() {
			return this.body;
		}

}