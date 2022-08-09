public class AbstractJmsClientSystem {
    protected static Properties parseStringArgs(String[] args) {
        File configFile = null;
        Properties props = new Properties();

        if (args == null || args.length == 0) {
            return props; // Empty properties
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-D") || arg.startsWith("-d")) {
                arg = arg.substring(2);
            }
            int index = arg.indexOf("=");
            String key = arg.substring(0, index);
            String val = arg.substring(index + 1);

            if (key.equalsIgnoreCase("sysTest.propsConfigFile")) {
                if (!val.endsWith(".properties")) {
                    val += ".properties";
                }
                configFile = new File(val);
            }
            props.setProperty(key, val);
        }

        Properties fileProps = new Properties();
        try {
            if (configFile != null) {
                try(FileInputStream inputStream = new FileInputStream(configFile)) {
                    LOG.info("Loading properties file: " + configFile.getAbsolutePath());
                    fileProps.load(inputStream);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Overwrite file settings with command line settings
        fileProps.putAll(props);
        return fileProps;
    }

}