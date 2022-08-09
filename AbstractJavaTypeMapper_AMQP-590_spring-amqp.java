public class AbstractJavaTypeMapper {
    protected void addHeader(MessageProperties properties, String headerName,
                           Class<?> clazz) {
        if (this.classIdMapping.containsKey(clazz)) {
            properties.getHeaders().put(headerName, this.classIdMapping.get(clazz));
        }
        else {
            properties.getHeaders().put(headerName, clazz.getName());
        }
    }

    private void validateIdTypeMapping() {
        Map<String, Class<?>> finalIdClassMapping = new HashMap<String, Class<?>>();
        for (Map.Entry<String, Class<?>> entry : this.idClassMapping.entrySet()) {
            String id = entry.getKey();
            Class<?> clazz = entry.getValue();
            finalIdClassMapping.put(id, clazz);
            this.classIdMapping.put(clazz, id);
        }
        this.idClassMapping = finalIdClassMapping;
    }

    public Map<String, Class<?>> getIdClassMapping() {
        return Collections.unmodifiableMap(this.idClassMapping);
    }

}