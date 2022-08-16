public class FTPBlobUploadStrategy {
    public URL uploadFile(ActiveMQBlobMessage message, File file) throws JMSException, IOException {
        try(FileInputStream fis = new FileInputStream(file)) {
            return uploadStream(message, fis);
        }
    }

}