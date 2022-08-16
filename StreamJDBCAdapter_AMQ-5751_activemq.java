public class StreamJDBCAdapter {
    protected byte[] getBinaryData(ResultSet rs, int index) throws SQLException {

        try (InputStream is = rs.getBinaryStream(index);
             ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 4)) {

            int ch;
            while ((ch = is.read()) >= 0) {
                os.write(ch);
            }

            return os.toByteArray();
        } catch (IOException e) {
            throw (SQLException)new SQLException("Error reading binary parameter: " + index).initCause(e);
        }
    }

}