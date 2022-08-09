public class DiskBenchmark {
    public static void main(String[] args) {

        DiskBenchmark benchmark = new DiskBenchmark();
        args = CommandLineSupport.setOptions(benchmark, args);
        ArrayList<String> files = new ArrayList<String>();
        if (args.length == 0) {
            files.add("disk-benchmark.dat");
        } else {
            files.addAll(Arrays.asList(args));
        }

        for (String f : files) {
            try {
                File file = new File(f);
                if (file.exists()) {
                    System.out.println("File " + file + " already exists, will not benchmark.");
                } else {
                    System.out.println("Benchmarking: " + file.getCanonicalPath());
                    Report report = benchmark.benchmark(file);
                    file.delete();
                    System.out.println(report.toString());
                }
            } catch (Throwable e) {
                if (benchmark.verbose) {
                    System.out.println("ERROR:");
                    e.printStackTrace(System.out);
                } else {
                    System.out.println("ERROR: " + e);
                }
            }
        }

    }

    private void preallocateDataFile(RecoverableRandomAccessFile raf, File location) throws Exception {
        File tmpFile;
        if (location != null && location.isDirectory()) {
            tmpFile = new File(location, "template.dat");
        }else {
            tmpFile = new File("template.dat");
        }
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        RandomAccessFile templateFile = new RandomAccessFile(tmpFile, "rw");
        templateFile.setLength(size);
        templateFile.getChannel().force(true);
        templateFile.getChannel().transferTo(0, size, raf.getChannel());
        templateFile.close();
        tmpFile.delete();
    }

    public Report benchmark(File file) throws Exception {
        Report rc = new Report();

        // Initialize the block we will be writing to disk.
        byte[] data = new byte[bs];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ('a' + (i % 26));
        }

        rc.size = data.length;
        RecoverableRandomAccessFile raf = new RecoverableRandomAccessFile(file, "rw");
        preallocateDataFile(raf, file.getParentFile());

        // Figure out how many writes we can do in the sample interval.
        long start = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        int ioCount = 0;
        while (true) {
            if ((now - start) > sampleInterval) {
                break;
            }
            raf.seek(0);
            for (long i = 0; i + data.length < size; i += data.length) {
                raf.write(data);
                ioCount++;
                now = System.currentTimeMillis();
                if ((now - start) > sampleInterval) {
                    break;
                }
            }
            // Sync to disk so that the we actually write the data to disk..
            // otherwise OS buffering might not really do the write.
            raf.getChannel().force(!SKIP_METADATA_UPDATE);
        }
        raf.getChannel().force(!SKIP_METADATA_UPDATE);
        raf.close();
        now = System.currentTimeMillis();

        rc.size = data.length;
        rc.writes = ioCount;
        rc.writeDuration = (now - start);

        raf = new RecoverableRandomAccessFile(file, "rw");
        start = System.currentTimeMillis();
        now = System.currentTimeMillis();
        ioCount = 0;
        while (true) {
            if ((now - start) > sampleInterval) {
                break;
            }
            for (long i = 0; i + data.length < size; i += data.length) {
                raf.seek(i);
                raf.write(data);
                raf.getChannel().force(!SKIP_METADATA_UPDATE);
                ioCount++;
                now = System.currentTimeMillis();
                if ((now - start) > sampleInterval) {
                    break;
                }
            }
        }
        raf.close();
        now = System.currentTimeMillis();
        rc.syncWrites = ioCount;
        rc.syncWriteDuration = (now - start);

        raf = new RecoverableRandomAccessFile(file, "rw");
        start = System.currentTimeMillis();
        now = System.currentTimeMillis();
        ioCount = 0;
        while (true) {
            if ((now - start) > sampleInterval) {
                break;
            }
            raf.seek(0);
            for (long i = 0; i + data.length < size; i += data.length) {
                raf.seek(i);
                raf.readFully(data);
                ioCount++;
                now = System.currentTimeMillis();
                if ((now - start) > sampleInterval) {
                    break;
                }
            }
        }
        raf.close();

        rc.reads = ioCount;
        rc.readDuration = (now - start);
        return rc;
    }

}