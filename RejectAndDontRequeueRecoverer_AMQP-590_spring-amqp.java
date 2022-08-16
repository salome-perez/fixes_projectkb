public class RejectAndDontRequeueRecoverer {
	public void recover(Message message, Throwable cause) {
		if (this.logger.isWarnEnabled()) {
			this.logger.warn("Retries exhausted for message " + message, cause);
		}
		throw new ListenerExecutionFailedException("Retry Policy Exhausted",
					new AmqpRejectAndDontRequeueException(cause), message);
	}

}