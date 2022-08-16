public class SerializerMessageConverter {
	@Override
	protected Message createMessage(Object object, MessageProperties messageProperties)
			throws MessageConversionException {
		byte[] bytes = null;
		if (object instanceof String) {
			try {
				bytes = ((String) object).getBytes(this.defaultCharset);
			} catch (UnsupportedEncodingException e) {
				throw new MessageConversionException("failed to convert Message content", e);
			}
			messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
			messageProperties.setContentEncoding(this.defaultCharset);
		}
		else if (object instanceof byte[]) {
			bytes = (byte[]) object;
			messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
		}
		else {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			try {
				this.serializer.serialize(object, output);
			}
			catch (IOException e) {
				throw new MessageConversionException("Cannot convert object to bytes", e);
			}
			bytes = output.toByteArray();
			messageProperties.setContentType(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT);
		}
		if (bytes != null) {
			messageProperties.setContentLength(bytes.length);
		}
		return new Message(bytes, messageProperties);
	}

	public void setDeserializer(Deserializer<Object> deserializer) {
		this.deserializer = deserializer;
		if (this.deserializer.getClass().equals(DefaultDeserializer.class)) {
			try {
				this.defaultDeserializerClassLoader = (ClassLoader) new DirectFieldAccessor(deserializer)
						.getPropertyValue("classLoader");
			}
			catch (Exception e) {
				// no-op
			}
			this.usingDefaultDeserializer = true;
		}
		else {
			this.usingDefaultDeserializer = false;
		}
	}

	@Override
	public Object fromMessage(Message message) throws MessageConversionException {
		Object content = null;
		MessageProperties properties = message.getMessageProperties();
		if (properties != null) {
			String contentType = properties.getContentType();
			if (contentType != null && contentType.startsWith("text") && !this.ignoreContentType) {
				String encoding = properties.getContentEncoding();
				if (encoding == null) {
					encoding = this.defaultCharset;
				}
				try {
					content = new String(message.getBody(), encoding);
				}
				catch (UnsupportedEncodingException e) {
					throw new MessageConversionException("failed to convert text-based Message content", e);
				}
			}
			else if (contentType != null && contentType.equals(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT)
					|| this.ignoreContentType) {
				try {
					ByteArrayInputStream inputStream = new ByteArrayInputStream(message.getBody());
					if (this.usingDefaultDeserializer) {
						content = deserialize(inputStream, this.deserializer);
					}
					else {
						content = this.deserializer.deserialize(inputStream);
					}
				}
				catch (IOException e) {
					throw new MessageConversionException("Could not convert message body", e);
				}
			}
		}
		if (content == null) {
			content = message.getBody();
		}
		return content;
	}

}