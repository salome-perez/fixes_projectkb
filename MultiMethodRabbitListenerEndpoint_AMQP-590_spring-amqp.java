public class MultiMethodRabbitListenerEndpoint {
		@Override
		protected Address getReplyToAddress(Message request) throws Exception {
			Address replyTo = request.getMessageProperties().getReplyToAddress();
			Address defaultReplyTo = null;
			if (MultiMethodRabbitListenerEndpoint.this.delegatingHandler != null) {
				defaultReplyTo = MultiMethodRabbitListenerEndpoint.this.delegatingHandler.getDefaultReplyTo();
			}
			if (replyTo == null && defaultReplyTo == null) {
				throw new AmqpException(
						"Cannot determine ReplyTo message property value: " +
								"Request message does not contain reply-to property, " +
								"and no @SendTo annotation found.");
			}
			return replyTo == null ? defaultReplyTo : replyTo;
		}

}