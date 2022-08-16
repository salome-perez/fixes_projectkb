public class URIBuilder {
    private static String normalizePath(final String path) {
        String s = path;
        if (s == null) {
            return "/";
        }
        int n = 0;
        for (; n < s.length(); n++) {
            if (s.charAt(n) != '/') {
                break;
            }
        }
        if (n > 1) {
            s = s.substring(n - 1);
        }
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        return s;
    }

}