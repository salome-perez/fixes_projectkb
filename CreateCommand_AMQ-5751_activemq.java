public class CreateCommand {
    private void writeFile(String typeName, File dest) throws IOException {
        String data;
        if (typeName.equals("winActivemq")) {
            data = winActivemqData;
            data = resolveParam("[$][{]activemq.home[}]", amqHome.getCanonicalPath().replaceAll("[\\\\]", "/"), data);
            data = resolveParam("[$][{]activemq.base[}]", targetAmqBase.getCanonicalPath().replaceAll("[\\\\]", "/"), data);
        } else if (typeName.equals("unixActivemq")) {
            data = getUnixActivemqData();
            data = resolveParam("[$][{]activemq.home[}]", amqHome.getCanonicalPath().replaceAll("[\\\\]", "/"), data);
            data = resolveParam("[$][{]activemq.base[}]", targetAmqBase.getCanonicalPath().replaceAll("[\\\\]", "/"), data);
        } else {
            throw new IllegalStateException("Unknown file type: " + typeName);
        }

        ByteBuffer buf = ByteBuffer.allocate(data.length());
        buf.put(data.getBytes());
        buf.flip();

        try(FileOutputStream fos = new FileOutputStream(dest);
            FileChannel destinationChannel = fos.getChannel()) {
            destinationChannel.write(buf);
        }

        // Set file permissions available for Java 6.0 only
        dest.setExecutable(true);
        dest.setReadable(true);
        dest.setWritable(true);
    }

    private void copyFile(File from, File dest) throws IOException {
        if (!from.exists()) {
            return;
        }

        try(FileInputStream fis = new FileInputStream(from);
            FileChannel sourceChannel = fis.getChannel();
            FileOutputStream fos = new FileOutputStream(dest);
            FileChannel destinationChannel = fos.getChannel()) {
            sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
        }
    }

}