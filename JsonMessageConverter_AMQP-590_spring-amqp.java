public class JsonMessageConverter {
	@Override
	protected Message createMessage(Object objectToConvert,
			MessageProperties messageProperties)
			throws MessageConversionException {
		byte[] bytes = null;
		try {
			String jsonString = this.jsonObjectMapper
					.writeValueAsString(objectToConvert);
			bytes = jsonString.getBytes(getDefaultCharset());
		}
		catch (IOException e) {
			throw new MessageConversionException(
					"Failed to convert Message content", e);
		}
		messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		messageProperties.setContentEncoding(getDefaultCharset());
		if (bytes != null) {
			messageProperties.setContentLength(bytes.length);
		}

		if (getClassMapper() == null) {
			getJavaTypeMapper().fromJavaType(this.jsonObjectMapper.constructType(objectToConvert.getClass()),
					messageProperties);

		}
		else {
			getClassMapper().fromClass(objectToConvert.getClass(),
					messageProperties);

		}

		return new Message(bytes, messageProperties);

	}

	public JavaTypeMapper getJavaTypeMapper() {
		return this.javaTypeMapper;
	}

	protected void initializeJsonObjectMapper() {
		this.jsonObjectMapper
				.configure(
						DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,
						false);
	}

	private Object convertBytesToObject(byte[] body, String encoding,
			Class<?> targetClass) throws JsonParseException,
			JsonMappingException, IOException {
		String contentAsString = new String(body, encoding);
		return this.jsonObjectMapper.readValue(contentAsString, this.jsonObjectMapper.constructType(targetClass));
	}

}