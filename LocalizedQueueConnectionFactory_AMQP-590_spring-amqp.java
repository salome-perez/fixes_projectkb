public class LocalizedQueueConnectionFactory {
	private ConnectionFactory determineConnectionFactory(String queue) {
		for (int i = 0; i < this.adminUris.length; i++) {
			String adminUri = this.adminUris[i];
			if (!adminUri.endsWith("/api/")) {
				adminUri += "/api/";
			}
			try {
				Client client = createClient(adminUri, this.username, this.password);
				QueueInfo queueInfo = client.getQueue(this.vhost, queue);
				if (queueInfo != null) {
					String node = queueInfo.getNode();
					if (node != null) {
						String uri = this.nodeToAddress.get(node);
						if (uri != null) {
							return nodeConnectionFactory(queue, node, uri);
						}
						if (this.logger.isDebugEnabled()) {
							this.logger.debug("No match for node: " + node);
						}
					}
				}
				else {
					throw new AmqpException("Admin returned null QueueInfo");
				}
			}
			catch (Exception e) {
				this.logger.warn("Failed to determine queue location for: " + queue + " at: " +
						adminUri + ": " + e.getMessage());
			}
		}
		this.logger.warn("Failed to determine queue location for: " + queue + ", using default connection factory");
		return null;
	}

	private synchronized ConnectionFactory nodeConnectionFactory(String queue, String node, String address)
			throws Exception {
		if (this.logger.isInfoEnabled()) {
			this.logger.info("Queue: " + queue + " is on node: " + node + " at: " + address);
		}
		ConnectionFactory cf = this.nodeFactories.get(node);
		if (cf == null) {
			cf = createConnectionFactory(address, node);
			if (this.logger.isInfoEnabled()) {
				this.logger.info("Created new connection factory: " + cf);
			}
			this.nodeFactories.put(node, cf);
		}
		return cf;
	}

}