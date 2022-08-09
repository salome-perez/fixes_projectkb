public class ZipUtils {
  private static void unzipEntry(ZipEntry entry, ZipInputStream zipStream, File toDir) throws IOException {
    File to = new File(toDir, entry.getName());
    verifyInsideTargetDirectory(entry, to.toPath(), toDir.toPath());

    if (entry.isDirectory()) {
      throwExceptionIfDirectoryIsNotCreatable(to);
    } else {
      File parent = to.getParentFile();
      throwExceptionIfDirectoryIsNotCreatable(parent);
      copy(zipStream, to);
    }
  }

  public static File unzip(File zip, File toDir, Predicate<ZipEntry> filter) throws IOException {
    if (!toDir.exists()) {
      FileUtils.forceMkdir(toDir);
    }

    Path targetDirNormalizedPath = toDir.toPath().normalize();
    ZipFile zipFile = new ZipFile(zip);
    try {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (filter.test(entry)) {
          File target = new File(toDir, entry.getName());

          verifyInsideTargetDirectory(entry, target.toPath(), targetDirNormalizedPath);

          if (entry.isDirectory()) {
            throwExceptionIfDirectoryIsNotCreatable(target);
          } else {
            File parent = target.getParentFile();
            throwExceptionIfDirectoryIsNotCreatable(parent);
            copy(zipFile, entry, target);
          }
        }
      }
      return toDir;

    } finally {
      zipFile.close();
    }
  }

}