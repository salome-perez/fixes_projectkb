public class SimpleConnection {
	@Override
	public boolean isOpen() {
		return this.delegate != null
				&& (this.delegate.isOpen() || this.delegate.getClass().getSimpleName().contains("AutorecoveringConnection"));
	}

	public Channel createChannel(boolean transactional) {
		try {
			Channel channel = this.delegate.createChannel();
			if (transactional) {
				// Just created so we want to start the transaction
				channel.txSelect();
			}
			return channel;
		} catch (IOException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
	}

	@Override
	public String toString() {
		return "SimpleConnection@"
				+ ObjectUtils.getIdentityHexString(this)
				+ " [delegate=" + this.delegate + ", localPort= " + getLocalPort() + "]";
	}

	@Override
	public void close() {
		try {
			// let the physical close time out if necessary
			this.delegate.close(this.closeTimeout);
		} catch (IOException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
	}

}