public class KDiag {
  @Override
  public int run(String[] argv) throws Exception {
    List<String> args = new LinkedList<>(Arrays.asList(argv));
    String keytabName = popOptionWithArgument(ARG_KEYTAB, args);
    if (keytabName != null) {
      keytab = new File(keytabName);
    }
    principal = popOptionWithArgument(ARG_PRINCIPAL, args);
    String outf = popOptionWithArgument(ARG_OUTPUT, args);
    String mkl = popOptionWithArgument(ARG_KEYLEN, args);
    if (mkl != null) {
      minKeyLength = Integer.parseInt(mkl);
    }
    securityRequired = popOption(ARG_SECURE, args);
    nofail = popOption(ARG_NOFAIL, args);
    jaas = popOption(ARG_JAAS, args);
    nologin = popOption(ARG_NOLOGIN, args);
    checkShortName = popOption(ARG_VERIFYSHORTNAME, args);

    // look for list of resources
    String resource;
    while (null != (resource = popOptionWithArgument(ARG_RESOURCE, args))) {
      // loading a resource
      LOG.info("Loading resource {}", resource);
      try (InputStream in =
               getClass().getClassLoader().getResourceAsStream(resource)) {
        if (verify(in != null, CAT_CONFIG, "No resource %s", resource)) {
          Configuration.addDefaultResource(resource);
        }
      }
    }
    // look for any leftovers
    if (!args.isEmpty()) {
      println("Unknown arguments in command:");
      for (String s : args) {
        println("  \"%s\"", s);
      }
      println();
      println(usage());
      return -1;
    }
    if (outf != null) {
      println("Printing output to %s", outf);
      out = new PrintWriter(new File(outf), "UTF-8");
    }
    execute();
    return probeHasFailed ? KDIAG_FAILURE : 0;
  }

  private String usage() {
    return "KDiag: Diagnose Kerberos Problems\n"
      + arg("-D", "key=value", "Define a configuration option")
      + arg(ARG_JAAS, "",
      "Require a JAAS file to be defined in " + SUN_SECURITY_JAAS_FILE)
      + arg(ARG_KEYLEN, "<keylen>",
      "Require a minimum size for encryption keys supported by the JVM."
      + " Default value : "+ minKeyLength)
      + arg(ARG_KEYTAB, "<keytab> " + ARG_PRINCIPAL + " <principal>",
          "Login from a keytab as a specific principal")
      + arg(ARG_NOFAIL, "", "Do not fail on the first problem")
      + arg(ARG_NOLOGIN, "", "Do not attempt to log in")
      + arg(ARG_OUTPUT, "<file>", "Write output to a file")
      + arg(ARG_RESOURCE, "<resource>", "Load an XML configuration resource")
      + arg(ARG_SECURE, "", "Require the hadoop configuration to be secure")
      + arg(ARG_VERIFYSHORTNAME, ARG_PRINCIPAL + " <principal>",
      "Verify the short name of the specific principal does not contain '@' or '/'");
  }

  @SuppressWarnings("deprecation")
  public boolean execute() throws Exception {

    title("Kerberos Diagnostics scan at %s",
        new Date(System.currentTimeMillis()));

    // check that the machine has a name
    println("Hostname = %s",
        InetAddress.getLocalHost().getCanonicalHostName());

    println("%s = %d", ARG_KEYLEN, minKeyLength);
    println("%s = %s", ARG_KEYTAB, keytab);
    println("%s = %s", ARG_PRINCIPAL, principal);
    println("%s = %s", ARG_VERIFYSHORTNAME, checkShortName);

    // Fail fast on a JVM without JCE installed.
    validateKeyLength();

    // look at realm
    println("JVM Kerberos Login Module = %s", getKrb5LoginModuleName());

    title("Core System Properties");
    for (String prop : new String[]{
      "user.name",
      "java.version",
      "java.vendor",
      JAVA_SECURITY_KRB5_CONF,
      JAVA_SECURITY_KRB5_REALM,
      JAVA_SECURITY_KRB5_KDC_ADDRESS,
      SUN_SECURITY_KRB5_DEBUG,
      SUN_SECURITY_SPNEGO_DEBUG,
      SUN_SECURITY_JAAS_FILE
    }) {
      printSysprop(prop);
    }
    endln();

    title("All System Properties");
    ArrayList<String> propList = new ArrayList<>(
        System.getProperties().stringPropertyNames());
    Collections.sort(propList, String.CASE_INSENSITIVE_ORDER);
    for (String s : propList) {
      printSysprop(s);
    }
    endln();

    title("Environment Variables");
    for (String env : new String[]{
      HADOOP_JAAS_DEBUG,
      KRB5_CCNAME,
      HADOOP_USER_NAME,
      HADOOP_PROXY_USER,
      HADOOP_TOKEN_FILE_LOCATION,
      "HADOOP_SECURE_LOG",
      "HADOOP_OPTS",
      "HADOOP_CLIENT_OPTS",
    }) {
      printEnv(env);
    }
    endln();

    title("Configuration Options");
    for (String prop : new String[]{
      KERBEROS_KINIT_COMMAND,
      HADOOP_SECURITY_AUTHENTICATION,
      HADOOP_SECURITY_AUTHORIZATION,
      "hadoop.kerberos.min.seconds.before.relogin",    // not in 2.6
      "hadoop.security.dns.interface",   // not in 2.6
      "hadoop.security.dns.nameserver",  // not in 2.6
      HADOOP_RPC_PROTECTION,
      HADOOP_SECURITY_SASL_PROPS_RESOLVER_CLASS,
      HADOOP_SECURITY_CRYPTO_CODEC_CLASSES_KEY_PREFIX,
      HADOOP_SECURITY_GROUP_MAPPING,
      "hadoop.security.impersonation.provider.class",    // not in 2.6
      DFS_DATA_TRANSFER_PROTECTION, // HDFS
      DFS_DATA_TRANSFER_SASLPROPERTIES_RESOLVER_CLASS // HDFS
    }) {
      printConfOpt(prop);
    }

    // check that authentication is enabled
    Configuration conf = getConf();
    if (isSimpleAuthentication(conf)) {
      println(HADOOP_AUTHENTICATION_IS_DISABLED);
      failif(securityRequired, CAT_CONFIG, HADOOP_AUTHENTICATION_IS_DISABLED);
      // no security, warn
      LOG.warn("Security is not enabled for the Hadoop cluster");
    } else {
      if (isSimpleAuthentication(new Configuration())) {
        LOG.warn("The default cluster security is insecure");
        failif(securityRequired, CAT_CONFIG, HADOOP_AUTHENTICATION_IS_DISABLED);
      }
    }


    // now the big test: login, then try again
    boolean krb5Debug = getAndSet(SUN_SECURITY_KRB5_DEBUG);
    boolean spnegoDebug = getAndSet(SUN_SECURITY_SPNEGO_DEBUG);

    try {
      UserGroupInformation.setConfiguration(conf);
      validateKrb5File();
      printDefaultRealm();
      validateSasl(HADOOP_SECURITY_SASL_PROPS_RESOLVER_CLASS);
      if (conf.get(DFS_DATA_TRANSFER_SASLPROPERTIES_RESOLVER_CLASS) != null) {
        validateSasl(DFS_DATA_TRANSFER_SASLPROPERTIES_RESOLVER_CLASS);
      }
      validateKinitExecutable();
      validateJAAS(jaas);
      validateNTPConf();

      if (checkShortName) {
        validateShortName();
      }

      if (!nologin) {
        title("Logging in");
        if (keytab != null) {
          dumpKeytab(keytab);
          loginFromKeytab();
        } else {
          UserGroupInformation loginUser = getLoginUser();
          dumpUGI("Log in user", loginUser);
          validateUGI("Login user", loginUser);
          println("Ticket based login: %b", isLoginTicketBased());
          println("Keytab based login: %b", isLoginKeytabBased());
        }
      }

      return true;
    } finally {
      // restore original system properties
      System.setProperty(SUN_SECURITY_KRB5_DEBUG,
        Boolean.toString(krb5Debug));
      System.setProperty(SUN_SECURITY_SPNEGO_DEBUG,
        Boolean.toString(spnegoDebug));
    }
  }

}