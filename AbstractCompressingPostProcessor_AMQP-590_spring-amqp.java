public class AbstractCompressingPostProcessor {
	public Message postProcessMessage(Message message) throws AmqpException {
		ByteArrayOutputStream zipped = new ByteArrayOutputStream();
		try {
			OutputStream zipper = getCompressorStream(zipped);
			FileCopyUtils.copy(new ByteArrayInputStream(message.getBody()), zipper);
			MessageProperties messageProperties = message.getMessageProperties();
			String currentEncoding = messageProperties.getContentEncoding();
			messageProperties
					.setContentEncoding(getEncoding() + (currentEncoding == null ? "" : ":" + currentEncoding));
			if (this.autoDecompress) {
				messageProperties.setHeader(MessageProperties.SPRING_AUTO_DECOMPRESS, true);
			}
			byte[] compressed = zipped.toByteArray();
			if (this.logger.isTraceEnabled()) {
				this.logger.trace("Compressed " + message.getBody().length + " to " + compressed.length);
			}
			return new Message(compressed, messageProperties);
		}
		catch (IOException e) {
			throw new AmqpIOException(e);
		}
	}

}