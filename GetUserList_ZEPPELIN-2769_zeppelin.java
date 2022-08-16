public class GetUserList {
  public List<String> getUserList(JdbcRealm obj) {
    List<String> userlist = new ArrayList<>();
    PreparedStatement ps = null;
    ResultSet rs = null;
    DataSource dataSource = null;
    String authQuery = "";
    String retval[];
    String tablename = "";
    String username = "";
    String userquery = "";
    try {
      dataSource = (DataSource) FieldUtils.readField(obj, "dataSource", true);
      authQuery = (String) FieldUtils.readField(obj, "authenticationQuery", true);
      LOG.info(authQuery);
      String authQueryLowerCase = authQuery.toLowerCase();
      retval = authQueryLowerCase.split("from", 2);
      if (retval.length >= 2) {
        retval = retval[1].split("with|where", 2);
        tablename = retval[0];
        retval = retval[1].split("where", 2);
        if (retval.length >= 2)
          retval = retval[1].split("=", 2);
        else
          retval = retval[0].split("=", 2);
        username = retval[0];
      }

      if (StringUtils.isBlank(username) || StringUtils.isBlank(tablename)) {
        return userlist;
      }

      userquery = "select ? from ?";

    } catch (IllegalAccessException e) {
      LOG.error("Error while accessing dataSource for JDBC Realm", e);
      return null;
    }

    try {
      Connection con = dataSource.getConnection();
      ps = con.prepareStatement(userquery);
      ps.setString(1, username);
      ps.setString(2, tablename);
      rs = ps.executeQuery();
      while (rs.next()) {
        userlist.add(rs.getString(1).trim());
      }
    } catch (Exception e) {
      LOG.error("Error retrieving User list from JDBC Realm", e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
    }
    return userlist;
  }

}