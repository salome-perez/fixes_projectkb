public class FileSystemBlobStrategy {
    protected void createRootFolder() throws MalformedURLException, URISyntaxException {
        rootFile = new File(new URL(policy.getUploadUrl()).toURI());
        if (rootFile.exists() == false) {
            rootFile.mkdirs();
        } else if (rootFile.isDirectory() == false) {
            throw new IllegalArgumentException("Given url is not a directory " + rootFile );
        }
    }

    public URL uploadStream(ActiveMQBlobMessage message, InputStream in) throws JMSException, IOException {
        File f = getFile(message);
        try(FileOutputStream out = new FileOutputStream(f)) {
            byte[] buffer = new byte[policy.getBufferSize()];
            for (int c = in.read(buffer); c != -1; c = in.read(buffer)) {
                out.write(buffer, 0, c);
                out.flush();
            }
        }
        // File.toURL() is deprecated
        return f.toURI().toURL();
    }

    protected File getFile(ActiveMQBlobMessage message) throws JMSException, IOException {
    	if (message.getURL() != null) {
    		try {
				return new File(message.getURL().toURI());
			} catch (URISyntaxException e) {
                                IOException ioe = new IOException("Unable to open file for message " + message);
                                ioe.initCause(e);
			}
    	}
        //replace all : with _ to make windows more happy
        String fileName = message.getJMSMessageID().replaceAll(":", "_");
        return new File(rootFile, fileName);

    }

    public URL uploadFile(ActiveMQBlobMessage message, File file) throws JMSException, IOException {
        try(FileInputStream fis = new FileInputStream(file)) {
            return uploadStream(message, fis);
        }
    }

}