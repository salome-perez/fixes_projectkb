public class LdapGroupsMapping {
  List<String> doGetGroups(String user) throws NamingException {
    List<String> groups = new ArrayList<String>();

    DirContext ctx = getDirContext();

    // Search for the user. We'll only ever need to look at the first result
    NamingEnumeration<SearchResult> results = ctx.search(baseDN,
        userSearchFilter,
        new Object[]{user},
        SEARCH_CONTROLS);
    if (results.hasMoreElements()) {
      SearchResult result = results.nextElement();
      String userDn = result.getNameInNamespace();

      NamingEnumeration<SearchResult> groupResults = null;

      if (isPosix) {
        String gidNumber = null;
        String uidNumber = null;
        Attribute gidAttribute = result.getAttributes().get(posixGidAttr);
        Attribute uidAttribute = result.getAttributes().get(posixUidAttr);
        if (gidAttribute != null) {
          gidNumber = gidAttribute.get().toString();
        }
        if (uidAttribute != null) {
          uidNumber = uidAttribute.get().toString();
        }
        if (uidNumber != null && gidNumber != null) {
          groupResults =
              ctx.search(baseDN,
                  "(&"+ groupSearchFilter + "(|(" + posixGidAttr + "={0})" +
                      "(" + groupMemberAttr + "={1})))",
                  new Object[] { gidNumber, uidNumber },
                  SEARCH_CONTROLS);
        }
      } else {
        groupResults =
            ctx.search(baseDN,
                "(&" + groupSearchFilter + "(" + groupMemberAttr + "={0}))",
                new Object[]{userDn},
                SEARCH_CONTROLS);
      }
      if (groupResults != null) {
        while (groupResults.hasMoreElements()) {
          SearchResult groupResult = groupResults.nextElement();
          Attribute groupName = groupResult.getAttributes().get(groupNameAttr);
          groups.add(groupName.get().toString());
        }
      }
    }

    return groups;
  }

  @Override
  public synchronized void setConf(Configuration conf) {
    ldapUrl = conf.get(LDAP_URL_KEY, LDAP_URL_DEFAULT);
    if (ldapUrl == null || ldapUrl.isEmpty()) {
      throw new RuntimeException("LDAP URL is not configured");
    }
    
    useSsl = conf.getBoolean(LDAP_USE_SSL_KEY, LDAP_USE_SSL_DEFAULT);
    keystore = conf.get(LDAP_KEYSTORE_KEY, LDAP_KEYSTORE_DEFAULT);
    
    keystorePass = getPassword(conf, LDAP_KEYSTORE_PASSWORD_KEY,
        LDAP_KEYSTORE_PASSWORD_DEFAULT);
    if (keystorePass.isEmpty()) {
      keystorePass = extractPassword(conf.get(LDAP_KEYSTORE_PASSWORD_FILE_KEY,
          LDAP_KEYSTORE_PASSWORD_FILE_DEFAULT));
    }
    
    bindUser = conf.get(BIND_USER_KEY, BIND_USER_DEFAULT);
    bindPassword = getPassword(conf, BIND_PASSWORD_KEY, BIND_PASSWORD_DEFAULT);
    if (bindPassword.isEmpty()) {
      bindPassword = extractPassword(
          conf.get(BIND_PASSWORD_FILE_KEY, BIND_PASSWORD_FILE_DEFAULT));
    }
    
    baseDN = conf.get(BASE_DN_KEY, BASE_DN_DEFAULT);
    groupSearchFilter =
        conf.get(GROUP_SEARCH_FILTER_KEY, GROUP_SEARCH_FILTER_DEFAULT);
    userSearchFilter =
        conf.get(USER_SEARCH_FILTER_KEY, USER_SEARCH_FILTER_DEFAULT);
    isPosix = groupSearchFilter.contains(POSIX_GROUP) && userSearchFilter
        .contains(POSIX_ACCOUNT);
    groupMemberAttr =
        conf.get(GROUP_MEMBERSHIP_ATTR_KEY, GROUP_MEMBERSHIP_ATTR_DEFAULT);
    groupNameAttr =
        conf.get(GROUP_NAME_ATTR_KEY, GROUP_NAME_ATTR_DEFAULT);
    posixUidAttr =
        conf.get(POSIX_UID_ATTR_KEY, POSIX_UID_ATTR_DEFAULT);
    posixGidAttr =
        conf.get(POSIX_GID_ATTR_KEY, POSIX_GID_ATTR_DEFAULT);

    int dirSearchTimeout = conf.getInt(DIRECTORY_SEARCH_TIMEOUT, DIRECTORY_SEARCH_TIMEOUT_DEFAULT);
    SEARCH_CONTROLS.setTimeLimit(dirSearchTimeout);
    // Limit the attributes returned to only those required to speed up the search.
    // See HADOOP-10626 and HADOOP-12001 for more details.
    SEARCH_CONTROLS.setReturningAttributes(
        new String[] {groupNameAttr, posixUidAttr, posixGidAttr});

    this.conf = conf;
  }

}