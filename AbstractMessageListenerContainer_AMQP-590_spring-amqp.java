public class AbstractMessageListenerContainer {
	protected final ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	@Override
	public final void afterPropertiesSet() {
		super.afterPropertiesSet();
		Assert.state(
				this.exposeListenerChannel || !getAcknowledgeMode().isManual(),
				"You cannot acknowledge messages manually if the channel is not exposed to the listener "
						+ "(please check your configuration and set exposeListenerChannel=true or acknowledgeMode!=MANUAL)");
		Assert.state(
				!(getAcknowledgeMode().isAutoAck() && isChannelTransacted()),
				"The acknowledgeMode is NONE (autoack in Rabbit terms) which is not consistent with having a "
						+ "transactional channel. Either use a different AcknowledgeMode or make sure channelTransacted=false");
		validateConfiguration();
		initialize();
	}

	public AcknowledgeMode getAcknowledgeMode() {
		return this.acknowledgeMode;
	}

	@Override
	public void start() {
		if (!this.initialized) {
			synchronized (this.lifecycleMonitor) {
				if (!this.initialized) {
					afterPropertiesSet();
					this.initialized = true;
				}
			}
		}
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Starting Rabbit listener container.");
			}
			doStart();
		} catch (Exception ex) {
			throw convertRabbitAccessException(ex);
		}
	}

	@Override
	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

}