public class CachingConnectionFactory {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String methodName = method.getName();
			if (methodName.equals("txSelect") && !this.transactional) {
				throw new UnsupportedOperationException("Cannot start transaction on non-transactional channel");
			}
			if (methodName.equals("equals")) {
				// Only consider equal when proxies are identical.
				return (proxy == args[0]);
			}
			else if (methodName.equals("hashCode")) {
				// Use hashCode of Channel proxy.
				return System.identityHashCode(proxy);
			}
			else if (methodName.equals("toString")) {
				return "Cached Rabbit Channel: " + this.target;
			}
			else if (methodName.equals("close")) {
				// Handle close method: don't pass the call on.
				if (CachingConnectionFactory.this.active) {
					synchronized (this.channelList) {
						if (!RabbitUtils.isPhysicalCloseRequired() &&
								(this.channelList.size() < getChannelCacheSize()
										|| this.channelList.contains(proxy))) {
							logicalClose((ChannelProxy) proxy);
							// Remain open in the channel list.
							releasePermit();
							return null;
						}
					}
				}

				// If we get here, we're supposed to shut down.
				physicalClose();
				releasePermit();
				return null;
			}
			else if (methodName.equals("getTargetChannel")) {
				// Handle getTargetChannel method: return underlying Channel.
				return this.target;
			}
			else if (methodName.equals("isOpen")) {
				// Handle isOpen method: we are closed if the target is closed
				return this.target != null && this.target.isOpen();
			}
			else if (methodName.equals("isTransactional")) {
				return this.transactional;
			}
			try {
				if (this.target == null || !this.target.isOpen()) {
					if (this.target instanceof PublisherCallbackChannel) {
						this.target.close();
						throw new InvocationTargetException(new AmqpException("PublisherCallbackChannel is closed"));
					}
					this.target = null;
				}
				synchronized (this.targetMonitor) {
					if (this.target == null) {
						this.target = createBareChannel(this.theConnection, this.transactional);
					}
					return method.invoke(this.target, args);
				}
			}
			catch (InvocationTargetException ex) {
				if (this.target == null || !this.target.isOpen()) {
					// Basic re-connection logic...
					if (logger.isDebugEnabled()) {
						logger.debug("Detected closed channel on exception.  Re-initializing: " + this.target);
					}
					this.target = null;
					synchronized (this.targetMonitor) {
						if (this.target == null) {
							this.target = createBareChannel(this.theConnection, this.transactional);
						}
					}
				}
				throw ex.getTargetException();
			}
		}

		@Override
		public int hashCode() {
			return 31 + ((this.target == null) ? 0 : this.target.hashCode());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			ChannelCachingConnectionProxy other = (ChannelCachingConnectionProxy) obj;
			if (this.target == null) {
				if (other.target != null) {
					return false;
				}
			} else if (!this.target.equals(other.target)) {
				return false;
			}
			return true;
		}

		@Override
		public boolean isOpen() {
			return this.target != null && this.target.isOpen();
		}

	public void setCacheMode(CacheMode cacheMode) {
		Assert.isTrue(!this.initialized, "'cacheMode' cannot be changed after initialization.");
		Assert.notNull(cacheMode, "'cacheMode' must not be null.");
		this.cacheMode = cacheMode;
	}

		@Override
		public Connection getTargetConnection() {
			return this.target;
		}

	@Override
	public final Connection createConnection() throws AmqpException {
		Assert.state(!this.stopped, "The ApplicationContext is closed and the ConnectionFactory can no longer create connections.");
		synchronized (this.connectionMonitor) {
			if (this.cacheMode == CacheMode.CHANNEL) {
				if (this.connection == null) {
					this.connection = new ChannelCachingConnectionProxy(super.createBareConnection());
					// invoke the listener *after* this.connection is assigned
					getConnectionListener().onCreate(this.connection);
					this.checkoutPermits.put(this.connection, new Semaphore(this.channelCacheSize));
				}
				return this.connection;
			}
			else if (this.cacheMode == CacheMode.CONNECTION) {
				ChannelCachingConnectionProxy connection = null;
				while (connection == null && !this.idleConnections.isEmpty()) {
					connection = this.idleConnections.poll();
					if (connection != null) {
						if (!connection.isOpen()) {
							if (logger.isDebugEnabled()) {
								logger.debug("Removing closed connection '" + connection + "'");
							}
							connection.notifyCloseIfNecessary();
							this.openConnections.remove(connection);
							this.openConnectionNonTransactionalChannels.remove(connection);
							this.openConnectionTransactionalChannels.remove(connection);
							this.checkoutPermits.remove(connection);
							connection = null;
						}
					}
				}
				if (connection == null) {
					connection = new ChannelCachingConnectionProxy(super.createBareConnection());
					getConnectionListener().onCreate(connection);
					if (logger.isDebugEnabled()) {
						logger.debug("Adding new connection '" + connection + "'");
					}
					this.openConnections.add(connection);
					this.openConnectionNonTransactionalChannels.put(connection, new LinkedList<ChannelProxy>());
					this.channelHighWaterMarks.put(ObjectUtils.getIdentityHexString(
							this.openConnectionNonTransactionalChannels.get(connection)), new AtomicInteger());
					this.openConnectionTransactionalChannels.put(connection, new LinkedList<ChannelProxy>());
					this.channelHighWaterMarks.put(
							ObjectUtils.getIdentityHexString(this.openConnectionTransactionalChannels.get(connection)),
							new AtomicInteger());
					this.checkoutPermits.put(connection, new Semaphore(this.channelCacheSize));
				}
				else {
					if (logger.isDebugEnabled()) {
						logger.debug("Obtained connection '" + connection + "' from cache");
					}
				}
				return connection;
			}
		}
		return null;
	}

		@Override
		public String toString() {
			return CachingConnectionFactory.this.cacheMode == CacheMode.CHANNEL ? "Shared " : "Dedicated " +
					"Rabbit Connection: " + this.target;
		}

	}

	private static class DefaultChannelCloseLogger implements ConditionalExceptionLogger {

		@Override
		public void log(Log logger, String message, Throwable t) {
			if (t instanceof ShutdownSignalException) {
				ShutdownSignalException cause = (ShutdownSignalException) t;
				if (RabbitUtils.isPassiveDeclarationChannelClose(cause)) {
					if (logger.isDebugEnabled()) {
						logger.debug(message + ": " + cause.getMessage());
					}
				}
				else if (RabbitUtils.isExclusiveUseChannelClose(cause)) {
					if (logger.isInfoEnabled()) {
						logger.info(message + ": " + cause.getMessage());
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

	public CacheMode getCacheMode() {
		return this.cacheMode;
	}

		private Channel createBareChannel(boolean transactional) {
			return this.target.createChannel(transactional);
		}

		@Override
		public void close() {
			if (CachingConnectionFactory.this.cacheMode == CacheMode.CONNECTION) {
				synchronized (CachingConnectionFactory.this.connectionMonitor) {
					if (!this.target.isOpen()
							|| CachingConnectionFactory.this.idleConnections.size() >=
									CachingConnectionFactory.this.connectionCacheSize) {
						if (logger.isDebugEnabled()) {
							logger.debug("Completely closing connection '" + this + "'");
						}
						if (this.target.isOpen()) {
							RabbitUtils.closeConnection(this.target);
						}
						this.notifyCloseIfNecessary();
						CachingConnectionFactory.this.openConnections.remove(this);
						CachingConnectionFactory.this.openConnectionNonTransactionalChannels.remove(this);
						CachingConnectionFactory.this.openConnectionTransactionalChannels.remove(this);
					}
					else {
						if (!CachingConnectionFactory.this.idleConnections.contains(this)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Returning connection '" + this + "' to cache");
							}
							CachingConnectionFactory.this.idleConnections.add(this);
							if (CachingConnectionFactory.this.connectionHighWaterMark
									.get() < CachingConnectionFactory.this.idleConnections.size()) {
								CachingConnectionFactory.this.connectionHighWaterMark
										.set(CachingConnectionFactory.this.idleConnections.size());
							}
						}
					}
				}
			}
		}

		private void logicalClose(ChannelProxy proxy) throws Exception {
			if (this.target == null) {
				return;
			}
			if (this.target != null && !this.target.isOpen()) {
				synchronized (this.targetMonitor) {
					if (this.target != null && !this.target.isOpen()) {
						if (this.target instanceof PublisherCallbackChannel) {
							this.target.close(); // emit nacks if necessary
						}
						if (this.channelList.contains(proxy)) {
							this.channelList.remove(proxy);
						}
						this.target = null;
						return;
					}
				}
			}
			// Allow for multiple close calls...
			if (!this.channelList.contains(proxy)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Returning cached Channel: " + this.target);
				}
				this.channelList.addLast(proxy);
				setHighWaterMark();
			}
		}

		public void destroy() {
			if (CachingConnectionFactory.this.cacheMode == CacheMode.CHANNEL) {
				reset(CachingConnectionFactory.this.cachedChannelsNonTransactional,
						CachingConnectionFactory.this.cachedChannelsTransactional);
			}
			else {
				reset(CachingConnectionFactory.this.openConnectionNonTransactionalChannels.get(this),
						CachingConnectionFactory.this.openConnectionTransactionalChannels.get(this));
			}
			if (this.target != null) {
				RabbitUtils.closeConnection(this.target);
				this.notifyCloseIfNecessary();
			}
			this.target = null;
		}

}