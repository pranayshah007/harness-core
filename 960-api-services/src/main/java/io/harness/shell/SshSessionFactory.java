/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.shell.AccessType.USER_PASSWORD;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static java.util.Collections.emptyMap;

import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.network.Http;
import io.harness.security.EncryptionUtils;
import io.harness.ssh.SshHelperUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 2/8/16.
 */
@UtilityClass
@Slf4j
public class SshSessionFactory {
  private static final String SSH_NETWORK_PROXY = "SSH_NETWORK_PROXY";
  private static final int NR_OF_RETRIES_FOR_SSH_SESSION_CONNECTION = 6;

  /**
   * Gets the SSH session with jumpbox.
   *
   * @param config the config
   * @param logCallback the LogCallback
   * @return the SSH session with jumpbox
   * @throws JSchException the j sch exception
   */
  public static Session getSSHSessionWithJumpbox(SshSessionConfig config, LogCallback logCallback)
      throws JSchException {
    Session jumpboxSession = getSSHSession(config.getBastionHostConfig());
    int forwardingPort = jumpboxSession.setPortForwardingL(0, config.getHost(), config.getPort());
    log.info("portforwarding port " + forwardingPort);
    logCallback.saveExecutionLog("portforwarding port " + forwardingPort);

    SshSessionConfig newConfig = aSshSessionConfig()
                                     .withUserName(config.getUserName())
                                     .withPassword(config.getPassword())
                                     .withKey(config.getKey())
                                     .withHost("127.0.0.1")
                                     .withPort(forwardingPort)
                                     .withUseSshClient(config.isUseSshClient())
                                     .withUseSshj(config.isUseSshj())
                                     .build();
    return getSSHSession(newConfig);
  }

  /**
   * Gets the SSH session.
   *
   * @param config the config
   * @return the SSH session
   * @throws JSchException the j sch exception
   */
  public static Session getSSHSession(SshSessionConfig config) throws JSchException {
    return getSSHSession(config, new NoopExecutionCallback());
  }

  public static Session getSSHSession(SshSessionConfig config, LogCallback logCallback) throws JSchException {
    Session session = null;
    int retryCount = 0;
    while (retryCount <= NR_OF_RETRIES_FOR_SSH_SESSION_CONNECTION && session == null) {
      try {
        TimeUnit.SECONDS.sleep(1);
        retryCount++;
        session = fetchSSHSession(config, logCallback);
      } catch (InterruptedException ie) {
        log.error("Interrupted exception while fetching ssh session", ie);
      } catch (JSchException jse) {
        if (retryCount == NR_OF_RETRIES_FOR_SSH_SESSION_CONNECTION) {
          log.error("Jschexception while SSH connection with retry count {}, cause {}", retryCount, jse.getMessage());
          throw jse;
        }
      }
    }

    return session;
  }

  /**
   * Gets the SSH session.
   *
   * @param config the config
   * @param logCallback the LogCallback
   * @return the SSH session
   * @throws JSchException the j sch exception
   */
  public static Session fetchSSHSession(SshSessionConfig config, LogCallback logCallback) throws JSchException {
    JSch jsch = new JSch();
    log.info("[SshSessionFactory]: SSHSessionConfig is : {}", config);
    Session session;

    if (config.getAuthenticationScheme() != null && config.getAuthenticationScheme() == KERBEROS) {
      logCallback.saveExecutionLog("SSH using Kerberos Auth");
      log.info("[SshSessionFactory]: SSH using Kerberos Auth");
      generateTGTUsingSshConfig(config, logCallback);

      session = jsch.getSession(config.getKerberosConfig().getPrincipal(), config.getHost(), config.getPort());
      session.setConfig("PreferredAuthentications", "gssapi-with-mic");
    } else if (config.getAccessType() != null && config.getAccessType() == USER_PASSWORD) {
      log.info("[SshSessionFactory]: SSH using Username Password");
      session = jsch.getSession(config.getUserName(), config.getHost(), config.getPort());
      byte[] password = EncryptionUtils.toBytes(config.getSshPassword(), Charsets.UTF_8);
      session.setPassword(password);
      session.setUserInfo(new SshUserInfo(new String(password, Charsets.UTF_8)));
    } else if (config.isKeyLess()) {
      log.info("[SshSessionFactory]: SSH using KeyPath");
      String keyPath = getKeyPath(config);
      if (!new File(keyPath).isFile()) {
        throw new JSchException("File at " + keyPath + " does not exist", new FileNotFoundException());
      }
      if (isEmpty(config.getKeyPassphrase())) {
        jsch.addIdentity(keyPath);
      } else {
        jsch.addIdentity(keyPath, EncryptionUtils.toBytes(config.getKeyPassphrase(), Charsets.UTF_8));
      }
      session = jsch.getSession(config.getUserName(), config.getHost(), config.getPort());
    } else if (config.isVaultSSH()) {
      log.info("[SshSessionFactory]: SSH using Vault SSH secret engine with SignedPublicKey: {} ",
          config.getSignedPublicKey());

      final char[] copyOfKey = getCopyOfKey(config);
      if (isEmpty(config.getKeyPassphrase())) {
        jsch.addIdentity(config.getKeyName(), EncryptionUtils.toBytes(copyOfKey, Charsets.UTF_8),
            EncryptionUtils.toBytes(config.getSignedPublicKey().toCharArray(), Charsets.UTF_8), null);
      } else {
        jsch.addIdentity(config.getKeyName(), EncryptionUtils.toBytes(copyOfKey, Charsets.UTF_8),
            EncryptionUtils.toBytes(config.getSignedPublicKey().toCharArray(), Charsets.UTF_8),
            EncryptionUtils.toBytes(config.getKeyPassphrase(), Charsets.UTF_8));
      }
      session = jsch.getSession(config.getUserName(), config.getHost(), config.getPort());
      log.info("[VaultSSH]: SSH using Vault SSH secret engine with SignedPublicKey is completed: {} ",
          config.getSignedPublicKey());
    } else {
      if (config.getKey() != null && config.getKey().length > 0) {
        // Copy Key because EncryptionUtils has a side effect of modifying the original array
        final char[] copyOfKey = getCopyOfKey(config);
        log.info("SSH using Key");
        if (null == config.getKeyPassphrase()) {
          jsch.addIdentity(config.getKeyName(), EncryptionUtils.toBytes(copyOfKey, Charsets.UTF_8), null, null);
        } else {
          jsch.addIdentity(config.getKeyName(), EncryptionUtils.toBytes(copyOfKey, Charsets.UTF_8), null,
              EncryptionUtils.toBytes(config.getKeyPassphrase(), Charsets.UTF_8));
        }
        session = jsch.getSession(config.getUserName(), config.getHost(), config.getPort());
      } else {
        log.warn("User password on commandline is not supported...");
        session = jsch.getSession(config.getUserName(), config.getHost(), config.getPort());
        session.setPassword(new String(config.getPassword()));
        session.setUserInfo(new SshUserInfo(new String(config.getPassword())));
      }
    }
    session.setConfig("StrictHostKeyChecking", "no");
    session.setTimeout(config.getSshSessionTimeout());
    session.setServerAliveInterval(10 * 1000); // Send noop packet every 10 sec

    final String ssh_network_proxy = System.getenv(SSH_NETWORK_PROXY);
    boolean enableProxy = "true".equals(ssh_network_proxy);
    log.info("proxy enabled: " + enableProxy + ". Connecting host: " + config.getHost());
    if (enableProxy) {
      if (Http.getProxyHostName() != null && !Http.shouldUseNonProxy(config.getHost())) {
        log.info("Using proxy");
        ProxyHTTP proxyHTTP = getProxy(config, logCallback);
        session.setProxy(proxyHTTP);
      }
    }
    session.connect(config.getSshConnectionTimeout());

    return session;
  }

  private static ProxyHTTP getProxy(SshSessionConfig config, LogCallback logCallback) {
    String host = Http.getProxyHostName();
    String port = Http.getProxyPort();

    ProxyHTTP proxyHTTP = new ProxyHTTP(host, Integer.parseInt(port));
    proxyHTTP.setUserPasswd(Http.getProxyUserName(), Http.getProxyPassword());

    return proxyHTTP;
  }

  @VisibleForTesting
  public static char[] getCopyOfKey(SshSessionConfig config) {
    return Arrays.copyOf(config.getKey(), config.getKey().length);
  }

  public static void generateTGTUsingSshConfig(SshSessionConfig config, LogCallback logCallback) throws JSchException {
    if (config.getKerberosConfig() == null) {
      return;
    }
    log.info("Do we need to generate Ticket Granting Ticket(TGT)? " + config.getKerberosConfig().isGenerateTGT());
    if (config.getKerberosConfig().isGenerateTGT()) {
      SshHelperUtils.generateTGT(config.getKerberosConfig().getPrincipalWithRealm(),
          config.getPassword() != null ? new String(config.getPassword()) : null,
          config.getKerberosConfig().getKeyTabFilePath(), logCallback, emptyMap());
    }
  }

  public static String getKeyPath(SshSessionConfig config) {
    String userhome = System.getProperty("user.home");
    String keyPath = userhome + File.separator + ".ssh" + File.separator + "id_rsa";
    if (config.getKeyPath() != null) {
      keyPath = config.getKeyPath();
      keyPath = keyPath.replace("$HOME", userhome);
    }
    return keyPath;
  }
}
