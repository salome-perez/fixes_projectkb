public class KerberosName {
  }

  public String getShortName() throws IOException {
    String[] params;
    if (hostName == null) {
      // if it is already simple, just return it
      if (realm == null) {
        return serviceName;
      }
      params = new String[]{realm, serviceName};
    } else {
      params = new String[]{realm, serviceName, hostName};
    }
    for(Rule r: rules) {
      String result = r.apply(params);
      if (result != null) {
        return result;
      }
    }
    LOG.info("No auth_to_local rules applied to {}

    String apply(String[] params) throws IOException {
      String result = null;
      if (isDefault) {
        if (defaultRealm.equals(params[0])) {
          result = params[1];
        }
      } else if (params.length - 1 == numOfComponents) {
        String base = replaceParameters(format, params);
        if (match == null || match.matcher(base).matches()) {
          if (fromPattern == null) {
            result = base;
          } else {
            result = replaceSubstitution(base, fromPattern, toPattern,  repeat);
          }
        }
      }
      if (result != null && nonSimplePattern.matcher(result).find()) {
        LOG.info("Non-simple name {} after auth_to_local rule {}",
            result, this);
      }
      if (toLowerCase && result != null) {
        result = result.toLowerCase(Locale.ENGLISH);
      }
      return result;
    }

}