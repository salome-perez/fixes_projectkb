public class PageFile {
        public byte[] getDiskBound() throws IOException {
            if (diskBound == null && diskBoundLocation != -1) {
                diskBound = new byte[length];
                try(RandomAccessFile file = new RandomAccessFile(tmpFile, "r")) {
                    file.seek(diskBoundLocation);
                    file.read(diskBound);
                }
                diskBoundLocation = -1;
            }
            return diskBound;
        }

}