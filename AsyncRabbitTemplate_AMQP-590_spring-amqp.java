public class AsyncRabbitTemplate {
	@Override
	public void confirm(CorrelationData correlationData, boolean ack, String cause) {
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Confirm: " + correlationData + ", ack=" + ack
					+ (cause == null ? "" : (", cause: " + cause)));
		}
		String correlationId = correlationData.getId();
		if (correlationId != null) {
			RabbitFuture<?> future = this.pending.get(correlationId);
			if (future != null) {
				future.setNackCause(cause);
				((SettableListenableFuture<Boolean>) future.getConfirm()).set(ack);
			}
			else {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Confirm: " + correlationData + ", ack=" + ack
							+ (cause == null ? "" : (", cause: " + cause))
							+ " no pending future - either canceled or the reply is already received");
				}
			}
		}
	}

		public Message postProcessMessage(Message message) throws AmqpException {
			Message messageToSend = message;
			if (this.userPostProcessor != null) {
				messageToSend = this.userPostProcessor.postProcessMessage(message);
			}
			String correlationId = getOrSetCorrelationIdAndSetReplyTo(messageToSend);
			this.future = new RabbitConverterFuture<C>(correlationId, message);
			if (this.correlationData != null && this.correlationData.getId() == null) {
				this.correlationData.setId(correlationId);
				this.future.setConfirm(new SettableListenableFuture<Boolean>());
			}
			AsyncRabbitTemplate.this.pending.put(correlationId, this.future);
			return messageToSend;
		}

		public String getNackCause() {
			return this.nackCause;
		}

			@Override
			public void run() {
				AsyncRabbitTemplate.this.pending.remove(RabbitFuture.this.correlationId);
				setException(new AmqpReplyTimeoutException("Reply timed out", RabbitFuture.this.requestMessage));
			}

		}

	}

	public class RabbitMessageFuture extends RabbitFuture<Message> {

		public RabbitMessageFuture(String correlationId, Message requestMessage) {
			super(correlationId, requestMessage);
		}

	}

	public class RabbitConverterFuture<C> extends RabbitFuture<C> {

		public RabbitConverterFuture(String correlationId, Message requestMessage) {
			super(correlationId, requestMessage);
		}

	}

	private final class CorrelationMessagePostProcessor<C> implements MessagePostProcessor {

		private final MessagePostProcessor userPostProcessor;

		private final CorrelationData correlationData;

		private volatile RabbitConverterFuture<C> future;

		private CorrelationMessagePostProcessor(MessagePostProcessor userPostProcessor,
				CorrelationData correlationData) {
			this.userPostProcessor = userPostProcessor;
			this.correlationData = correlationData;
		}

		@Override
		public Message postProcessMessage(Message message) throws AmqpException {
			Message messageToSend = message;
			if (this.userPostProcessor != null) {
				messageToSend = this.userPostProcessor.postProcessMessage(message);
			}
			String correlationId = getOrSetCorrelationIdAndSetReplyTo(messageToSend);
			this.future = new RabbitConverterFuture<C>(correlationId, message);
			if (this.correlationData != null && this.correlationData.getId() == null) {
				this.correlationData.setId(correlationId);
				this.future.setConfirm(new SettableListenableFuture<Boolean>());
			}
			AsyncRabbitTemplate.this.pending.put(correlationId, this.future);
			return messageToSend;
		}

		public ListenableFuture<Boolean> getConfirm() {
			return this.confirm;
		}

	@SuppressWarnings("unchecked")
	@Override
	public void onMessage(Message message) {
		MessageProperties messageProperties = message.getMessageProperties();
		if (messageProperties != null) {
			byte[] correlationId = messageProperties.getCorrelationId();
			if (correlationId != null) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("onMessage: " + message);
				}
				RabbitFuture<?> future = this.pending.remove(new String(correlationId, this.charset));
				if (future != null) {
					if (future instanceof AsyncRabbitTemplate.RabbitConverterFuture) {
						Object converted = this.template.getMessageConverter().fromMessage(message);
						((RabbitConverterFuture<Object>) future).set(converted);
					}
					else {
						((RabbitMessageFuture) future).set(message);
					}
				}
				else {
					if (this.logger.isWarnEnabled()) {
						this.logger.warn("No pending reply - perhaps timed out: " + message);
					}
				}
			}
		}
	}

}