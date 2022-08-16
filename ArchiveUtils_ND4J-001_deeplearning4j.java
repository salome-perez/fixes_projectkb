public class ArchiveUtils {
    public static void unzipFileTo(String file, String dest) throws IOException {
        File target = new File(file);
        if (!target.exists())
            throw new IllegalArgumentException("Archive doesnt exist");
        FileInputStream fin = new FileInputStream(target);
        int BUFFER = 2048;
        byte data[] = new byte[BUFFER];

        if (file.endsWith(".zip") || file.endsWith(".jar")) {
            try(ZipInputStream zis = new ZipInputStream(fin)) {
                //get the zipped file list entry
                ZipEntry ze = zis.getNextEntry();

                while (ze != null) {
                    String fileName = ze.getName();

                    String canonicalDestinationDirPath = new File(dest).getCanonicalPath();
                    File newFile = new File(dest + File.separator + fileName);
                    String canonicalDestinationFile = newFile.getCanonicalPath();

                    if (!canonicalDestinationFile.startsWith(canonicalDestinationDirPath + File.separator)) {
                        log.debug("Attempt to unzip entry is outside of the target dir");
                        throw new IOException("Entry is outside of the target dir: ");
                    }

                    if (ze.isDirectory()) {
                        newFile.mkdirs();
                        zis.closeEntry();
                        ze = zis.getNextEntry();
                        continue;
                    }

                    FileOutputStream fos = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(data)) > 0) {
                        fos.write(data, 0, len);
                    }

                    fos.close();
                    ze = zis.getNextEntry();
                    log.debug("File extracted: " + newFile.getAbsoluteFile());
                }

                zis.closeEntry();
            }
        } else if (file.endsWith(".tar.gz") || file.endsWith(".tgz")) {

            BufferedInputStream in = new BufferedInputStream(fin);
            GzipCompressorInputStream gzIn = new GzipCompressorInputStream(in);
            TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn);

            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
                log.info("Extracting: " + entry.getName());

                if (entry.isDirectory()) {
                    File f = new File(dest + File.separator + entry.getName());
                    f.mkdirs();
                }
                else {
                    int count;
                    try(FileOutputStream fos = new FileOutputStream(dest + File.separator + entry.getName());
                        BufferedOutputStream destStream = new BufferedOutputStream(fos, BUFFER);) {
                        while ((count = tarIn.read(data, 0, BUFFER)) != -1) {
                            destStream.write(data, 0, count);
                        }

                        destStream.flush();
                        IOUtils.closeQuietly(destStream);
                    }
                }
            }

            // Close the input stream
            tarIn.close();
        } else if (file.endsWith(".gz")) {
            File extracted = new File(target.getParent(), target.getName().replace(".gz", ""));
            if (extracted.exists())
                extracted.delete();
            extracted.createNewFile();
            try(GZIPInputStream is2 = new GZIPInputStream(fin); OutputStream fos = FileUtils.openOutputStream(extracted)) {
                IOUtils.copyLarge(is2, fos);
                fos.flush();
            }
        } else {
            throw new IllegalStateException("Unable to infer file type (compression format) from source file name: " +
                    file);
        }
        target.delete();
    }

}