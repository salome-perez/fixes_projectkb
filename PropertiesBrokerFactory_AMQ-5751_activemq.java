public class PropertiesBrokerFactory {
    protected BrokerService createBrokerService(URI brokerURI, Map<Object, Object> properties) {
        return new BrokerService();
    }

    protected Map<Object, Object> loadProperties(URI brokerURI) throws IOException {
        // lets load a URI
        String remaining = brokerURI.getSchemeSpecificPart();
        Properties properties = new Properties();
        File file = new File(remaining);

        try (InputStream inputStream = loadStream(file, remaining)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        }

        // should we append any system properties?
        try {
            Properties systemProperties = System.getProperties();
            properties.putAll(systemProperties);
        } catch (Exception e) {
            // ignore security exception
        }
        return properties;
    }

    public BrokerService createBroker(URI brokerURI) throws Exception {

        Map<Object, Object> properties = loadProperties(brokerURI);
        BrokerService brokerService = createBrokerService(brokerURI, properties);

        IntrospectionSupport.setProperties(brokerService, properties);
        return brokerService;
    }

    protected InputStream loadStream(File file, String remaining) throws IOException {
        InputStream inputStream = null;
        if (file != null && file.exists()) {
            inputStream = new FileInputStream(file);
        } else {
            URL url = null;
            try {
                url = new URL(remaining);
            } catch (MalformedURLException e) {
                // lets now see if we can find the name on the classpath
                inputStream = findResourceOnClassPath(remaining);
                if (inputStream == null) {
                    throw new IOException("File does not exist: " + remaining + ", could not be found on the classpath and is not a valid URL: " + e);
                }
            }
            if (inputStream == null && url != null) {
                inputStream = url.openStream();
            }
        }
        return inputStream;
    }

}