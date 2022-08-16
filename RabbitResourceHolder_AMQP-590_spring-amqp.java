public class RabbitResourceHolder {
	public void commitAll() throws AmqpException {
		try {
			for (Channel channel : this.channels) {
				if (this.deliveryTags.containsKey(channel)) {
					for (Long deliveryTag : this.deliveryTags.get(channel)) {
						channel.basicAck(deliveryTag, false);
					}
				}
				channel.txCommit();
			}
		} catch (IOException e) {
			throw new AmqpException("failed to commit RabbitMQ transaction", e);
		}
	}

	public boolean isReleaseAfterCompletion() {
		return this.releaseAfterCompletion;
	}

	public void rollbackAll() {
		for (Channel channel : this.channels) {
			if (logger.isDebugEnabled()) {
				logger.debug("Rolling back messages to channel: " + channel);
			}
			RabbitUtils.rollbackIfNecessary(channel);
			if (this.deliveryTags.containsKey(channel)) {
				for (Long deliveryTag : this.deliveryTags.get(channel)) {
					try {
						channel.basicReject(deliveryTag, true);
					} catch (IOException ex) {
						throw new AmqpIOException(ex);
					}
				}
				// Need to commit the reject (=nack)
				RabbitUtils.commitIfNecessary(channel);
			}
		}
	}

}