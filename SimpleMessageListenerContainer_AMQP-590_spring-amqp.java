public class SimpleMessageListenerContainer {
	@ManagedMetric(metricType = MetricType.GAUGE)
	public int getActiveConsumerCount() {
		return this.cancellationLock.getCount();
	}

	private boolean isActive(BlockingQueueConsumer consumer) {
		Boolean consumerActive;
		synchronized(this.consumersMonitor) {
			if (this.consumers != null) {
				Boolean active = this.consumers.get(consumer);
				consumerActive = active != null && active;
			}
			else {
				consumerActive = false;
			}
		}
		return consumerActive && this.isActive();
	}

	private void queuesChanged() {
		synchronized (this.consumersMonitor) {
			if (this.consumers != null) {
				int count = 0;
				for (Entry<BlockingQueueConsumer, Boolean> consumer : this.consumers.entrySet()) {
					if (consumer.getValue()) {
						if (logger.isDebugEnabled()) {
							logger.debug("Queues changed; stopping consumer: " + consumer.getKey());
						}
						consumer.getKey().basicCancel();
						consumer.setValue(false);
						count++;
					}
				}
				this.addAndStartConsumers(count);
			}
		}
	}

	public void setConsumerArguments(Map<String, Object> args) {
		synchronized(this.consumersMonitor) {
			this.consumerArgs.clear();
			this.consumerArgs.putAll(args);
		}
	}

		@Override
		public void run() {

			boolean aborted = false;

			int consecutiveIdles = 0;

			int consecutiveMessages = 0;

			try {

				try {
					if (SimpleMessageListenerContainer.this.autoDeclare) {
						SimpleMessageListenerContainer.this.redeclareElementsIfNecessary();
					}
					this.consumer.start();
					this.start.countDown();
				}
				catch (QueuesNotAvailableException e) {
					if (SimpleMessageListenerContainer.this.missingQueuesFatal) {
						throw e;
					}
					else {
						this.start.countDown();
						handleStartupFailure(this.consumer.getBackOffExecution());
						throw e;
					}
				}
				catch (FatalListenerStartupException ex) {
					throw ex;
				}
				catch (Throwable t) {//NOSONAR
					this.start.countDown();
					handleStartupFailure(this.consumer.getBackOffExecution());
					throw t;
				}

				if (SimpleMessageListenerContainer.this.transactionManager != null) {
					ConsumerChannelRegistry.registerConsumerChannel(this.consumer.getChannel(), getConnectionFactory());
				}

				while (isActive(this.consumer) || this.consumer.hasDelivery()) {
					try {
						boolean receivedOk = receiveAndExecute(this.consumer); // At least one message received
						if (SimpleMessageListenerContainer.this.maxConcurrentConsumers != null) {
							if (receivedOk) {
								if (isActive(this.consumer)) {
									consecutiveIdles = 0;
									if (consecutiveMessages++ > SimpleMessageListenerContainer.this.consecutiveActiveTrigger) {
										considerAddingAConsumer();
										consecutiveMessages = 0;
									}
								}
							}
							else {
								consecutiveMessages = 0;
								if (consecutiveIdles++ > SimpleMessageListenerContainer.this.consecutiveIdleTrigger) {
									considerStoppingAConsumer(this.consumer);
									consecutiveIdles = 0;
								}
							}
						}
						if (SimpleMessageListenerContainer.this.idleEventInterval != null) {
							if (receivedOk) {
								SimpleMessageListenerContainer.this.lastReceive = System.currentTimeMillis();
							}
							else {
								long now = System.currentTimeMillis();
								long lastAlertAt = SimpleMessageListenerContainer.this.lastNoMessageAlert.get();
								long lastReceive = SimpleMessageListenerContainer.this.lastReceive;
								if (now > lastReceive + SimpleMessageListenerContainer.this.idleEventInterval
										&& now > lastAlertAt + SimpleMessageListenerContainer.this.idleEventInterval
										&& SimpleMessageListenerContainer.this.lastNoMessageAlert
												.compareAndSet(lastAlertAt, now)) {
									publishIdleContainerEvent(now - lastReceive);
								}
							}
						}
					}
					catch (ListenerExecutionFailedException ex) {
						// Continue to process, otherwise re-throw
						if (ex.getCause() instanceof NoSuchMethodException) {
							throw new FatalListenerExecutionException("Invalid listener", ex);
						}
					}
					catch (AmqpRejectAndDontRequeueException rejectEx) {
					}
				}

			}
			catch (InterruptedException e) {
				logger.debug("Consumer thread interrupted, processing stopped.");
				Thread.currentThread().interrupt();
				aborted = true;
				publishConsumerFailedEvent("Consumer thread interrupted, processing stopped", true, e);
			}
			catch (QueuesNotAvailableException ex) {
				if (SimpleMessageListenerContainer.this.missingQueuesFatal) {
					logger.error("Consumer received fatal exception on startup", ex);
					this.startupException = ex;
					// Fatal, but no point re-throwing, so just abort.
					aborted = true;
				}
				publishConsumerFailedEvent("Consumer queue(s) not available", aborted, ex);
			}
			catch (FatalListenerStartupException ex) {
				logger.error("Consumer received fatal exception on startup", ex);
				this.startupException = ex;
				// Fatal, but no point re-throwing, so just abort.
				aborted = true;
				publishConsumerFailedEvent("Consumer received fatal exception on startup", true, ex);
			}
			catch (FatalListenerExecutionException ex) {
				logger.error("Consumer received fatal exception during processing", ex);
				// Fatal, but no point re-throwing, so just abort.
				aborted = true;
				publishConsumerFailedEvent("Consumer received fatal exception during processing", true, ex);
			}
			catch (ShutdownSignalException e) {
				if (RabbitUtils.isNormalShutdown(e)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Consumer received Shutdown Signal, processing stopped: " + e.getMessage());
					}
				}
				else {
					this.logConsumerException(e);
				}
			}
			catch (AmqpIOException e) {
				if (e.getCause() instanceof IOException && e.getCause().getCause() instanceof ShutdownSignalException
						&& e.getCause().getCause().getMessage().contains("in exclusive use")) {
					SimpleMessageListenerContainer.this.exclusiveConsumerExceptionLogger.log(logger,
							"Exclusive consumer failure", e.getCause().getCause());
					publishConsumerFailedEvent("Consumer raised exception, attempting restart", false, e);
				}
				else {
					this.logConsumerException(e);
				}
			}
			catch (Error e) {//NOSONAR
				// ok to catch Error - we're aborting so will stop
				logger.error("Consumer thread error, thread abort.", e);
				aborted = true;
			}
			catch (Throwable t) {//NOSONAR
				// by now, it must be an exception
				if (isActive()) {
					this.logConsumerException(t);
				}
			}
			finally {
				if (SimpleMessageListenerContainer.this.transactionManager != null) {
					ConsumerChannelRegistry.unRegisterConsumerChannel();
				}
			}

			// In all cases count down to allow container to progress beyond startup
			this.start.countDown();

			if (!isActive(this.consumer) || aborted) {
				logger.debug("Cancelling " + this.consumer);
				try {
					this.consumer.stop();
					SimpleMessageListenerContainer.this.cancellationLock.release(this.consumer);
					synchronized (SimpleMessageListenerContainer.this.consumersMonitor) {
						if (SimpleMessageListenerContainer.this.consumers != null) {
							SimpleMessageListenerContainer.this.consumers.remove(this.consumer);
						}
					}
				}
				catch (AmqpException e) {
					logger.info("Could not cancel message consumer", e);
				}
				if (aborted) {
					logger.error("Stopping container from aborted consumer");
					stop();
				}
			}
			else {
				logger.info("Restarting " + this.consumer);
				restart(this.consumer);
			}

		}

	private void initializeProxy() {
		if (this.adviceChain.length == 0) {
			return;
		}
		ProxyFactory factory = new ProxyFactory();
		for (Advice advice : getAdviceChain()) {
			factory.addAdvisor(new DefaultPointcutAdvisor(Pointcut.TRUE, advice));
		}
		factory.setProxyTargetClass(false);
		factory.addInterface(ContainerDelegate.class);
		factory.setTarget(this.delegate);
		this.proxy = (ContainerDelegate) factory.getProxy(ContainerDelegate.class.getClassLoader());
	}

		private void publishIdleContainerEvent(long idleTime) {
			if (SimpleMessageListenerContainer.this.applicationEventPublisher != null) {
				SimpleMessageListenerContainer.this.applicationEventPublisher.publishEvent(
						new ListenerContainerIdleEvent(SimpleMessageListenerContainer.this, idleTime, getListenerId(),
								getQueueNames()));
			}
		}

	}

	@Override
	protected void invokeListener(Channel channel, Message message) throws Exception {
		this.proxy.invokeListener(channel, message);
	}

	protected void handleStartupFailure(BackOffExecution backOffExecution) throws Exception {
		long recoveryInterval = backOffExecution.nextBackOff();
		if (BackOffExecution.STOP == recoveryInterval) {
			synchronized (this) {
				if (isActive()) {
					logger.warn("stopping container - restart recovery attempts exhausted");
					stop();
				}
			}
			return;
		}
		try {
			if (logger.isDebugEnabled() && isActive()) {
				logger.debug("Recovering consumer in " + recoveryInterval + " ms.");
			}
			long timeout = System.currentTimeMillis() + recoveryInterval;
			while (isActive() && System.currentTimeMillis() < timeout) {
				Thread.sleep(200);
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Unrecoverable interruption on consumer restart");
		}
	}

	@SuppressWarnings("serial")
	private static class WrappedTransactionException extends RuntimeException {

		private WrappedTransactionException(Throwable cause) {
			super(cause);
		}

	}

	private static class DefaultExclusiveConsumerLogger implements ConditionalExceptionLogger {

		@Override
		public void log(Log logger, String message, Throwable t) {
			if (t instanceof ShutdownSignalException) {
				ShutdownSignalException cause = (ShutdownSignalException) t;
				if (RabbitUtils.isExclusiveUseChannelClose(cause)) {
					if (logger.isWarnEnabled()) {
						logger.warn(message + ": " + cause.toString());
					}
				}
				else if (!RabbitUtils.isNormalChannelClose(cause)) {
					logger.error(message + ": " + cause.getMessage());
				}
			}
			else {
				logger.error("Unexpected invocation of " + this.getClass() + ", with message: " + message, t);
			}
		}

	protected RabbitAdmin getRabbitAdmin() {
		return this.rabbitAdmin;
	}

	private boolean doReceiveAndExecute(BlockingQueueConsumer consumer) throws Throwable {

		Channel channel = consumer.getChannel();

		for (int i = 0; i < this.txSize; i++) {

			logger.trace("Waiting for message from consumer.");
			Message message = consumer.nextMessage(this.receiveTimeout);
			if (message == null) {
				break;
			}
			try {
				executeListener(channel, message);
			}
			catch (ImmediateAcknowledgeAmqpException e) {
				break;
			}
			catch (Throwable ex) {//NOSONAR
				consumer.rollbackOnExceptionIfNecessary(ex);
				throw ex;
			}

		}

		return consumer.commitIfNecessary(isChannelLocallyTransacted(channel));

	}

	protected BlockingQueueConsumer createBlockingQueueConsumer() {
		BlockingQueueConsumer consumer;
		String[] queues = getRequiredQueueNames();
		// There's no point prefetching less than the tx size, otherwise the consumer will stall because the broker
		// didn't get an ack for delivered messages
		int actualPrefetchCount = this.prefetchCount > this.txSize ? this.prefetchCount : this.txSize;
		consumer = new BlockingQueueConsumer(getConnectionFactory(), this.messagePropertiesConverter, this.cancellationLock,
				getAcknowledgeMode(), isChannelTransacted(), actualPrefetchCount, this.defaultRequeueRejected,
				this.consumerArgs, this.exclusive, queues);
		if (this.declarationRetries != null) {
			consumer.setDeclarationRetries(this.declarationRetries);
		}
		if (this.failedDeclarationRetryInterval != null) {
			consumer.setFailedDeclarationRetryInterval(this.failedDeclarationRetryInterval);
		}
		if (this.retryDeclarationInterval != null) {
			consumer.setRetryDeclarationInterval(this.retryDeclarationInterval);
		}
		if (this.consumerTagStrategy != null) {
			consumer.setTagStrategy(this.consumerTagStrategy);
		}
		consumer.setBackOffExecution(this.recoveryBackOff.start());
		return consumer;
	}

	@Override
	protected void doShutdown() {

		if (!this.isRunning()) {
			return;
		}

		try {
			synchronized (this.consumersMonitor) {
				if (this.consumers != null) {
					for (BlockingQueueConsumer consumer : this.consumers.keySet()) {
						consumer.basicCancel();
					}
				}
			}
			logger.info("Waiting for workers to finish.");
			boolean finished = this.cancellationLock.await(this.shutdownTimeout, TimeUnit.MILLISECONDS);
			if (finished) {
				logger.info("Successfully waited for workers to finish.");
			}
			else {
				logger.info("Workers not finished.");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupted waiting for workers.  Continuing with shutdown.");
		}

		synchronized (this.consumersMonitor) {
			this.consumers = null;
		}

	}

	private boolean receiveAndExecute(final BlockingQueueConsumer consumer) throws Throwable {

		if (this.transactionManager != null) {
			try {
				return new TransactionTemplate(this.transactionManager, this.transactionAttribute)
						.execute(new TransactionCallback<Boolean>() {
							@Override
							public Boolean doInTransaction(TransactionStatus status) {
								ConnectionFactoryUtils.bindResourceToTransaction(
										new RabbitResourceHolder(consumer.getChannel(), false),
										getConnectionFactory(), true);
								try {
									return doReceiveAndExecute(consumer);
								}
								catch (RuntimeException e) {
									throw e;
								}
								catch (Throwable e) {//NOSONAR
									// ok to catch Throwable here because we re-throw it below
									throw new WrappedTransactionException(e);
								}
							}
						});
			}
			catch (WrappedTransactionException e) {
				throw e.getCause();
			}
		}

		return doReceiveAndExecute(consumer);

	}

	protected int initializeConsumers() {
		int count = 0;
		synchronized (this.consumersMonitor) {
			if (this.consumers == null) {
				this.cancellationLock.reset();
				this.consumers = new HashMap<BlockingQueueConsumer, Boolean>(this.concurrentConsumers);
				for (int i = 0; i < this.concurrentConsumers; i++) {
					BlockingQueueConsumer consumer = createBlockingQueueConsumer();
					this.consumers.put(consumer, true);
					count++;
				}
			}
		}
		return count;
	}

	private void considerAddingAConsumer() {
		synchronized(this.consumersMonitor) {
			if (this.consumers != null
					&& this.maxConcurrentConsumers != null && this.consumers.size() < this.maxConcurrentConsumers) {
				long now = System.currentTimeMillis();
				if (this.lastConsumerStarted + this.startConsumerMinInterval < now) {
					this.addAndStartConsumers(1);
					this.lastConsumerStarted = now;
				}
			}
		}
	}

	private void considerStoppingAConsumer(BlockingQueueConsumer consumer) {
		synchronized (this.consumersMonitor) {
			if (this.consumers != null && this.consumers.size() > this.concurrentConsumers) {
				long now = System.currentTimeMillis();
				if (this.lastConsumerStopped + this.stopConsumerMinInterval < now) {
					consumer.basicCancel();
					this.consumers.put(consumer, false);
					if (logger.isDebugEnabled()) {
						logger.debug("Idle consumer terminating: " + consumer);
					}
					this.lastConsumerStopped = now;
				}
			}
		}
	}

		private FatalListenerStartupException getStartupException() throws TimeoutException, InterruptedException {
			this.start.await(60000L, TimeUnit.MILLISECONDS);//NOSONAR - ignore return value
			return this.startupException;
		}

	protected void invokeListener(Channel channel, Message message) throws Exception {
		this.proxy.invokeListener(channel, message);
	}

	@Override
	protected void validateConfiguration() {

		super.validateConfiguration();

		Assert.state(
				!(getAcknowledgeMode().isAutoAck() && this.transactionManager != null),
				"The acknowledgeMode is NONE (autoack in Rabbit terms) which is not consistent with having an "
						+ "external transaction manager. Either use a different AcknowledgeMode or make sure " +
						"the transactionManager is null.");

		if (this.getConnectionFactory() instanceof CachingConnectionFactory) {
			CachingConnectionFactory cf = (CachingConnectionFactory) getConnectionFactory();
			if (cf.getCacheMode() == CacheMode.CHANNEL && cf.getChannelCacheSize() < this.concurrentConsumers) {
				cf.setChannelCacheSize(this.concurrentConsumers);
				logger.warn("CachingConnectionFactory's channelCacheSize can not be less than the number " +
						"of concurrentConsumers so it was reset to match: "	+ this.concurrentConsumers);
			}
		}

	}

		private void publishConsumerFailedEvent(String reason, boolean fatal, Throwable t) {
			if (SimpleMessageListenerContainer.this.applicationEventPublisher != null) {
				SimpleMessageListenerContainer.this.applicationEventPublisher
						.publishEvent(new ListenerContainerConsumerFailedEvent(SimpleMessageListenerContainer.this,
								reason, t, fatal));
			}
		}

	public void setConcurrentConsumers(final int concurrentConsumers) {
		Assert.isTrue(concurrentConsumers > 0, "'concurrentConsumers' value must be at least 1 (one)");
		Assert.isTrue(!this.exclusive || concurrentConsumers == 1,
				"When the consumer is exclusive, the concurrency must be 1");
		if (this.maxConcurrentConsumers != null) {
			Assert.isTrue(concurrentConsumers <= this.maxConcurrentConsumers,
					"'concurrentConsumers' cannot be more than 'maxConcurrentConsumers'");
		}
		synchronized(this.consumersMonitor) {
			if (logger.isDebugEnabled()) {
				logger.debug("Changing consumers from " + this.concurrentConsumers + " to " + concurrentConsumers);
			}
			int delta = this.concurrentConsumers - concurrentConsumers;
			this.concurrentConsumers = concurrentConsumers;
			if (isActive() && this.consumers != null) {
				if (delta > 0) {
					Iterator<Entry<BlockingQueueConsumer, Boolean>> entryIterator = this.consumers.entrySet()
							.iterator();
					while (entryIterator.hasNext() && delta > 0) {
						Entry<BlockingQueueConsumer, Boolean> entry = entryIterator.next();
						if (entry.getValue()) {
							BlockingQueueConsumer consumer = entry.getKey();
							consumer.basicCancel();
							this.consumers.put(consumer, false);
							delta--;
						}
					}

				}
				else {
					addAndStartConsumers(-delta);
				}
			}
		}
	}

}