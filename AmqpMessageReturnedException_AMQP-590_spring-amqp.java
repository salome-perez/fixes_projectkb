public class AmqpMessageReturnedException {
	public String getExchange() {
		return this.exchange;
	}

	public int getReplyCode() {
		return this.replyCode;
	}

	public Message getReturnedMessage() {
		return this.returnedMessage;
	}

	@Override
	public String toString() {
		return "AmqpMessageReturnedException: "
				+ getMessage()
				+ "[returnedMessage=" + this.returnedMessage + ", replyCode=" + this.replyCode
				+ ", replyText=" + this.replyText + ", exchange=" + this.exchange + ", routingKey=" + this.routingKey
				+ "]";
	}

	public String getReplyText() {
		return this.replyText;
	}

	public String getRoutingKey() {
		return this.routingKey;
	}

}