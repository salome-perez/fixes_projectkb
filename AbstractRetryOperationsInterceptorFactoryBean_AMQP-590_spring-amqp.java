public class AbstractRetryOperationsInterceptorFactoryBean {
	protected RetryOperations getRetryOperations() {
		return this.retryTemplate;
	}

	protected MessageRecoverer getMessageRecoverer() {
		return this.messageRecoverer;
	}

}