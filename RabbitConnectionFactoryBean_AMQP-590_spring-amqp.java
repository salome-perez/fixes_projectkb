public class RabbitConnectionFactoryBean {
	protected String getSslAlgorithm() {
		return this.sslAlgorithm;
	}

	protected Resource getSslPropertiesLocation() {
		return this.sslPropertiesLocation;
	}

	protected boolean isUseSSL() {
		return this.useSSL;
	}

}