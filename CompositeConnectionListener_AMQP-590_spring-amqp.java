public class CompositeConnectionListener {
	public void onClose(Connection connection) {
		for (ConnectionListener delegate : this.delegates) {
			delegate.onClose(connection);
		}
	}

	public void onCreate(Connection connection) {
		for (ConnectionListener delegate : this.delegates) {
			delegate.onCreate(connection);
		}
	}

}