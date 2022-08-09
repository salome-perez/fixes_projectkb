public class SimpleBatchingStrategy {
	public MessageBatch addToBatch(String exchange, String routingKey, Message message) {
		if (this.exchange != null) {
			Assert.isTrue(this.exchange.equals(exchange), "Cannot send to different exchanges in the same batch");
		}
		else {
			this.exchange = exchange;
		}
		if (this.routingKey != null) {
			Assert.isTrue(this.routingKey.equals(routingKey), "Cannot send with different routing keys in the same batch");
		}
		else {
			this.routingKey = routingKey;
		}
		int bufferUse = 4 + message.getBody().length;
		MessageBatch batch = null;
		if (this.messages.size() > 0 && this.currentSize + bufferUse > this.bufferLimit) {
			batch = doReleaseBatch();
			this.exchange = exchange;
			this.routingKey = routingKey;
		}
		this.currentSize += bufferUse;
		this.messages.add(message);
		if (batch == null && (this.messages.size() >= this.batchSize
								|| this.currentSize >= this.bufferLimit)) {
			batch = doReleaseBatch();
		}
		return batch;
	}

}