public class AbstractDelegationTokenSecretManager {
  private void removeExpiredToken() throws IOException {
    long now = Time.now();
    Set<TokenIdent> expiredTokens = new HashSet<TokenIdent>();
    synchronized (this) {
      Iterator<Map.Entry<TokenIdent, DelegationTokenInformation>> i =
          currentTokens.entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry<TokenIdent, DelegationTokenInformation> entry = i.next();
        long renewDate = entry.getValue().getRenewDate();
        if (renewDate < now) {
          expiredTokens.add(entry.getKey());
          i.remove();
        }
      }
    }
    // don't hold lock on 'this' to avoid edit log updates blocking token ops
    logExpireTokens(expiredTokens);
  }

  protected void logExpireTokens(
      Collection<TokenIdent> expiredTokens) throws IOException {
    for (TokenIdent ident : expiredTokens) {
      logExpireToken(ident);
      LOG.info("Removing expired token " + formatTokenId(ident));
      removeStoredToken(ident);
    }
  }

}