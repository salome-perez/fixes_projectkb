public class CacheClientProxy {
    public void setSubject(Subject subject) {
        // TODO:hitesh synchronization
        synchronized (this.clientUserAuthsLock) {
          if (this.subject != null) {
            this.subject.logout();
          }
          this.subject = subject;
        }
      }  
      private CacheClientProxy getProxy() {
        return this._proxy;
      }  
}