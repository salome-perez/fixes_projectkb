public class DefaultBlobUploadStrategy {
    public URL uploadStream(ActiveMQBlobMessage message, InputStream fis) throws JMSException, IOException {
        URL url = createMessageURL(message);

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);

        // use chunked mode or otherwise URLConnection loads everything into
        // memory
        // (chunked mode not supported before JRE 1.5)
        connection.setChunkedStreamingMode(transferPolicy.getBufferSize());

        try(OutputStream os = connection.getOutputStream()) {
            byte[] buf = new byte[transferPolicy.getBufferSize()];
            for (int c = fis.read(buf); c != -1; c = fis.read(buf)) {
                os.write(buf, 0, c);
                os.flush();
            }
        }

        if (!isSuccessfulCode(connection.getResponseCode())) {
            throw new IOException("PUT was not successful: " + connection.getResponseCode() + " "
                                  + connection.getResponseMessage());
        }

        return url;
    }

    public URL uploadFile(ActiveMQBlobMessage message, File file) throws JMSException, IOException {
        try(FileInputStream fis = new FileInputStream(file)) {
            return uploadStream(message, fis);
        }
    }

}