public class MessageProperties {
	public String getCorrelationIdString() {
		return this.correlationIdString;
	}

	@Override
	public String toString() {
		return "MessageProperties [headers=" + this.headers + ", timestamp=" + this.timestamp + ", messageId=" + this.messageId
				+ ", userId=" + this.userId + ", appId=" + this.appId + ", clusterId=" + this.clusterId + ", type=" + this.type
				+ ", correlationId=" + Arrays.toString(this.correlationId) + ", replyTo=" + this.replyTo + ", contentType="
				+ this.contentType + ", contentEncoding=" + this.contentEncoding + ", contentLength=" + this.contentLength
				+ ", deliveryMode=" + this.deliveryMode + ", expiration=" + this.expiration + ", priority=" + this.priority
				+ ", redelivered=" + this.redelivered + ", receivedExchange=" + this.receivedExchange + ", receivedRoutingKey="
				+ this.receivedRoutingKey + ", deliveryTag=" + this.deliveryTag + ", messageCount=" + this.messageCount + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.appId == null) ? 0 : this.appId.hashCode());
		result = prime * result + ((this.clusterId == null) ? 0 : this.clusterId.hashCode());
		result = prime * result + ((this.contentEncoding == null) ? 0 : this.contentEncoding.hashCode());
		result = prime * result + (int) (this.contentLength ^ (this.contentLength >>> 32));
		result = prime * result + ((this.contentType == null) ? 0 : this.contentType.hashCode());
		result = prime * result + Arrays.hashCode(this.correlationId);
		result = prime * result + ((this.deliveryMode == null) ? 0 : this.deliveryMode.hashCode());
		result = prime * result + (int) (this.deliveryTag ^ (this.deliveryTag >>> 32));
		result = prime * result + ((this.expiration == null) ? 0 : this.expiration.hashCode());
		result = prime * result + ((this.headers == null) ? 0 : this.headers.hashCode());
		result = prime * result + ((this.messageCount == null) ? 0 : this.messageCount.hashCode());
		result = prime * result + ((this.messageId == null) ? 0 : this.messageId.hashCode());
		result = prime * result + ((this.priority == null) ? 0 : this.priority.hashCode());
		result = prime * result + ((this.receivedExchange == null) ? 0 : this.receivedExchange.hashCode());
		result = prime * result + ((this.receivedRoutingKey == null) ? 0 : this.receivedRoutingKey.hashCode());
		result = prime * result + ((this.redelivered == null) ? 0 : this.redelivered.hashCode());
		result = prime * result + ((this.replyTo == null) ? 0 : this.replyTo.hashCode());
		result = prime * result + ((this.timestamp == null) ? 0 : this.timestamp.hashCode());
		result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
		result = prime * result + ((this.userId == null) ? 0 : this.userId.hashCode());
		return result;
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
		MessageProperties other = (MessageProperties) obj;
		if (this.appId == null) {
			if (other.appId != null) {
				return false;
			}
		}
		else if (!this.appId.equals(other.appId)) {
			return false;
		}
		if (this.clusterId == null) {
			if (other.clusterId != null) {
				return false;
			}
		}
		else if (!this.clusterId.equals(other.clusterId)) {
			return false;
		}
		if (this.contentEncoding == null) {
			if (other.contentEncoding != null) {
				return false;
			}
		}
		else if (!this.contentEncoding.equals(other.contentEncoding)) {
			return false;
		}
		if (this.contentLength != other.contentLength) {
			return false;
		}
		if (this.contentType == null) {
			if (other.contentType != null) {
				return false;
			}
		}
		else if (!this.contentType.equals(other.contentType)) {
			return false;
		}
		if (!Arrays.equals(this.correlationId, other.correlationId)) {
			return false;
		}
		if (this.deliveryMode != other.deliveryMode) {
			return false;
		}
		if (this.deliveryTag != other.deliveryTag) {
			return false;
		}
		if (this.expiration == null) {
			if (other.expiration != null) {
				return false;
			}
		}
		else if (!this.expiration.equals(other.expiration)) {
			return false;
		}
		if (this.headers == null) {
			if (other.headers != null) {
				return false;
			}
		}
		else if (!this.headers.equals(other.headers)) {
			return false;
		}
		if (this.messageCount == null) {
			if (other.messageCount != null) {
				return false;
			}
		}
		else if (!this.messageCount.equals(other.messageCount)) {
			return false;
		}
		if (this.messageId == null) {
			if (other.messageId != null) {
				return false;
			}
		}
		else if (!this.messageId.equals(other.messageId)) {
			return false;
		}
		if (this.priority == null) {
			if (other.priority != null) {
				return false;
			}
		}
		else if (!this.priority.equals(other.priority)) {
			return false;
		}
		if (this.receivedExchange == null) {
			if (other.receivedExchange != null) {
				return false;
			}
		}
		else if (!this.receivedExchange.equals(other.receivedExchange)) {
			return false;
		}
		if (this.receivedRoutingKey == null) {
			if (other.receivedRoutingKey != null) {
				return false;
			}
		}
		else if (!this.receivedRoutingKey.equals(other.receivedRoutingKey)) {
			return false;
		}
		if (this.redelivered == null) {
			if (other.redelivered != null) {
				return false;
			}
		}
		else if (!this.redelivered.equals(other.redelivered)) {
			return false;
		}
		if (this.replyTo == null) {
			if (other.replyTo != null) {
				return false;
			}
		}
		else if (!this.replyTo.equals(other.replyTo)) {
			return false;
		}
		if (this.timestamp == null) {
			if (other.timestamp != null) {
				return false;
			}
		}
		else if (!this.timestamp.equals(other.timestamp)) {
			return false;
		}
		if (this.type == null) {
			if (other.type != null) {
				return false;
			}
		}
		else if (!this.type.equals(other.type)) {
			return false;
		}
		if (this.userId == null) {
			if (other.userId != null) {
				return false;
			}
		}
		else if (!this.userId.equals(other.userId)) {
			return false;
		}
		return true;
	}

}