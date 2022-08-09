public class ActiveMQSslConnectionFactory {
    protected KeyManager[] createKeyManager() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(getKeyStoreType());
        KeyManager[] keystoreManagers = null;
        if (keyStore != null) {
            byte[] sslCert = loadClientCredential(keyStore);

            if (sslCert != null && sslCert.length > 0) {
                try(ByteArrayInputStream bin = new ByteArrayInputStream(sslCert)) {
                    ks.load(bin, keyStorePassword.toCharArray());
                    kmf.init(ks, keyStoreKeyPassword !=null ? keyStoreKeyPassword.toCharArray() : keyStorePassword.toCharArray());
                    keystoreManagers = kmf.getKeyManagers();
                }
            }
        }
        return keystoreManagers;
    }

    protected TrustManager[] createTrustManager() throws Exception {
        TrustManager[] trustStoreManagers = null;
        KeyStore trustedCertStore = KeyStore.getInstance(getTrustStoreType());

        if (trustStore != null) {
            try(InputStream tsStream = getInputStream(trustStore)) {

                trustedCertStore.load(tsStream, trustStorePassword.toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

                tmf.init(trustedCertStore);
                trustStoreManagers = tmf.getTrustManagers();
            }
        }
        return trustStoreManagers;
    }

    protected byte[] loadClientCredential(String fileName) throws IOException {
        if (fileName == null) {
            return null;
        }
        try(InputStream in = getInputStream(fileName);
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[512];
            int i = in.read(buf);
            while (i > 0) {
                out.write(buf, 0, i);
                i = in.read(buf);
            }
            return out.toByteArray();
        }
    }

}