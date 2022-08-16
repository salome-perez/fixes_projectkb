public class BlobJDBCAdapter {
    @Override
    public byte[] doGetMessage(TransactionContext c, MessageId id) throws SQLException, IOException {
        PreparedStatement s = null;
        ResultSet rs = null;
        cleanupExclusiveLock.readLock().lock();
        try {

            s = c.getConnection().prepareStatement(statements.getFindMessageStatement());
            s.setString(1, id.getProducerId().toString());
            s.setLong(2, id.getProducerSequenceId());
            rs = s.executeQuery();

            if (!rs.next()) {
                return null;
            }
            Blob blob = rs.getBlob(1);

            try(InputStream is = blob.getBinaryStream();
                ByteArrayOutputStream os = new ByteArrayOutputStream((int)blob.length())) {
                int ch;
                while ((ch = is.read()) >= 0) {
                    os.write(ch);
                }
                return os.toByteArray();
            }
        } finally {
            cleanupExclusiveLock.readLock().unlock();
            close(rs);
            close(s);
        }
    }

}