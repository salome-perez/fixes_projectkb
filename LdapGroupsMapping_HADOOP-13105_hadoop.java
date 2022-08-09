public class LdapGroupsMapping {
  DirContext getDirContext() throws NamingException {
    if (ctx == null) {
      // Set up the initial environment for LDAP connectivity
      Hashtable<String, String> env = new Hashtable<String, String>();
      env.put(Context.INITIAL_CONTEXT_FACTORY,
          com.sun.jndi.ldap.LdapCtxFactory.class.getName());
      env.put(Context.PROVIDER_URL, ldapUrl);
      env.put(Context.SECURITY_AUTHENTICATION, "simple");

      // Set up SSL security, if necessary
      if (useSsl) {
        env.put(Context.SECURITY_PROTOCOL, "ssl");
        System.setProperty("javax.net.ssl.keyStore", keystore);
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePass);
      }

      env.put(Context.SECURITY_PRINCIPAL, bindUser);
      env.put(Context.SECURITY_CREDENTIALS, bindPassword);

      env.put("com.sun.jndi.ldap.connect.timeout", conf.get(CONNECTION_TIMEOUT,
          String.valueOf(CONNECTION_TIMEOUT_DEFAULT)));
      env.put("com.sun.jndi.ldap.read.timeout", conf.get(READ_TIMEOUT,
          String.valueOf(READ_TIMEOUT_DEFAULT)));

      ctx = new InitialDirContext(env);
    }

    return ctx;
  }

}