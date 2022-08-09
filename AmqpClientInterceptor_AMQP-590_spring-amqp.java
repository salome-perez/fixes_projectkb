public class AmqpClientInterceptor {
	public AmqpTemplate getAmqpTemplate() {
		return this.amqpTemplate;
	}

	public Object invoke(MethodInvocation invocation) throws Throwable {
		RemoteInvocation remoteInvocation = getRemoteInvocationFactory().createRemoteInvocation(invocation);

		Object rawResult;
		if (getRoutingKey() == null) {
			// Use the template's default routing key
			rawResult = this.amqpTemplate.convertSendAndReceive(remoteInvocation);
		}
		else {
			rawResult = this.amqpTemplate.convertSendAndReceive(this.routingKey, remoteInvocation);
		}

		if (rawResult == null) {
			throw new RemoteProxyFailureException("No reply received from '" +
					remoteInvocation.getMethodName() +
					"' with arguments '" +
					Arrays.asList(remoteInvocation.getArguments()) +
					"' - perhaps a timeout in the template?", null);
		}
		else if (!(rawResult instanceof RemoteInvocationResult)) {
			throw new RemoteProxyFailureException("Expected a result of type "
					+ RemoteInvocationResult.class.getCanonicalName() + " but found "
					+ rawResult.getClass().getCanonicalName(), null);
		}

		RemoteInvocationResult result = (RemoteInvocationResult) rawResult;
		return result.recreate();
	}

	public RemoteInvocationFactory getRemoteInvocationFactory() {
		return this.remoteInvocationFactory;
	}

	public String getRoutingKey() {
		return this.routingKey;
	}

}