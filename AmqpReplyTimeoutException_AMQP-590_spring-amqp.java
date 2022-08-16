public class AmqpReplyTimeoutException {
	@Override
	public String toString() {
		return "AmqpReplyTimeoutException [" + getMessage() + ", requestMessage=" + this.requestMessage + "]";
	}

	public Message getRequestMessage() {
		return this.requestMessage;
	}

}