public class Binding {
	public boolean isDestinationQueue() {
		return DestinationType.QUEUE.equals(this.destinationType);
	}

	@Override
	public String toString() {
		return "Binding [destination=" + this.destination + ", exchange=" + this.exchange + ", routingKey="
					+ this.routingKey + "]";
	}

}