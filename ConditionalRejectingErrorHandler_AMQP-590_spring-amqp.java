public class ConditionalRejectingErrorHandler {
		@Override
		public boolean isFatal(Throwable t) {
			if (t instanceof ListenerExecutionFailedException
					&& t.getCause() instanceof MessageConversionException) {
				if (ConditionalRejectingErrorHandler.this.logger.isWarnEnabled()) {
					ConditionalRejectingErrorHandler.this.logger.warn(
							"Fatal message conversion error; message rejected; "
							+ "it will be dropped or routed to a dead letter exchange, if so configured: "
							+ ((ListenerExecutionFailedException) t).getFailedMessage(), t);
				}
				return true;
			}
			return false;
		}

	public void handleError(Throwable t) {
		if (this.logger.isWarnEnabled()) {
			this.logger.warn("Execution of Rabbit message listener failed.", t);
		}
		if (!this.causeChainContainsARADRE(t) && this.exceptionStrategy.isFatal(t)) {
			throw new AmqpRejectAndDontRequeueException("Error Handler converted exception to fatal", t);
		}
	}

}