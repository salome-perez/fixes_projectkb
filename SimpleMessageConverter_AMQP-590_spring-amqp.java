public class SimpleMessageConverter {
	protected ObjectInputStream createObjectInputStream(InputStream is, String codebaseUrl) throws IOException {
		return new CodebaseAwareObjectInputStream(is, this.beanClassLoader, codebaseUrl) {

			@Override
			protected Class<?> resolveClass(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
				Class<?> clazz = super.resolveClass(classDesc);
				checkWhiteList(clazz);
				return clazz;
			}

		};
	}

}