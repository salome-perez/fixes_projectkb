public class PendingConfirm {
	public String getCause() {
		return this.cause;
	}

	@Override
	public String toString() {
		return "PendingConfirm [correlationData=" + this.correlationData + (this.cause == null ? "" : " cause=" + this.cause) + "]";
	}

	public CorrelationData getCorrelationData() {
		return this.correlationData;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

}