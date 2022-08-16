public class AbstractRabbitListenerEndpoint {
	protected BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	public Collection<String> getQueueNames() {
		return this.queueNames;
	}

	public RabbitAdmin getAdmin() {
		return this.admin;
	}

	public Integer getPriority() {
		return this.priority;
	}

	protected BeanExpressionContext getBeanExpressionContext() {
		return this.expressionContext;
	}

	public Collection<Queue> getQueues() {
		return this.queues;
	}

	protected BeanExpressionResolver getResolver() {
		return this.resolver;
	}

	public boolean isExclusive() {
		return this.exclusive;
	}

}