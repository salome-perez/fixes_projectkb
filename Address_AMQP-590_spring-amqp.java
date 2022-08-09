public class Address {
	@Override
	public int hashCode() {
		int result = this.exchangeName != null ? this.exchangeName.hashCode() : 0;
		result = 31 * result + (this.routingKey != null ? this.routingKey.hashCode() : 0);
		return result;
	}

}