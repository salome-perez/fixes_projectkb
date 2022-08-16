public class CacheClientNotifier {
  protected void registerGFEClient(DataInputStream dis, DataOutputStream dos, Socket socket,
      boolean isPrimary, long startTime, Version clientVersion, long acceptorId,
      boolean notifyBySubscription) throws IOException {
    // Read the ports and throw them away. We no longer need them
    int numberOfPorts = dis.readInt();
    for (int i = 0; i < numberOfPorts; i++) {
      dis.readInt();
    }
    // Read the handshake identifier and convert it to a string member id
    ClientProxyMembershipID proxyID = null;
    CacheClientProxy proxy;
    AccessControl authzCallback = null;
    byte clientConflation = HandShake.CONFLATION_DEFAULT;
    try {
      proxyID = ClientProxyMembershipID.readCanonicalized(dis);
      if (getBlacklistedClient().contains(proxyID)) {
        writeException(dos, HandShake.REPLY_INVALID,
            new Exception("This client is blacklisted by server"), clientVersion);
        return;
      }
      proxy = getClientProxy(proxyID);
      DistributedMember member = proxyID.getDistributedMember();

      DistributedSystem system = this.getCache().getDistributedSystem();
      Properties sysProps = system.getProperties();
      String authenticator = sysProps.getProperty(SECURITY_CLIENT_AUTHENTICATOR);

      if (clientVersion.compareTo(Version.GFE_603) >= 0) {
        byte[] overrides = HandShake.extractOverrides(new byte[] {(byte) dis.read()});
        clientConflation = overrides[0];
      } else {
        clientConflation = (byte) dis.read();
      }

      switch (clientConflation) {
        case HandShake.CONFLATION_DEFAULT:
        case HandShake.CONFLATION_OFF:
        case HandShake.CONFLATION_ON:
          break;
        default:
          writeException(dos, HandShake.REPLY_INVALID,
              new IllegalArgumentException("Invalid conflation byte"), clientVersion);
          return;
      }
      Object subject = null;
      Properties credentials =
          HandShake.readCredentials(dis, dos, system, this.cache.getSecurityService());
      if (credentials != null) {
        if (securityLogWriter.fineEnabled()) {
          securityLogWriter
              .fine("CacheClientNotifier: verifying credentials for proxyID: " + proxyID);
        }
        subject =
            HandShake.verifyCredentials(authenticator, credentials, system.getSecurityProperties(),
                this.logWriter, this.securityLogWriter, member, this.cache.getSecurityService());
      }

      Subject shiroSubject =
          subject != null && subject instanceof Subject ? (Subject) subject : null;
      proxy = registerClient(socket, proxyID, proxy, isPrimary, clientConflation, clientVersion,
          acceptorId, notifyBySubscription, shiroSubject);

      if (proxy != null && subject != null) {
        if (subject instanceof Principal) {
          Principal principal = (Principal) subject;
          if (securityLogWriter.fineEnabled()) {
            securityLogWriter
                .fine("CacheClientNotifier: successfully verified credentials for proxyID: "
                    + proxyID + " having principal: " + principal.getName());
          }

          String postAuthzFactoryName = sysProps.getProperty(SECURITY_CLIENT_ACCESSOR_PP);
          if (postAuthzFactoryName != null && postAuthzFactoryName.length() > 0) {
            if (principal == null) {
              securityLogWriter.warning(
                  LocalizedStrings.CacheClientNotifier_CACHECLIENTNOTIFIER_POST_PROCESS_AUTHORIZATION_CALLBACK_ENABLED_BUT_AUTHENTICATION_CALLBACK_0_RETURNED_WITH_NULL_CREDENTIALS_FOR_PROXYID_1,
                  new Object[] {SECURITY_CLIENT_AUTHENTICATOR, proxyID});
            }
            Method authzMethod = ClassLoadUtil.methodFromName(postAuthzFactoryName);
            authzCallback = (AccessControl) authzMethod.invoke(null, (Object[]) null);
            authzCallback.init(principal, member, this.getCache());
          }
          proxy.setPostAuthzCallback(authzCallback);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new IOException(
          LocalizedStrings.CacheClientNotifier_CLIENTPROXYMEMBERSHIPID_OBJECT_COULD_NOT_BE_CREATED_EXCEPTION_OCCURRED_WAS_0
              .toLocalizedString(e));
    } catch (AuthenticationRequiredException ex) {
      securityLogWriter.warning(
          LocalizedStrings.CacheClientNotifier_AN_EXCEPTION_WAS_THROWN_FOR_CLIENT_0_1,
          new Object[] {proxyID, ex});
      writeException(dos, HandShake.REPLY_EXCEPTION_AUTHENTICATION_REQUIRED, ex, clientVersion);
      return;
    } catch (AuthenticationFailedException ex) {
      securityLogWriter.warning(
          LocalizedStrings.CacheClientNotifier_AN_EXCEPTION_WAS_THROWN_FOR_CLIENT_0_1,
          new Object[] {proxyID, ex});
      writeException(dos, HandShake.REPLY_EXCEPTION_AUTHENTICATION_FAILED, ex, clientVersion);
      return;
    } catch (CacheException e) {
      logger.warn(LocalizedMessage.create(
          LocalizedStrings.CacheClientNotifier_0_REGISTERCLIENT_EXCEPTION_ENCOUNTERED_IN_REGISTRATION_1,
          new Object[] {this, e}), e);
      IOException io = new IOException(
          LocalizedStrings.CacheClientNotifier_EXCEPTION_OCCURRED_WHILE_TRYING_TO_REGISTER_INTEREST_DUE_TO_0
              .toLocalizedString(e.getMessage()));
      io.initCause(e);
      throw io;
    } catch (Exception ex) {
      logger.warn(LocalizedMessage.create(
          LocalizedStrings.CacheClientNotifier_AN_EXCEPTION_WAS_THROWN_FOR_CLIENT_0_1,
          new Object[] {proxyID, ""}), ex);
      writeException(dos, CommunicationMode.UnsuccessfulServerToClient.getModeNumber(), ex,
          clientVersion);
      return;
    }

    this.statistics.endClientRegistration(startTime);
  }

  private CacheClientProxy registerClient(Socket socket, ClientProxyMembershipID proxyId,
      CacheClientProxy proxy, boolean isPrimary, byte clientConflation, Version clientVersion,
      long acceptorId, boolean notifyBySubscription, Subject subject)
      throws IOException, CacheException {
    CacheClientProxy l_proxy = proxy;

    // Initialize the socket
    socket.setTcpNoDelay(true);
    socket.setSendBufferSize(CacheClientNotifier.socketBufferSize);
    socket.setReceiveBufferSize(CacheClientNotifier.socketBufferSize);

    if (logger.isDebugEnabled()) {
      logger.debug(
          "CacheClientNotifier: Initialized server-to-client socket with send buffer size: {} bytes and receive buffer size: {} bytes",
          socket.getSendBufferSize(), socket.getReceiveBufferSize());
    }

    // Determine whether the client is durable or not.
    byte responseByte = CommunicationMode.SuccessfulServerToClient.getModeNumber();
    String unsuccessfulMsg = null;
    boolean successful = true;
    boolean clientIsDurable = proxyId.isDurable();
    if (logger.isDebugEnabled()) {
      if (clientIsDurable) {
        logger.debug("CacheClientNotifier: Attempting to register durable client: {}",
            proxyId.getDurableId());
      } else {
        logger.debug("CacheClientNotifier: Attempting to register non-durable client");
      }
    }

    byte epType = 0x00;
    int qSize = 0;
    if (clientIsDurable) {
      if (l_proxy == null) {
        if (isTimedOut(proxyId)) {
          qSize = PoolImpl.PRIMARY_QUEUE_TIMED_OUT;
        } else {
          qSize = PoolImpl.PRIMARY_QUEUE_NOT_AVAILABLE;
        }
        // No proxy exists for this durable client. It must be created.
        if (logger.isDebugEnabled()) {
          logger.debug(
              "CacheClientNotifier: No proxy exists for durable client with id {}. It must be created.",
              proxyId.getDurableId());
        }
        l_proxy =
            new CacheClientProxy(this, socket, proxyId, isPrimary, clientConflation, clientVersion,
                acceptorId, notifyBySubscription, this.cache.getSecurityService(), subject);
        successful = this.initializeProxy(l_proxy);
      } else {
        l_proxy.setSubject(subject);
        if (proxy.isPrimary()) {
          epType = (byte) 2;
        } else {
          epType = (byte) 1;
        }
        qSize = proxy.getQueueSize();
        // A proxy exists for this durable client. It must be reinitialized.
        if (l_proxy.isPaused()) {
          if (CacheClientProxy.testHook != null) {
            CacheClientProxy.testHook.doTestHook("CLIENT_PRE_RECONNECT");
          }
          if (l_proxy.lockDrain()) {
            try {
              if (logger.isDebugEnabled()) {
                logger.debug(
                    "CacheClientNotifier: A proxy exists for durable client with id {}. This proxy will be reinitialized: {}",
                    proxyId.getDurableId(), l_proxy);
              }
              this.statistics.incDurableReconnectionCount();
              l_proxy.getProxyID().updateDurableTimeout(proxyId.getDurableTimeout());
              l_proxy.reinitialize(socket, proxyId, this.getCache(), isPrimary, clientConflation,
                  clientVersion);
              l_proxy.setMarkerEnqueued(true);
              if (CacheClientProxy.testHook != null) {
                CacheClientProxy.testHook.doTestHook("CLIENT_RECONNECTED");
              }
            } finally {
              l_proxy.unlockDrain();
            }
          } else {
            unsuccessfulMsg =
                LocalizedStrings.CacheClientNotifier_COULD_NOT_CONNECT_DUE_TO_CQ_BEING_DRAINED
                    .toLocalizedString();
            logger.warn(unsuccessfulMsg);
            responseByte = HandShake.REPLY_REFUSED;
            if (CacheClientProxy.testHook != null) {
              CacheClientProxy.testHook.doTestHook("CLIENT_REJECTED_DUE_TO_CQ_BEING_DRAINED");
            }
          }
        } else {
          // The existing proxy is already running (which means that another
          // client is already using this durable id.
          unsuccessfulMsg =
              LocalizedStrings.CacheClientNotifier_CACHECLIENTNOTIFIER_THE_REQUESTED_DURABLE_CLIENT_HAS_THE_SAME_IDENTIFIER__0__AS_AN_EXISTING_DURABLE_CLIENT__1__DUPLICATE_DURABLE_CLIENTS_ARE_NOT_ALLOWED
                  .toLocalizedString(new Object[] {proxyId.getDurableId(), proxy});
          logger.warn(unsuccessfulMsg);
          // Set the unsuccessful response byte.
          responseByte = HandShake.REPLY_EXCEPTION_DUPLICATE_DURABLE_CLIENT;
        }
      }
    } else {
      CacheClientProxy staleClientProxy = this.getClientProxy(proxyId);
      boolean toCreateNewProxy = true;
      if (staleClientProxy != null) {
        if (staleClientProxy.isConnected() && staleClientProxy.getSocket().isConnected()) {
          successful = false;
          toCreateNewProxy = false;
        } else {
          // A proxy exists for this non-durable client. It must be closed.
          if (logger.isDebugEnabled()) {
            logger.debug(
                "CacheClientNotifier: A proxy exists for this non-durable client. It must be closed.");
          }
          if (staleClientProxy.startRemoval()) {
            staleClientProxy.waitRemoval();
          } else {
            staleClientProxy.close(false, false); // do not check for queue, just close it
            removeClientProxy(staleClientProxy); // remove old proxy from proxy set
          }
        }
      } // non-null stale proxy

      if (toCreateNewProxy) {
        // Create the new proxy for this non-durable client
        l_proxy =
            new CacheClientProxy(this, socket, proxyId, isPrimary, clientConflation, clientVersion,
                acceptorId, notifyBySubscription, this.cache.getSecurityService(), subject);
        successful = this.initializeProxy(l_proxy);
      }
    }

    if (!successful) {
      l_proxy = null;
      responseByte = HandShake.REPLY_REFUSED;
      unsuccessfulMsg =
          LocalizedStrings.CacheClientNotifier_CACHECLIENTNOTIFIER_A_PREVIOUS_CONNECTION_ATTEMPT_FROM_THIS_CLIENT_IS_STILL_BEING_PROCESSED__0
              .toLocalizedString(new Object[] {proxyId});
      logger.warn(unsuccessfulMsg);
    }

    // Tell the client that the proxy has been registered using the response
    // byte. This byte will be read on the client by the CacheClientUpdater to
    // determine whether the registration was successful. The times when
    // registration is unsuccessful currently are if a duplicate durable client
    // is attempted to be registered or authentication fails.
    try {
      DataOutputStream dos =
          new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
      // write the message type, message length and the error message (if any)
      writeMessage(dos, responseByte, unsuccessfulMsg, clientVersion, epType, qSize);
    } catch (IOException ioe) {// remove the added proxy if we get IOException.
      if (l_proxy != null) {
        boolean keepProxy = l_proxy.close(false, false); // do not check for queue, just close it
        if (!keepProxy) {
          removeClientProxy(l_proxy);
        }
      }
      throw ioe;
    }

    if (unsuccessfulMsg != null && logger.isDebugEnabled()) {
      logger.debug(unsuccessfulMsg);
    }

    // If the client is not durable, start its message processor
    // Starting it here (instead of in the CacheClientProxy constructor)
    // will ensure that the response byte is sent to the client before
    // the marker message. If the client is durable, the message processor
    // is not started until the clientReady message is received.
    if (!clientIsDurable && l_proxy != null
        && responseByte == CommunicationMode.SuccessfulServerToClient.getModeNumber()) {
      // The startOrResumeMessageDispatcher tests if the proxy is a primary.
      // If this is a secondary proxy, the dispatcher is not started.
      // The false parameter signifies that a marker message has not already been
      // processed. This will generate and send one.
      l_proxy.startOrResumeMessageDispatcher(false);
    }

    if (responseByte == CommunicationMode.SuccessfulServerToClient.getModeNumber()) {
      if (logger.isDebugEnabled()) {
        logger.debug("CacheClientNotifier: Successfully registered {}", l_proxy);
      }
    } else {
      logger.warn(LocalizedMessage.create(
          LocalizedStrings.CacheClientNotifier_CACHECLIENTNOTIFIER_UNSUCCESSFULLY_REGISTERED_CLIENT_WITH_IDENTIFIER__0,
          new Object[] {proxyId, responseByte}));
    }
    return l_proxy;
  }

}