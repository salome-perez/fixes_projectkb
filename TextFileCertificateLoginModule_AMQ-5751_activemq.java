public class TextFileCertificateLoginModule {
    @Override
    protected String getUserNameForCertificates(final X509Certificate[] certs) throws LoginException {
        if (certs == null) {
            throw new LoginException("Client certificates not found. Cannot authenticate.");
        }

        File usersFile = new File(baseDir, usersFilePathname);

        Properties users = new Properties();

        try(java.io.FileInputStream in = new java.io.FileInputStream(usersFile)) {
            users.load(in);
        } catch (IOException ioe) {
            throw new LoginException("Unable to load user properties file " + usersFile);
        }

        String dn = getDistinguishedName(certs);

        Enumeration<Object> keys = users.keys();
        for (Enumeration<Object> vals = users.elements(); vals.hasMoreElements();) {
            if (((String)vals.nextElement()).equals(dn)) {
                return (String)keys.nextElement();
            } else {
                keys.nextElement();
            }
        }

        return null;
    }

}