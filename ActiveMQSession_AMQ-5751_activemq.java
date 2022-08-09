public class ActiveMQSession {
    public BlobMessage createBlobMessage(InputStream in) throws JMSException {
        ActiveMQBlobMessage message = new ActiveMQBlobMessage();
        configureMessage(message);
        message.setBlobUploader(new BlobUploader(getBlobTransferPolicy(), in));
        message.setBlobDownloader(new BlobDownloader(getBlobTransferPolicy()));
        message.setDeletedByBroker(true);
        return message;
    }

}