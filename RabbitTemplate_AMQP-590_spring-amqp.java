public class RabbitTemplate {
		public String getSavedCorrelation() {
			return this.savedCorrelation;
		}

	protected void doSend(Channel channel, String exchange, String routingKey, Message message,
			boolean mandatory, CorrelationData correlationData) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Publishing message on exchange [" + exchange + "], routingKey = [" + routingKey + "]");
		}

		if (exchange == null) {
			// try to send to configured exchange
			exchange = this.exchange;
		}

		if (routingKey == null) {
			// try to send to configured routing key
			routingKey = this.routingKey;
		}
		setupConfirm(channel, correlationData);
		Message messageToUse = message;
		MessageProperties messageProperties = messageToUse.getMessageProperties();
		if (mandatory) {
			messageProperties.getHeaders().put(PublisherCallbackChannel.RETURN_CORRELATION_KEY, this.uuid);
		}
		if (this.beforePublishPostProcessors != null) {
			for (MessagePostProcessor processor : this.beforePublishPostProcessors) {
				messageToUse = processor.postProcessMessage(messageToUse);
			}
		}
		BasicProperties convertedMessageProperties = this.messagePropertiesConverter
				.fromMessageProperties(messageProperties, this.encoding);
		channel.basicPublish(exchange, routingKey, mandatory, convertedMessageProperties, messageToUse.getBody());
		// Check if commit needed
		if (isChannelLocallyTransacted(channel)) {
			// Transacted channel created by this template -> commit.
			RabbitUtils.commitIfNecessary(channel);
		}
	}

			public Message doInRabbit(Channel channel) throws Exception {
				final PendingReply pendingReply = new PendingReply();
				String messageTag = String.valueOf(RabbitTemplate.this.messageTagProvider.incrementAndGet());
				RabbitTemplate.this.replyHolder.put(messageTag, pendingReply);
				// Save any existing replyTo and correlation data
				String savedReplyTo = message.getMessageProperties().getReplyTo();
				pendingReply.setSavedReplyTo(savedReplyTo);
				if (StringUtils.hasLength(savedReplyTo) && logger.isDebugEnabled()) {
					logger.debug("Replacing replyTo header:" + savedReplyTo
							+ " in favor of template's configured reply-queue:"
							+ RabbitTemplate.this.replyAddress);
				}
				message.getMessageProperties().setReplyTo(RabbitTemplate.this.replyAddress);
				String savedCorrelation = null;
				if (RabbitTemplate.this.correlationKey == null) { // using standard correlationId property
					byte[] correlationId = message.getMessageProperties().getCorrelationId();
					if (correlationId != null) {
						savedCorrelation = new String(correlationId,
								RabbitTemplate.this.encoding);
					}
				}
				else {
					savedCorrelation = (String) message.getMessageProperties()
							.getHeaders().get(RabbitTemplate.this.correlationKey);
				}
				pendingReply.setSavedCorrelation(savedCorrelation);
				if (RabbitTemplate.this.correlationKey == null) { // using standard correlationId property
					message.getMessageProperties().setCorrelationId(messageTag
							.getBytes(RabbitTemplate.this.encoding));
				}
				else {
					message.getMessageProperties().setHeader(
							RabbitTemplate.this.correlationKey, messageTag);
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Sending message with tag " + messageTag);
				}
				Message reply = null;
				try {
					reply = exchangeMessages(exchange, routingKey, message, correlationData, channel, pendingReply,
							messageTag);
				}
				finally {
					RabbitTemplate.this.replyHolder.remove(messageTag);
				}
				return reply;
			}

		public String getSavedReplyTo() {
			return this.savedReplyTo;
		}

	protected Message doSendAndReceiveWithFixed(final String exchange, final String routingKey, final Message message,
			final CorrelationData correlationData) {
		Assert.state(this.isListener, "RabbitTemplate is not configured as MessageListener - "
							+ "cannot use a 'replyAddress': " + this.replyAddress);
		return this.execute(new ChannelCallback<Message>() {

			@Override
			public Message doInRabbit(Channel channel) throws Exception {
				final PendingReply pendingReply = new PendingReply();
				String messageTag = String.valueOf(RabbitTemplate.this.messageTagProvider.incrementAndGet());
				RabbitTemplate.this.replyHolder.put(messageTag, pendingReply);
				// Save any existing replyTo and correlation data
				String savedReplyTo = message.getMessageProperties().getReplyTo();
				pendingReply.setSavedReplyTo(savedReplyTo);
				if (StringUtils.hasLength(savedReplyTo) && logger.isDebugEnabled()) {
					logger.debug("Replacing replyTo header:" + savedReplyTo
							+ " in favor of template's configured reply-queue:"
							+ RabbitTemplate.this.replyAddress);
				}
				message.getMessageProperties().setReplyTo(RabbitTemplate.this.replyAddress);
				String savedCorrelation = null;
				if (RabbitTemplate.this.correlationKey == null) { // using standard correlationId property
					byte[] correlationId = message.getMessageProperties().getCorrelationId();
					if (correlationId != null) {
						savedCorrelation = new String(correlationId,
								RabbitTemplate.this.encoding);
					}
				}
				else {
					savedCorrelation = (String) message.getMessageProperties()
							.getHeaders().get(RabbitTemplate.this.correlationKey);
				}
				pendingReply.setSavedCorrelation(savedCorrelation);
				if (RabbitTemplate.this.correlationKey == null) { // using standard correlationId property
					message.getMessageProperties().setCorrelationId(messageTag
							.getBytes(RabbitTemplate.this.encoding));
				}
				else {
					message.getMessageProperties().setHeader(
							RabbitTemplate.this.correlationKey, messageTag);
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Sending message with tag " + messageTag);
				}
				Message reply = null;
				try {
					reply = exchangeMessages(exchange, routingKey, message, correlationData, channel, pendingReply,
							messageTag);
				}
				finally {
					RabbitTemplate.this.replyHolder.remove(messageTag);
				}
				return reply;
			}
		}, obtainTargetConnectionFactoryIfNecessary(this.sendConnectionFactorySelectorExpression, message));
	}

					public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
											   byte[] body) throws IOException {
						MessageProperties messageProperties = RabbitTemplate.this.messagePropertiesConverter
								.toMessageProperties(properties, envelope, RabbitTemplate.this.encoding);
						Message reply = new Message(body, messageProperties);
						if (logger.isTraceEnabled()) {
							logger.trace("Message received " + reply);
						}
						pendingReply.reply(reply);
					}

	public void send(final String exchange, final String routingKey,
			final Message message, final CorrelationData correlationData)
			throws AmqpException {
		execute(new ChannelCallback<Object>() {

			@Override
			public Object doInRabbit(Channel channel) throws Exception {
				doSend(channel, exchange, routingKey, message, RabbitTemplate.this.returnCallback != null
						&& RabbitTemplate.this.mandatoryExpression.getValue(
								RabbitTemplate.this.evaluationContext, message, Boolean.class),
						correlationData);
				return null;
			}
		}, obtainTargetConnectionFactoryIfNecessary(this.sendConnectionFactorySelectorExpression, message));
	}

	protected Message doSendAndReceiveWithTemporary(final String exchange, final String routingKey,
			final Message message, final CorrelationData correlationData) {
		return this.execute(new ChannelCallback<Message>() {

			@Override
			public Message doInRabbit(Channel channel) throws Exception {
				final PendingReply pendingReply = new PendingReply();
				String messageTag = String.valueOf(RabbitTemplate.this.messageTagProvider.incrementAndGet());
				RabbitTemplate.this.replyHolder.put(messageTag, pendingReply);

				Assert.isNull(message.getMessageProperties().getReplyTo(),
						"Send-and-receive methods can only be used if the Message does not already have a replyTo property.");
				String replyTo;
				if (RabbitTemplate.this.usingFastReplyTo) {
					replyTo = Address.AMQ_RABBITMQ_REPLY_TO;
				}
				else {
					DeclareOk queueDeclaration = channel.queueDeclare();
					replyTo = queueDeclaration.getQueue();
				}
				message.getMessageProperties().setReplyTo(replyTo);

				String consumerTag = UUID.randomUUID().toString();
				DefaultConsumer consumer = new DefaultConsumer(channel) {

					@Override
					public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
											   byte[] body) throws IOException {
						MessageProperties messageProperties = RabbitTemplate.this.messagePropertiesConverter
								.toMessageProperties(properties, envelope, RabbitTemplate.this.encoding);
						Message reply = new Message(body, messageProperties);
						if (logger.isTraceEnabled()) {
							logger.trace("Message received " + reply);
						}
						pendingReply.reply(reply);
					}
				};
				channel.basicConsume(replyTo, true, consumerTag, true, true, null, consumer);
				Message reply = null;
				try {
					reply = exchangeMessages(exchange, routingKey, message, correlationData, channel, pendingReply,
							messageTag);
				}
				finally {
					RabbitTemplate.this.replyHolder.remove(messageTag);
					try {
						channel.basicCancel(consumerTag);
					}
					catch (Exception e) {}
				}
				return reply;
			}
		}, obtainTargetConnectionFactoryIfNecessary(this.sendConnectionFactorySelectorExpression, message));
	}

	@Override
	public void handleReturn(int replyCode,
			String replyText,
			String exchange,
			String routingKey,
			BasicProperties properties,
			byte[] body)
		throws IOException {

		ReturnCallback returnCallback = this.returnCallback;
		if (returnCallback == null) {
			Object messageTagHeader = properties.getHeaders().remove(RETURN_CORRELATION_KEY);
			if (messageTagHeader != null) {
				String messageTag = messageTagHeader.toString();
				final PendingReply pendingReply = this.replyHolder.get(messageTag);
				if (pendingReply != null) {
					returnCallback = new ReturnCallback() {

						@Override
						public void returnedMessage(Message message, int replyCode, String replyText, String exchange,
								String routingKey) {
							pendingReply.returned(new AmqpMessageReturnedException("Message returned",
									message, replyCode, replyText, exchange, routingKey));
						}
					};
				}
				else if (logger.isWarnEnabled()) {
					logger.warn("Returned request message but caller has timed out");
				}
			}
			else if (logger.isWarnEnabled()) {
				logger.warn("Returned message but no callback available");
			}
		}
		if (returnCallback != null) {
			properties.getHeaders().remove(PublisherCallbackChannel.RETURN_CORRELATION_KEY);
			MessageProperties messageProperties = this.messagePropertiesConverter.toMessageProperties(
					properties, null, this.encoding);
			Message returnedMessage = new Message(body, messageProperties);
			returnCallback.returnedMessage(returnedMessage,
					replyCode, replyText, exchange, routingKey);
		}
	}

	@SuppressWarnings("unchecked")
	private <R, S> boolean doReceiveAndReply(final String queueName, final ReceiveAndReplyCallback<R, S> callback,
											 final ReplyToAddressCallback<S> replyToAddressCallback) throws AmqpException {
		return this.execute(new ChannelCallback<Boolean>() {

			@Override
			public Boolean doInRabbit(Channel channel) throws Exception {
				boolean channelTransacted = isChannelTransacted();
				Message receiveMessage = null;
				boolean channelLocallyTransacted = isChannelLocallyTransacted(channel);

				if (RabbitTemplate.this.receiveTimeout == 0) {
					GetResponse response = channel.basicGet(queueName, !channelTransacted);
					// Response can be null in the case that there is no message on the queue.
					if (response != null) {
						long deliveryTag = response.getEnvelope().getDeliveryTag();

						if (channelLocallyTransacted) {
							channel.basicAck(deliveryTag, false);
						}
						else if (channelTransacted) {
							// Not locally transacted but it is transacted so it could be
							// synchronized with an external transaction
							ConnectionFactoryUtils.registerDeliveryTag(getConnectionFactory(), channel, deliveryTag);
						}
						receiveMessage = buildMessageFromResponse(response);
					}
				}
				else {
					QueueingConsumer consumer = createQueueingConsumer(queueName, channel);
					Delivery delivery;
					if (RabbitTemplate.this.receiveTimeout < 0) {
						delivery = consumer.nextDelivery();
					}
					else {
						delivery = consumer.nextDelivery(RabbitTemplate.this.receiveTimeout);
					}
					channel.basicCancel(consumer.getConsumerTag());
					if (delivery != null) {
						long deliveryTag = delivery.getEnvelope().getDeliveryTag();
						if (channelTransacted && !channelLocallyTransacted) {
							// Not locally transacted but it is transacted so it could be
							// synchronized with an external transaction
							ConnectionFactoryUtils.registerDeliveryTag(getConnectionFactory(), channel, deliveryTag);
						}
						else {
							channel.basicAck(deliveryTag, false);
						}
						receiveMessage = buildMessageFromDelivery(delivery);
					}
				}
				if (receiveMessage != null) {
					Object receive = receiveMessage;
					if (!(ReceiveAndReplyMessageCallback.class.isAssignableFrom(callback.getClass()))) {
						receive = RabbitTemplate.this.getRequiredMessageConverter().fromMessage(receiveMessage);
					}

					S reply;
					try {
						reply = callback.handle((R) receive);
					}
					catch (ClassCastException e) {
						StackTraceElement[] trace = e.getStackTrace();
						if (trace[0].getMethodName().equals("handle") && trace[1].getFileName().equals("RabbitTemplate.java")) {
							throw new IllegalArgumentException("ReceiveAndReplyCallback '" + callback
									+ "' can't handle received object '" + receive + "'", e);
						}
						else {
							throw e;
						}
					}

					if (reply != null) {
						Address replyTo = replyToAddressCallback.getReplyToAddress(receiveMessage, reply);

						Message replyMessage = RabbitTemplate.this.convertMessageIfNecessary(reply);

						MessageProperties receiveMessageProperties = receiveMessage.getMessageProperties();
						MessageProperties replyMessageProperties = replyMessage.getMessageProperties();

						Object correlation = RabbitTemplate.this.correlationKey == null
								? receiveMessageProperties.getCorrelationId()
								: receiveMessageProperties.getHeaders().get(RabbitTemplate.this.correlationKey);

						if (RabbitTemplate.this.correlationKey == null || correlation == null) {
							// using standard correlationId property
							if (correlation == null) {
								String messageId = receiveMessageProperties.getMessageId();
								if (messageId != null) {
									correlation = messageId.getBytes(RabbitTemplate.this.encoding);
								}
							}
							replyMessageProperties.setCorrelationId((byte[]) correlation);
						}
						else {
							replyMessageProperties.setHeader(RabbitTemplate.this.correlationKey, correlation);
						}

						// 'doSend()' takes care of 'channel.txCommit()'.
						RabbitTemplate.this.doSend(
								channel,
								replyTo.getExchangeName(),
								replyTo.getRoutingKey(),
								replyMessage,
								RabbitTemplate.this.returnCallback != null && RabbitTemplate.this.mandatoryExpression
										.getValue(RabbitTemplate.this.evaluationContext, replyMessage, Boolean.class),
								null);
					}
					else if (channelLocallyTransacted) {
						channel.txCommit();
					}

					return true;
				}
				return false;
			}
		}, obtainTargetConnectionFactoryIfNecessary(this.receiveConnectionFactorySelectorExpression, queueName));
	}

	private Message exchangeMessages(final String exchange, final String routingKey, final Message message,
			final CorrelationData correlationData, Channel channel, final PendingReply pendingReply, String messageTag)
			throws Exception {
		Message reply;
		boolean mandatory = this.mandatoryExpression.getValue(this.evaluationContext, message, Boolean.class);
		if (mandatory && this.returnCallback == null) {
			message.getMessageProperties().getHeaders().put(RETURN_CORRELATION_KEY, messageTag);
		}
		doSend(channel, exchange, routingKey, message, mandatory, correlationData);
		reply = this.replyTimeout < 0 ? pendingReply.get() : pendingReply.get(this.replyTimeout, TimeUnit.MILLISECONDS);
		return reply;
	}

}