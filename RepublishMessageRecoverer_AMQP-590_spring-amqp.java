public class RepublishMessageRecoverer {
	@Override
	public void recover(Message message, Throwable cause) {
		Map<String, Object> headers = message.getMessageProperties().getHeaders();
		headers.put(X_EXCEPTION_STACKTRACE, getStackTraceAsString(cause));
		headers.put(X_EXCEPTION_MESSAGE, cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage());
		headers.put(X_ORIGINAL_EXCHANGE, message.getMessageProperties().getReceivedExchange());
		headers.put(X_ORIGINAL_ROUTING_KEY, message.getMessageProperties().getReceivedRoutingKey());
		Map<? extends String, ? extends Object> additionalHeaders = additionalHeaders(message, cause);
		if (additionalHeaders != null) {
			headers.putAll(additionalHeaders);
		}

		if (null != this.errorExchangeName) {
			String routingKey = this.errorRoutingKey != null ? this.errorRoutingKey : this.prefixedOriginalRoutingKey(message);
			this.errorTemplate.send(this.errorExchangeName, routingKey, message);
			if (this.logger.isWarnEnabled()) {
				this.logger.warn("Republishing failed message to exchange " + this.errorExchangeName);
			}
		}
		else {
			final String routingKey = this.prefixedOriginalRoutingKey(message);
			this.errorTemplate.send(routingKey, message);
			if (this.logger.isWarnEnabled()) {
				this.logger.warn("Republishing failed message to the template's default exchange with routing key " + routingKey);
			}
		}
	}

}