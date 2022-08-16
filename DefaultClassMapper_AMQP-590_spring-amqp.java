public class DefaultClassMapper {
	@Override
	public void fromClass(Class<?> clazz, MessageProperties properties) {
		properties.getHeaders().put(getClassIdFieldName(), fromClass(clazz));
	}

	@Override
	public Class<?> toClass(MessageProperties properties) {
		Map<String, Object> headers = properties.getHeaders();
		Object classIdFieldNameValue = headers.get(getClassIdFieldName());
		String classId = null;
		if (classIdFieldNameValue != null) {
			classId = classIdFieldNameValue.toString();
		}
		if (classId == null) {
			if (this.defaultType != null) {
				return this.defaultType;
			}
			else {
				throw new MessageConversionException(
						"failed to convert Message content. Could not resolve "
								+ getClassIdFieldName() + " in header " +
								"and no defaultType provided");
			}
		}
		return toClass(classId);
	}

	private void validateIdTypeMapping() {
		Map<String, Class<?>> finalIdClassMapping = new HashMap<String, Class<?>>();
		for (Entry<String, Class<?>> entry : this.idClassMapping.entrySet()) {
			String id = entry.getKey();
			Class<?> clazz = entry.getValue();
			finalIdClassMapping.put(id, clazz);
			this.classIdMapping.put(clazz, id);
		}
		this.idClassMapping = finalIdClassMapping;
	}

}