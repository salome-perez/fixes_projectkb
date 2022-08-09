public class ConsumerChannelRegistry {
		private Channel getChannel() {
			return this.channel;
		}

		private ConnectionFactory getConnectionFactory() {
			return this.connectionFactory;
		}

}