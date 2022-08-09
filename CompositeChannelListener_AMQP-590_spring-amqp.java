public class CompositeChannelListener {
	public void onCreate(Channel channel, boolean transactional) {
		for (ChannelListener delegate : this.delegates) {
			delegate.onCreate(channel, transactional);
		}
	}

}