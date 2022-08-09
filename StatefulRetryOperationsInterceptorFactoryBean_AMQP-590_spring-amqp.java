public class StatefulRetryOperationsInterceptorFactoryBean {
			public boolean isNew(Object[] args) {
				Message message = (Message) args[1];
				if (StatefulRetryOperationsInterceptorFactoryBean.this.newMessageIdentifier == null) {
					return !message.getMessageProperties().isRedelivered();
				}
				else {
					return StatefulRetryOperationsInterceptorFactoryBean.this.newMessageIdentifier.isNew(message);
				}
			}

	public StatefulRetryOperationsInterceptor getObject() {

		StatefulRetryOperationsInterceptor retryInterceptor = new StatefulRetryOperationsInterceptor();
		RetryOperations retryTemplate = getRetryOperations();
		if (retryTemplate == null) {
			retryTemplate = new RetryTemplate();
		}
		retryInterceptor.setRetryOperations(retryTemplate);

		retryInterceptor.setNewItemIdentifier(new NewMethodArgumentsIdentifier() {
			public boolean isNew(Object[] args) {
				Message message = (Message) args[1];
				if (StatefulRetryOperationsInterceptorFactoryBean.this.newMessageIdentifier == null) {
					return !message.getMessageProperties().isRedelivered();
				}
				else {
					return StatefulRetryOperationsInterceptorFactoryBean.this.newMessageIdentifier.isNew(message);
				}
			}
		});

		final MessageRecoverer messageRecoverer = getMessageRecoverer();
		retryInterceptor.setRecoverer(new MethodInvocationRecoverer<Void>() {
			public Void recover(Object[] args, Throwable cause) {
				Message message = (Message) args[1];
				if (messageRecoverer == null) {
					logger.warn("Message dropped on recovery: " + message, cause);
				} else {
					messageRecoverer.recover(message, cause);
				}
				// This is actually a normal outcome. It means the recovery was successful, but we don't want to consume
				// any more messages until the acks and commits are sent for this (problematic) message...
				throw new ImmediateAcknowledgeAmqpException("Recovered message forces ack (if ack mode requires it): "
						+ message, cause);
			}
		});

		retryInterceptor.setKeyGenerator(new MethodArgumentsKeyGenerator() {
			public Object getKey(Object[] args) {
				Message message = (Message) args[1];
				if (StatefulRetryOperationsInterceptorFactoryBean.this.messageKeyGenerator == null) {
					String messageId = message.getMessageProperties().getMessageId();
					if (messageId == null) {
						throw new FatalListenerExecutionException(
								"Illegal null id in message. Failed to manage retry for message: " + message);
					}
					return messageId;
				}
				else {
					return StatefulRetryOperationsInterceptorFactoryBean.this.messageKeyGenerator.getKey(message);
				}
			}
		});

		return retryInterceptor;

	}

			public Object getKey(Object[] args) {
				Message message = (Message) args[1];
				if (StatefulRetryOperationsInterceptorFactoryBean.this.messageKeyGenerator == null) {
					String messageId = message.getMessageProperties().getMessageId();
					if (messageId == null) {
						throw new FatalListenerExecutionException(
								"Illegal null id in message. Failed to manage retry for message: " + message);
					}
					return messageId;
				}
				else {
					return StatefulRetryOperationsInterceptorFactoryBean.this.messageKeyGenerator.getKey(message);
				}
			}

}