public class AbstractAdaptableMessageListener {
	protected void sendResponse(Channel channel, Address replyTo, Message messageIn) throws Exception {
		Message message;
		if (this.replyPostProcessor == null) {
			message = messageIn;
		}
		else {
			message = this.replyPostProcessor.postProcessMessage(messageIn);
		}
		postProcessChannel(channel, message);

		try {
			this.logger.debug("Publishing response to exchange = [" + replyTo.getExchangeName() + "], routingKey = ["
					+ replyTo.getRoutingKey() + "]");
			channel.basicPublish(replyTo.getExchangeName(), replyTo.getRoutingKey(), this.mandatoryPublish,
					this.messagePropertiesConverter.fromMessageProperties(message.getMessageProperties(), this.encoding),
					message.getBody());
		}
		catch (Exception ex) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(ex);
		}
	}

	protected void handleResult(Object result, Message request, Channel channel) throws Exception {
		if (channel != null) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Listener method returned result [" + result + "] - generating response message for it");
			}
			try {
				Message response = buildMessage(channel, result);
				postProcessResponse(request, response);
				Address replyTo = getReplyToAddress(request);
				sendResponse(channel, replyTo, response);
			}
			catch (Exception ex) {
				throw new ReplyFailureException("Failed to send reply with payload '" + result + "'", ex);
			}
		}
		else if (this.logger.isWarnEnabled()) {
			this.logger.warn("Listener method returned result [" + result
					+ "]: not generating response message for it because of no Rabbit Channel given");
		}
	}

	protected void handleListenerException(Throwable ex) {
		this.logger.error("Listener execution failed", ex);
	}

}