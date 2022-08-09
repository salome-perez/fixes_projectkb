public class PublisherCallbackChannelImpl {
	@Override
	public Channel getDelegate() {
		return this.delegate;
	}

	private synchronized void generateNacksForPendingAcks(String cause) {
		for (Entry<Listener, SortedMap<Long, PendingConfirm>> entry : this.pendingConfirms.entrySet()) {
			Listener listener = entry.getKey();
			for (Entry<Long, PendingConfirm> confirmEntry : entry.getValue().entrySet()) {
				confirmEntry.getValue().setCause(cause);
				if (this.logger.isDebugEnabled()) {
					this.logger.debug(this.toString() + " PC:Nack:(close):" + confirmEntry.getKey());
				}
				processAck(confirmEntry.getKey(), false, false, false);
			}
			listener.revoke(this);
		}
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("PendingConfirms cleared");
		}
		this.pendingConfirms.clear();
		this.listenerForSeq.clear();
		this.listeners.clear();
	}

	private void doHandleConfirm(boolean ack, Listener listener, PendingConfirm pendingConfirm) {
		try {
			if (listener.isConfirmListener()) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Sending confirm " + pendingConfirm);
				}
				listener.handleConfirm(pendingConfirm, ack);
			}
		}
		catch (Exception e) {
			this.logger.error("Exception delivering confirm", e);
		}
	}

	private synchronized void processAck(long seq, boolean ack, boolean multiple, boolean remove) {
		if (multiple) {
			Map<Long, Listener> involvedListeners = this.listenerForSeq.headMap(seq + 1);
			// eliminate duplicates
			Set<Listener> listeners = new HashSet<Listener>(involvedListeners.values());
			for (Listener involvedListener : listeners) {
				// find all unack'd confirms for this listener and handle them
				SortedMap<Long, PendingConfirm> confirmsMap = this.pendingConfirms.get(involvedListener);
				if (confirmsMap != null) {
					Map<Long, PendingConfirm> confirms = confirmsMap.headMap(seq + 1);
					Iterator<Entry<Long, PendingConfirm>> iterator = confirms.entrySet().iterator();
					while (iterator.hasNext()) {
						Entry<Long, PendingConfirm> entry = iterator.next();
						PendingConfirm value = entry.getValue();
						iterator.remove();
						doHandleConfirm(ack, involvedListener, value);
					}
				}
			}
			List<Long> seqs = new ArrayList<Long>(involvedListeners.keySet());
			for (Long key : seqs) {
				this.listenerForSeq.remove(key);
			}
		}
		else {
			Listener listener = this.listenerForSeq.remove(seq);
			if (listener != null) {
				SortedMap<Long, PendingConfirm> confirmsForListener = this.pendingConfirms.get(listener);
				PendingConfirm pendingConfirm;
				if (remove) {
					pendingConfirm = confirmsForListener.remove(seq);
				}
				else {
					pendingConfirm = confirmsForListener.get(seq);
				}
				if (pendingConfirm != null) {
					doHandleConfirm(ack, listener, pendingConfirm);
				}
			}
			else {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug(this.delegate.toString() + " No listener for seq:" + seq);
				}
			}
		}
	}

	@Override
	public void handleReturn(int replyCode,
			String replyText,
			String exchange,
			String routingKey,
			AMQP.BasicProperties properties,
			byte[] body) throws IOException
	{
		String uuidObject = properties.getHeaders().get(RETURN_CORRELATION_KEY).toString();
		Listener listener = this.listeners.get(uuidObject);
		if (listener == null || !listener.isReturnListener()) {
			if (this.logger.isWarnEnabled()) {
				this.logger.warn("No Listener for returned message");
			}
		}
		else {
			listener.handleReturn(replyCode, replyText, exchange, routingKey, properties, body);
		}
	}

	@Override
	public void close() throws IOException, TimeoutException {
		try {
			this.delegate.close();
		}
		catch (AlreadyClosedException e) {
			if (this.logger.isTraceEnabled()) {
				this.logger.trace(this.delegate + " is already closed");
			}
		}
		generateNacksForPendingAcks("Channel closed by application");
	}

	@Override
	public void handleNack(long seq, boolean multiple)
			throws IOException {
		if (this.logger.isDebugEnabled()) {
			this.logger.debug(this.toString() + " PC:Nack:" + seq + ":" + multiple);
		}
		this.processAck(seq, false, multiple, true);
	}

	@Override
	public void addListener(Listener listener) {
		Assert.notNull(listener, "Listener cannot be null");
		if (this.listeners.size() == 0) {
			this.delegate.addConfirmListener(this);
			this.delegate.addReturnListener(this);
		}
		if (this.listeners.putIfAbsent(listener.getUUID(), listener) == null) {
			this.pendingConfirms.put(listener, new ConcurrentSkipListMap<Long, PendingConfirm>());
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Added listener " + listener);
			}
		}
	}

	@Override
	public void handleAck(long seq, boolean multiple)
			throws IOException {
		if (this.logger.isDebugEnabled()) {
			this.logger.debug(this.toString() + " PC:Ack:" + seq + ":" + multiple);
		}
		this.processAck(seq, true, multiple, true);
	}

}