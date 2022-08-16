public class AbstractConnectionFactory {
	protected ConnectionListener getConnectionListener() {
		return this.connectionListener;
	}

	public int getCloseTimeout() {
		return this.closeTimeout;
	}

	public void setAddresses(String addresses) {
		if (StringUtils.hasText(addresses)) {
			Address[] addressArray = Address.parseAddresses(addresses);
			if (addressArray.length > 0) {
				this.addresses = addressArray;
				return;
			}
		}
		this.logger.info("setAddresses() called with an empty value, will be using the host+port properties for connections");
		this.addresses = null;
	}

	protected final  String getDefaultHostName() {
		String temp;
		try {
			InetAddress localMachine = InetAddress.getLocalHost();
			temp = localMachine.getHostName();
			this.logger.debug("Using hostname [" + temp + "] for hostname.");
		} catch (UnknownHostException e) {
			this.logger.warn("Could not get host name, using 'localhost' as default value", e);
			temp = "localhost";
		}
		return temp;
	}

	public void setUri(String uri) {
		try {
			this.rabbitConnectionFactory.setUri(uri);
		}
		catch (URISyntaxException use) {
			this.logger.info(BAD_URI, use);
		}
		catch (GeneralSecurityException gse) {
			this.logger.info(BAD_URI, gse);
		}
	}

	protected final Connection createBareConnection() {
		try {
			Connection connection = null;
			if (this.addresses != null) {
				connection = new SimpleConnection(this.rabbitConnectionFactory.newConnection(this.executorService, this.addresses),
									this.closeTimeout);
			}
			else {
				connection = new SimpleConnection(this.rabbitConnectionFactory.newConnection(this.executorService),
									this.closeTimeout);
			}
			if (this.logger.isInfoEnabled()) {
				this.logger.info("Created new connection: " + connection);
			}
			return connection;
		}
		catch (IOException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
		catch (TimeoutException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
	}

	protected ExecutorService getExecutorService() {
		return this.executorService;
	}

	@Override
	public String getVirtualHost() {
		return this.rabbitConnectionFactory.getVirtualHost();
	}

	protected ChannelListener getChannelListener() {
		return this.channelListener;
	}

}