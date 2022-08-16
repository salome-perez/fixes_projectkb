public class AbstractRoutingConnectionFactory {
	@Override
	public ConnectionFactory getTargetConnectionFactory(Object key) {
		return this.targetConnectionFactories.get(key);
	}

	public boolean isLenientFallback() {
		return this.lenientFallback;
	}

	protected ConnectionFactory removeTargetConnectionFactory(Object key) {
		return this.targetConnectionFactories.remove(key);
	}

	protected void addTargetConnectionFactory(Object key, ConnectionFactory connectionFactory) {
		this.targetConnectionFactories.put(key, connectionFactory);
		for(ConnectionListener listener : this.connectionListeners) {
			connectionFactory.addConnectionListener(listener);
		}
	}

}