public class MarshallingMessageConverter {
	@Override
	protected Message createMessage(Object object, MessageProperties messageProperties) throws MessageConversionException {
		try {
			if (this.contentType != null) {
				messageProperties.setContentType(this.contentType);
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			StreamResult streamResult = new StreamResult(bos);
			this.marshaller.marshal(object, streamResult);
			return new Message(bos.toByteArray(), messageProperties);
		}
		catch (XmlMappingException ex) {
			throw new MessageConversionException("Could not marshal [" + object + "]", ex);
		}
		catch (IOException ex) {
			throw new MessageConversionException("Could not marshal  [" + object + "]", ex);
		}
	}

	@Override
	public Object fromMessage(Message message) throws MessageConversionException {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(message.getBody());
			StreamSource source = new StreamSource(bis);
			return this.unmarshaller.unmarshal(source);
		}
		catch (IOException ex) {
			throw new MessageConversionException("Could not access message content: " + message, ex);
		}
		catch (XmlMappingException ex) {
			throw new MessageConversionException("Could not unmarshal message: " + message, ex);
		}
	}

}