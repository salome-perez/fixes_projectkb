public class XmlFilePerfReportWriter {
    protected void writeXmlLogFile() throws IOException {
        // Write throughput data
        xmlFileWriter.println("<property name='performanceData'>");
        xmlFileWriter.println("<list>");

        try (FileInputStream fileInputStream = new FileInputStream(tempLogFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[TP-DATA]")) {
                    handleCsvData(REPORT_PLUGIN_THROUGHPUT, line.substring("[TP-DATA]".length()));
                    parsePerfCsvData("tpdata", line.substring("[TP-DATA]".length()));
                } else if (line.startsWith("[CPU-DATA]")) {
                    handleCsvData(REPORT_PLUGIN_CPU, line.substring("[CPU-DATA]".length()));
                    parsePerfCsvData("cpudata", line.substring("[CPU-DATA]".length()));
                } else if (line.startsWith("[INFO]")) {
                    xmlFileWriter.println("<info>" + line + "</info>");
                } else {
                    xmlFileWriter.println("<error>" + line + "</error>");
                }
            }
        }
        xmlFileWriter.println("</list>");
        xmlFileWriter.println("</property>");
    }

}