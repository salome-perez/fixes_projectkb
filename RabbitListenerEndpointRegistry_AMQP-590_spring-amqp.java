public class RabbitListenerEndpointRegistry {
	@Override
	public void destroy() {
		for (MessageListenerContainer listenerContainer : getListenerContainers()) {
			if (listenerContainer instanceof DisposableBean) {
				try {
					((DisposableBean) listenerContainer).destroy();
				}
				catch (Exception ex) {
					this.logger.warn("Failed to destroy message listener container", ex);
				}
			}
		}
	}

}