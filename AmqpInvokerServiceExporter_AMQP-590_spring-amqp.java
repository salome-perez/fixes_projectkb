public class AmqpInvokerServiceExporter {
	public AmqpTemplate getAmqpTemplate() {
		return this.amqpTemplate;
	}

	private void send(Object object, Address replyToAddress) {
		Message message = this.messageConverter.toMessage(object, new MessageProperties());

		getAmqpTemplate().send(replyToAddress.getExchangeName(), replyToAddress.getRoutingKey(), message);
	}

	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

	public void onMessage(Message message) {
		Address replyToAddress = message.getMessageProperties().getReplyToAddress();
		if (replyToAddress == null) {
			throw new AmqpRejectAndDontRequeueException("No replyToAddress in inbound AMQP Message");
		}

		Object invocationRaw = this.messageConverter.fromMessage(message);

		RemoteInvocationResult remoteInvocationResult;
		if (invocationRaw == null || !(invocationRaw instanceof RemoteInvocation)) {
			remoteInvocationResult =  new RemoteInvocationResult(
					new IllegalArgumentException("The message does not contain a RemoteInvocation payload"));
		}
		else {
			RemoteInvocation invocation = (RemoteInvocation) invocationRaw;
			remoteInvocationResult = invokeAndCreateResult(invocation, getService());
		}
		send(remoteInvocationResult, replyToAddress);
	}

}