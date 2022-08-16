public class FSPermissionChecker {
  public void checkPermission(CachePool pool, FsAction access)
      throws AccessControlException {
    FsPermission mode = pool.getMode();
    if (isSuperUser()) {
      return;
    }
    if (getUser().equals(pool.getOwnerName())
        && mode.getUserAction().implies(access)) {
      return;
    }
    if (getGroups().contains(pool.getGroupName())
        && mode.getGroupAction().implies(access)) {
      return;
    }
    if (mode.getOtherAction().implies(access)) {
      return;
    }
    throw new AccessControlException("Permission denied while accessing pool "
        + pool.getPoolName() + ": user " + getUser() + " does not have "
        + access.toString() + " permissions.");
  }

  private void checkTraverse(INodeAttributes[] inodeAttrs, INode[] inodes,
      String path, int last) throws AccessControlException {
    int j = 0;
    try {
      for (; j <= last; j++) {
        check(inodeAttrs[j], path, FsAction.EXECUTE);
      }
    } catch (AccessControlException e) {
      checkAncestorType(inodes, j, e);
    }
  }

  private void checkAncestorType(INode[] inodes, int checkedAncestorIndex,
      AccessControlException e) throws AccessControlException {
    for (int i = 0; i <= checkedAncestorIndex; i++) {
      if (inodes[i] == null) {
        break;
      }
      if (!inodes[i].isDirectory()) {
        throw new AccessControlException(
            e.getMessage() + " (Ancestor " + inodes[i].getFullPathName()
                + " is not a directory).");
      }
    }
    throw e;
  }

}