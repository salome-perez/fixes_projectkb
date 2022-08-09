public class BindingFactoryBean {
	public Binding getObject() throws Exception {
		String destination;
		DestinationType destinationType;
		if (this.destinationQueue != null) {
			destination = this.destinationQueue.getName();
			destinationType = DestinationType.QUEUE;
		} else {
			destination = this.destinationExchange.getName();
			destinationType = DestinationType.EXCHANGE;
		}
		Binding binding = new Binding(destination, destinationType, this.exchange, this.routingKey, this.arguments);
		if (this.shouldDeclare != null) {
			binding.setShouldDeclare(this.shouldDeclare);
		}
		if (this.adminsThatShouldDeclare != null) {
			binding.setAdminsThatShouldDeclare((Object[]) this.adminsThatShouldDeclare);
		}
		return binding;
	}

}