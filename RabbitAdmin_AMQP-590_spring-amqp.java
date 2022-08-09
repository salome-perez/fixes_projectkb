public class RabbitAdmin {
	private DeclareOk[] declareQueues(final Channel channel, final Queue... queues) throws IOException {
		List<DeclareOk> declareOks = new ArrayList<DeclareOk>(queues.length);
		for (int i = 0; i < queues.length; i++) {
			Queue queue = queues[i];
			if (!queue.getName().startsWith("amq.")) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("declaring Queue '" + queue.getName() + "'");
				}
				try {
					try {
						DeclareOk declareOk = channel.queueDeclare(queue.getName(), queue.isDurable(), queue.isExclusive(), queue.isAutoDelete(),
								queue.getArguments());
						declareOks.add(declareOk);
					}
					catch (IllegalArgumentException e) {
						if (this.logger.isDebugEnabled()) {
							this.logger.error("Exception while declaring queue: '" + queue.getName() + "'");
						}
						try {
							if (channel instanceof ChannelProxy) {
								((ChannelProxy) channel).getTargetChannel().close();
							}
						}
						catch (TimeoutException e1) {
						}
						throw new IOException(e);
					}
				}
				catch (IOException e) {
					logOrRethrowDeclarationException(queue, "queue", e);
				}
			} else if (this.logger.isDebugEnabled()) {
				this.logger.debug("Queue with name that starts with 'amq.' cannot be declared.");
			}
		}
		return declareOks.toArray(new DeclareOk[declareOks.size()]);
	}

	private void declareBindings(final Channel channel, final Binding... bindings) throws IOException {
		for (Binding binding : bindings) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Binding destination [" + binding.getDestination() + " (" + binding.getDestinationType()
						+ ")] to exchange [" + binding.getExchange() + "] with routing key [" + binding.getRoutingKey()
						+ "]");
			}

			try {
				if (binding.isDestinationQueue()) {
					if (!isDeclaringImplicitQueueBinding(binding)) {
						channel.queueBind(binding.getDestination(), binding.getExchange(), binding.getRoutingKey(),
								binding.getArguments());
					}
				} else {
					channel.exchangeBind(binding.getDestination(), binding.getExchange(), binding.getRoutingKey(),
							binding.getArguments());
				}
			}
			catch (IOException e) {
				logOrRethrowDeclarationException(binding, "binding", e);
			}
		}
	}

			public Object doInRabbit(Channel channel) throws Exception {
				declareExchanges(channel, exchanges.toArray(new Exchange[exchanges.size()]));
				declareQueues(channel, queues.toArray(new Queue[queues.size()]));
				declareBindings(channel, bindings.toArray(new Binding[bindings.size()]));
				return null;
			}

	private boolean isDeclaringDefaultExchange(Exchange exchange) {
		if (isDefaultExchange(exchange.getName())) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Default exchange is pre-declared by server.");
			}
			return true;
		}
		return false;
	}

	public void removeBinding(final Binding binding) {
		this.rabbitTemplate.execute(new ChannelCallback<Object>() {
			@Override
			public Object doInRabbit(Channel channel) throws Exception {
				if (binding.isDestinationQueue()) {
					if (isRemovingImplicitQueueBinding(binding)) {
						return null;
					}

					channel.queueUnbind(binding.getDestination(), binding.getExchange(), binding.getRoutingKey(),
							binding.getArguments());
				} else {
					channel.exchangeUnbind(binding.getDestination(), binding.getExchange(), binding.getRoutingKey(),
							binding.getArguments());
				}
				return null;
			}
		});
	}

	private boolean isDeletingDefaultExchange(String exchangeName) {
		if (isDefaultExchange(exchangeName)) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Default exchange cannot be deleted.");
			}
			return true;
		}
		return false;
	}

	private void declareExchanges(final Channel channel, final Exchange... exchanges) throws IOException {
		for (final Exchange exchange : exchanges) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("declaring Exchange '" + exchange.getName() + "'");
			}

			if (!isDeclaringDefaultExchange(exchange)) {
				try {
					if (exchange.isDelayed()) {
						Map<String, Object> arguments = exchange.getArguments();
						if (arguments == null) {
							arguments = new HashMap<String, Object>();
						}
						else {
							arguments = new HashMap<String, Object>(arguments);
						}
						arguments.put("x-delayed-type", exchange.getType());
						channel.exchangeDeclare(exchange.getName(), ExchangeTypes.DELAYED, exchange.isDurable(),
								exchange.isAutoDelete(), arguments);
					}
					else {
						channel.exchangeDeclare(exchange.getName(), exchange.getType(), exchange.isDurable(),
							exchange.isAutoDelete(), exchange.getArguments());
					}
				}
				catch (IOException e) {
					logOrRethrowDeclarationException(exchange, "exchange", e);
				}
			}
		}
	}

	@Override
	public Properties getQueueProperties(final String queueName) {
		Assert.hasText(queueName, "'queueName' cannot be null or empty");
		return this.rabbitTemplate.execute(new ChannelCallback<Properties>() {
			@Override
			public Properties doInRabbit(Channel channel) throws Exception {
				try {
					DeclareOk declareOk = channel.queueDeclarePassive(queueName);
					Properties props = new Properties();
					props.put(QUEUE_NAME, declareOk.getQueue());
					props.put(QUEUE_MESSAGE_COUNT, declareOk.getMessageCount());
					props.put(QUEUE_CONSUMER_COUNT, declareOk.getConsumerCount());
					return props;
				}
				catch (IllegalArgumentException e) {
					if (RabbitAdmin.this.logger.isDebugEnabled()) {
						RabbitAdmin.this.logger.error("Exception while fetching Queue properties: '" + queueName + "'",
								e);
					}
					try {
						if (channel instanceof ChannelProxy) {
							((ChannelProxy) channel).getTargetChannel().close();
						}
					}
					catch (TimeoutException e1) {
					}
					return null;
				}
				catch (Exception e) {
					if (RabbitAdmin.this.logger.isDebugEnabled()) {
						RabbitAdmin.this.logger.debug("Queue '" + queueName + "' does not exist");
					}
					return null;
				}
			}
		});
	}

	public DeclarationExceptionEvent getLastDeclarationExceptionEvent() {
		return this.lastDeclarationExceptionEvent;
	}

	private boolean isDeclaringImplicitQueueBinding(Binding binding) {
		if (isImplicitQueueBinding(binding)) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("The default exchange is implicitly bound to every queue, with a routing key equal to the queue name.");
			}
			return true;
		}
		return false;
	}

	@Override
	public void afterPropertiesSet() {

		synchronized (this.lifecycleMonitor) {

			if (this.running || !this.autoStartup) {
				return;
			}

			if (this.connectionFactory instanceof CachingConnectionFactory &&
					((CachingConnectionFactory) this.connectionFactory).getCacheMode() == CacheMode.CONNECTION) {
				this.logger.warn("RabbitAdmin auto declaration is not supported with CacheMode.CONNECTION");
				return;
			}

			this.connectionFactory.addConnectionListener(new ConnectionListener() {

				// Prevent stack overflow...
				private final AtomicBoolean initializing = new AtomicBoolean(false);

				@Override
				public void onCreate(Connection connection) {
					if (!initializing.compareAndSet(false, true)) {
						// If we are already initializing, we don't need to do it again...
						return;
					}
					try {
						initialize();
					}
					finally {
						initializing.compareAndSet(true, false);
					}
				}

				@Override
				public void onClose(Connection connection) {
				}

			});

			this.running = true;

		}
	}

	private boolean isRemovingImplicitQueueBinding(Binding binding) {
		if (isImplicitQueueBinding(binding)) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Cannot remove implicit default exchange binding to queue.");
			}
			return true;
		}
		return false;
	}

	private <T extends Throwable> void logOrRethrowDeclarationException(Declarable element, String elementType, T t)
			throws T {
		DeclarationExceptionEvent event = new DeclarationExceptionEvent(this, element, t);
		this.lastDeclarationExceptionEvent = event;
		if (this.applicationEventPublisher != null) {
			this.applicationEventPublisher.publishEvent(event);
		}
		if (this.ignoreDeclarationExceptions) {
			if (this.logger.isWarnEnabled()) {
				this.logger.warn("Failed to declare " + elementType
						+ (element == null ? "broker-generated" : ": " + element)
						+ ", continuing...", t);
			}
		}
		else {
			throw t;
		}
	}

	public void initialize() {

		if (this.applicationContext == null) {
			if (this.logger.isDebugEnabled()) {
				this.logger
						.debug("no ApplicationContext has been set, cannot auto-declare Exchanges, Queues, and Bindings");
			}
			return;
		}

		this.logger.debug("Initializing declarations");
		Collection<Exchange> contextExchanges = new LinkedList<Exchange>(this.applicationContext.getBeansOfType(Exchange.class).values());
		Collection<Queue> contextQueues = new LinkedList<Queue>(this.applicationContext.getBeansOfType(Queue.class).values());
		Collection<Binding> contextBindings = new LinkedList<Binding>(this.applicationContext.getBeansOfType(Binding.class).values());

		@SuppressWarnings("rawtypes")
		Collection<Collection> collections = this.applicationContext.getBeansOfType(Collection.class).values();
		for (Collection<?> collection : collections) {
			if (collection.size() > 0 && collection.iterator().next() instanceof Declarable) {
				for (Object declarable : collection) {
					if (declarable instanceof Exchange) {
						contextExchanges.add((Exchange) declarable);
					}
					else if (declarable instanceof Queue) {
						contextQueues.add((Queue) declarable);
					}
					else if (declarable instanceof Binding) {
						contextBindings.add((Binding) declarable);
					}
				}
			}
		}

		final Collection<Exchange> exchanges = filterDeclarables(contextExchanges);
		final Collection<Queue> queues = filterDeclarables(contextQueues);
		final Collection<Binding> bindings = filterDeclarables(contextBindings);

		for (Exchange exchange : exchanges) {
			if (!exchange.isDurable() || exchange.isAutoDelete()) {
				this.logger.info("Auto-declaring a non-durable or auto-delete Exchange ("
						+ exchange.getName()
						+ ") durable:" + exchange.isDurable() + ", auto-delete:" + exchange.isAutoDelete() + ". "
						+ "It will be deleted by the broker if it shuts down, and can be redeclared by closing and "
						+ "reopening the connection.");
			}
		}

		for (Queue queue : queues) {
			if (!queue.isDurable() || queue.isAutoDelete() || queue.isExclusive()) {
				this.logger.info("Auto-declaring a non-durable, auto-delete, or exclusive Queue ("
						+ queue.getName()
						+ ") durable:" + queue.isDurable() + ", auto-delete:" + queue.isAutoDelete() + ", exclusive:"
						+ queue.isExclusive() + ". "
						+ "It will be redeclared if the broker stops and is restarted while the connection factory is "
						+ "alive, but all messages will be lost.");
			}
		}

		this.rabbitTemplate.execute(new ChannelCallback<Object>() {
			@Override
			public Object doInRabbit(Channel channel) throws Exception {
				declareExchanges(channel, exchanges.toArray(new Exchange[exchanges.size()]));
				declareQueues(channel, queues.toArray(new Queue[queues.size()]));
				declareBindings(channel, bindings.toArray(new Binding[bindings.size()]));
				return null;
			}
		});
		this.logger.debug("Declarations finished");

	}

}