public class DeclarationExceptionEvent {
	@Override
	public String toString() {
		return "DeclarationExceptionEvent [declarable=" + this.declarable + ", throwable=" + this.throwable + ", source="
				+ getSource() + "]";
	}

	public Throwable getThrowable() {
		return this.throwable;
	}

	public Declarable getDeclarable() {
		return this.declarable;
	}

}