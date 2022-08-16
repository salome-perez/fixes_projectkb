public class MessageBatch {
	public String getExchange() {
		return this.exchange;
	}

	public Message getMessage() {
		return this.message;
	}

	public String getRoutingKey() {
		return this.routingKey;
	}

}