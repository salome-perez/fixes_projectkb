public class AjaxServlet {
    protected void doJavaScript(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        // Look for a local resource first.
        String js = request.getServletPath() + request.getPathInfo();
        URL url = getServletContext().getResource(js);
        if (url != null) {
            getServletContext().getNamedDispatcher("default").forward(request, response);
            return;
        }

        // Serve from the classpath resources
        String resource = "org/apache/activemq/web" + request.getPathInfo();
        synchronized (jsCache) {

            byte[] data = jsCache.get(resource);
            if (data == null) {
                try(InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                    if (in != null) {
                        try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                            byte[] buf = new byte[4096];
                            int len = in.read(buf);
                            while (len >= 0) {
                                out.write(buf, 0, len);
                                len = in.read(buf);
                            }
                            data = out.toByteArray();
                            jsCache.put(resource, data);
                        }
                    }
                }
            }

            if (data != null) {

                long ifModified = request.getDateHeader("If-Modified-Since");

                if (ifModified == jsLastModified) {
                    response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
                } else {
                    response.setContentType("application/x-javascript");
                    response.setContentLength(data.length);
                    response.setDateHeader("Last-Modified", jsLastModified);
                    response.getOutputStream().write(data);
                }
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

}