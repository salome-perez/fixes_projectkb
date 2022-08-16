public class IOHelper {
    public static void copyInputStream(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int len = in.read(buffer);
            while (len >= 0) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
        } finally {
            in.close();
            out.close();
        }
    }

}