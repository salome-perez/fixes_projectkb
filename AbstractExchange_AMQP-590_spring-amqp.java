public class AbstractExchange {
	@Override
	public String toString() {
		return "Exchange [name=" + this.name +
						 ", type=" + getType() +
						 ", durable=" + this.durable +
						 ", autoDelete=" + this.autoDelete +
						 ", arguments="	+ this.arguments + "]";
	}

}