public class TransportFormat {
		static Object readFromXml(InputStream bufferedInput) throws IOException {
			final XStream xstream = createXStream(false);
			// see http://x-stream.github.io/security.html
			// clear out existing permissions and set own ones
			xstream.addPermission(NoTypePermission.NONE);
			// allow some basics
			xstream.addPermission(NullPermission.NULL);
			xstream.addPermission(PrimitiveTypePermission.PRIMITIVES);
			xstream.allowTypesByWildcard(
					new String[] { "java.lang.*", "java.util.*", "java.util.concurrent.*" });
			// allow any type from the same package
			xstream.allowTypesByWildcard(new String[] { PACKAGE_NAME + ".*" });
			final InputStreamReader reader = new InputStreamReader(bufferedInput, XML_CHARSET_NAME);
			try {
				return xstream.fromXML(reader);
			} finally {
				reader.close();
			}
		}

}