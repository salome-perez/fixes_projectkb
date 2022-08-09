public class Message {
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("(");
		buffer.append("Body:'" + this.getBodyContentAsString() + "'");
		if (this.messageProperties != null) {
			buffer.append(" ").append(this.messageProperties.toString());
		}
		buffer.append(")");
		return buffer.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Message other = (Message) obj;
		if (!Arrays.equals(this.body, other.body)) {
			return false;
		}
		if (this.messageProperties == null) {
			if (other.messageProperties != null) {
				return false;
			}
		}
		else if (!this.messageProperties.equals(other.messageProperties)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(this.body);
		result = prime * result + ((this.messageProperties == null) ? 0 : this.messageProperties.hashCode());
		return result;
	}

	private String getBodyContentAsString() {
		if (this.body == null) {
			return null;
		}
		try {
			String contentType = (this.messageProperties != null) ? this.messageProperties.getContentType() : null;
			if (MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT.equals(contentType)) {
				return SerializationUtils.deserialize(this.body).toString();
			}
			if (MessageProperties.CONTENT_TYPE_TEXT_PLAIN.equals(contentType)
					|| MessageProperties.CONTENT_TYPE_JSON.equals(contentType)
					|| MessageProperties.CONTENT_TYPE_JSON_ALT.equals(contentType)
					|| MessageProperties.CONTENT_TYPE_XML.equals(contentType)) {
				return new String(this.body, ENCODING);
			}
		}
		catch (Exception e) {
			// ignore
		}
		// Comes out as '[B@....b' (so harmless)
		return this.body.toString()+"(byte["+this.body.length+"])";//NOSONAR
	}

}