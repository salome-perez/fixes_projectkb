public class AmqpAppender {
    public String getApplicationId() {
        return this.applicationId;
    }
	public String getRoutingKeyPattern() {
		return this.routingKeyPattern;
	}
    @Override
    public void run() {
        try {
            RabbitTemplate rabbitTemplate = new RabbitTemplate(AmqpAppender.this.connectionFactory);
            while (true) {
                final Event event = AmqpAppender.this.events.take();
                LoggingEvent logEvent = event.getEvent();

                String name = logEvent.getLogger().getName();
                Level level = logEvent.getLevel();

                MessageProperties amqpProps = new MessageProperties();
                amqpProps.setDeliveryMode(AmqpAppender.this.deliveryMode);
                amqpProps.setContentType(AmqpAppender.this.contentType);
                if (null != AmqpAppender.this.contentEncoding) {
                    amqpProps.setContentEncoding(AmqpAppender.this.contentEncoding);
                }
                amqpProps.setHeader(CATEGORY_NAME, name);
                amqpProps.setHeader(CATEGORY_LEVEL, level.toString());
                if (AmqpAppender.this.generateId) {
                    amqpProps.setMessageId(UUID.randomUUID().toString());
                }

                // Set applicationId, if we're using one
                if (null != AmqpAppender.this.applicationId) {
                    amqpProps.setAppId(AmqpAppender.this.applicationId);
                }

                // Set timestamp
                Calendar tstamp = Calendar.getInstance();
                tstamp.setTimeInMillis(logEvent.getTimeStamp());
                amqpProps.setTimestamp(tstamp.getTime());

                // Copy properties in from MDC
                @SuppressWarnings("rawtypes")
                Map props = event.getProperties();
                @SuppressWarnings("unchecked")
                Set<Entry<?,?>> entrySet = props.entrySet();
                for (Entry<?, ?> entry : entrySet) {
                    amqpProps.setHeader(entry.getKey().toString(), entry.getValue());
                }
                LocationInfo locInfo = logEvent.getLocationInformation();
                if (!"?".equals(locInfo.getClassName())) {
                    amqpProps.setHeader(
                            "location",
                            String.format("%s.%s()[%s]", locInfo.getClassName(), locInfo.getMethodName(),
                                    locInfo.getLineNumber()));
                }

                StringBuilder msgBody;
                String routingKey;
                synchronized (AmqpAppender.this.layoutMutex) {
                    msgBody = new StringBuilder(layout.format(logEvent));
                    routingKey = AmqpAppender.this.routingKeyLayout.format(logEvent);
                }
                if (layout.ignoresThrowable() && null != logEvent.getThrowableInformation()) {
                    ThrowableInformation tinfo = logEvent.getThrowableInformation();
                    for (String line : tinfo.getThrowableStrRep()) {
                        msgBody.append(String.format("%s%n", line));
                    }
                }

                // Send a message
                try {
                    Message message = null;
                    if (AmqpAppender.this.charset != null) {
                        try {
                            message = new Message(msgBody.toString().getBytes(AmqpAppender.this.charset), amqpProps);
                        }
                        catch (UnsupportedEncodingException e) {/* fall back to default */}
                    }
                    if (message == null) {
                        message = new Message(msgBody.toString().getBytes(), amqpProps);//NOSONAR (default charset)
                    }
                    message = postProcessMessageBeforeSend(message, event);
                    rabbitTemplate.send(AmqpAppender.this.exchangeName, routingKey, message);
                }
                catch (AmqpException e) {
                    int retries = event.incrementRetries();
                    if (retries < AmqpAppender.this.maxSenderRetries) {
                        // Schedule a retry based on the number of times I've tried to re-send this
                        AmqpAppender.this.retryTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                AmqpAppender.this.events.add(event);
                            }
                        }, (long) (Math.pow(retries, Math.log(retries)) * 1000));
                    }
                    else {
                        errorHandler.error(
                                "Could not send log message " + logEvent.getRenderedMessage() + " after "
                                        + AmqpAppender.this.maxSenderRetries + " retries",
                                e, ErrorCode.WRITE_FAILURE, logEvent);
                    }
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

	public String getContentType() {
		return this.contentType;
	}
	protected void setUpExchangeDeclaration() {
		RabbitAdmin admin = new RabbitAdmin(this.connectionFactory);
		if (this.declareExchange) {
			Exchange x;
			if ("topic".equals(this.exchangeType)) {
				x = new TopicExchange(this.exchangeName, this.durable, this.autoDelete);
			}
			else if ("direct".equals(this.exchangeType)) {
				x = new DirectExchange(this.exchangeName, this.durable, this.autoDelete);
			}
			else if ("fanout".equals(this.exchangeType)) {
				x = new FanoutExchange(this.exchangeName, this.durable, this.autoDelete);
			}
			else if ("headers".equals(this.exchangeType)) {
				x = new HeadersExchange(this.exchangeType, this.durable, this.autoDelete);
			}
			else {
				x = new TopicExchange(this.exchangeName, this.durable, this.autoDelete);
			}
			this.connectionFactory.addConnectionListener(new DeclareExchangeConnectionListener(x, admin));
		}
	}

	public String getPassword() {
		return this.password;
	}
	public String getExchangeName() {
		return this.exchangeName;
	}
	public String getExchangeType() {
		return this.exchangeType;
	}
    public Map getProperties() {
        return this.properties;
    }
	public String getUsername() {
		return this.username;
	}
	@Override
	public void close() {
		if (null != this.senderPool) {
			this.senderPool.shutdownNow();
			this.senderPool = null;
		}
		if (null != this.connectionFactory) {
			this.connectionFactory.destroy();
		}
		this.retryTimer.cancel();
	}
	public String getVirtualHost() {
		return this.virtualHost;
	}
	public String getHost() {
		return this.host;
	}
	protected void startSenders() {
		this.senderPool = Executors.newCachedThreadPool();
		for (int i = 0; i < this.senderPoolSize; i++) {
			this.senderPool.submit(new EventSender());
		}
	}
	@Override
	public void activateOptions() {
		this.routingKeyLayout = new PatternLayout(this.routingKeyPattern
				.replaceAll("%X\\{applicationId\\}", this.applicationId));
		this.connectionFactory = new CachingConnectionFactory();
		this.connectionFactory.setHost(this.host);
		this.connectionFactory.setPort(this.port);
		this.connectionFactory.setUsername(this.username);
		this.connectionFactory.setPassword(this.password);
		this.connectionFactory.setVirtualHost(this.virtualHost);
		setUpExchangeDeclaration();
		startSenders();
	}
    public int incrementRetries() {
        return this.retries.incrementAndGet();
    }
	public String getCharset() {
		return this.charset;
	}
    public LoggingEvent getEvent() {
        return this.event;
    }
	public String getContentEncoding() {
		return this.contentEncoding;
	}
}