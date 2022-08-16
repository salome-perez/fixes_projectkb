public class RabbitListenerAnnotationBeanPostProcessor {
		private MessageHandlerMethodFactory createDefaultMessageHandlerMethodFactory() {
			DefaultMessageHandlerMethodFactory defaultFactory = new DefaultMessageHandlerMethodFactory();
			defaultFactory.setBeanFactory(RabbitListenerAnnotationBeanPostProcessor.this.beanFactory);
			defaultFactory.afterPropertiesSet();
			return defaultFactory;
		}

	private String getEndpointId(RabbitListener rabbitListener) {
		if (StringUtils.hasText(rabbitListener.id())) {
			return resolve(rabbitListener.id());
		}
		else {
			return "org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#" + this.counter.getAndIncrement();
		}
	}

}