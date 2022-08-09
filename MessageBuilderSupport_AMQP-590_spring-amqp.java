public class MessageBuilderSupport {
	public MessageBuilderSupport<T> setHeaderIfAbsent(String key, Object value) {
		if (this.properties.getHeaders().get(key) == null) {
			this.properties.setHeader(key, value);
		}
		return this;
	}

}