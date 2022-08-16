public class MissingMessageIdAdvice {
	public Object invoke(MethodInvocation invocation) throws Throwable {
		String id = null;
		Message message = null;
		boolean redelivered = false;
		try {
			message = (Message) invocation.getArguments()[1];
			MessageProperties messageProperties = message.getMessageProperties();
			if (messageProperties.getMessageId() == null) {
				id = UUID.randomUUID().toString();
				messageProperties.setMessageId(id);
			}
			redelivered = messageProperties.isRedelivered();
			return invocation.proceed();
		}
		catch (Exception e) {
			if (id != null && redelivered) {
				if (logger.isDebugEnabled()) {
					logger.debug("Canceling delivery of retried message that has no ID");
				}
				throw new ListenerExecutionFailedException("Cannot retry message without an ID",
						new AmqpRejectAndDontRequeueException(e), message);
			}
			else {
				throw e;
			}
		}
		finally {
			if (id != null) {
				this.retryContextCache.remove(id);
			}
		}
	}

}