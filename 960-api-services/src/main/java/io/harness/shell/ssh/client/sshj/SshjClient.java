/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client.sshj;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.SSH_RETRY;
import static io.harness.eraro.ErrorCode.UNKNOWN_EXECUTOR_TYPE_ERROR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.shell.AccessType.USER_PASSWORD;
import static io.harness.shell.AuthenticationScheme.KERBEROS;

import static java.lang.String.format;

import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.client.SshClient;
import io.harness.shell.ssh.client.SshConnection;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.exception.SshClientException;
import io.harness.shell.ssh.exception.SshjClientException;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import com.jcraft.jsch.JSchException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import org.apache.commons.lang3.NotImplementedException;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class SshjClient extends SshClient {
  public SshjClient(SshSessionConfig config, LogCallback logCallback) {
    setSshSessionConfig(config);
    setLogCallback(logCallback);
  }

  @Override
  protected ExecResponse execInternal(ExecRequest commandData, SshConnection sshConnection) {
    try (SshjExecSession execSession = getExecSession(sshConnection)) {
      Session session = execSession.getSession();
      session.allocateDefaultPTY();

      if (commandData.isDisplayCommand()) {
        saveExecutionLog(format("Executing command %s ...", commandData.getCommand()));
      } else {
        saveExecutionLog("Executing command ...");
      }

      Session.Command cmd = session.exec(commandData.getCommand());
      String output = IOUtils.readFully(cmd.getInputStream()).toString();
      cmd.join();
      Integer exitStatus = cmd.getExitStatus();
      return ExecResponse.builder().output(output).exitCode(exitStatus).status(SUCCESS).build();
    } catch (SshClientException ex) {
      if (ex.getCode() == SSH_RETRY) {
        throw ex;
      }
      log.error("Command execution failed with error", ex);
      return ExecResponse.builder().output(null).exitCode(1).status(FAILURE).build();
    } catch (Exception ex) {
      log.error("Command execution failed with error", ex);
      return ExecResponse.builder().output(null).exitCode(1).status(FAILURE).build();
    }
  }

  @Override
  protected ScpResponse scpUploadInternal(ScpRequest commandData, SshConnection connection) throws SshClientException {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  protected SftpResponse sftpDownloadInternal(SftpRequest commandData, SshConnection connection)
      throws SshClientException {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public void testConnection() throws SshClientException {
    try (SshjConnection ignored = getConnection()) {
    } catch (SshjClientException ex) {
      log.error("Failed to connect Host. ", ex);
      if (ex.getCode() != null) {
        throw new SshjClientException(ex.getCode(), ex.getCode().getDescription(), ex);
      } else {
        throw new SshjClientException(ex.getMessage(), ex);
      }
    } catch (Exception exception) {
      log.error("Failed to connect Host. ", exception);
      throw new SshjClientException(exception.getMessage(), exception);
    }
  }

  @Override
  public void testSession(SshConnection sshConnection) throws SshClientException {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public SshjConnection getConnection() throws SshClientException {
    try {
      final SSHClient client = getSshClient();
      return SshjConnection.builder().client(client).build();
    } catch (UserAuthException e) {
      log.error("Failed to user auth", e);
      throw new SshjClientException(e.getMessage(), e);
    } catch (TransportException e) {
      log.error("Transport exception", e);
      throw new SshjClientException(e.getMessage(), e);
    } catch (IOException io) {
      log.error("IOException", io);
      throw new SshjClientException(io.getMessage(), io);
    } catch (JSchException e) {
      log.error("Kerberos exception", e);
      throw new SshjClientException(e.getMessage(), e);
    } catch (GSSException e) {
      log.error("GSSException", e);
      throw new SshjClientException(e.getMessage(), e);
    } catch (LoginException e) {
      log.error("LoginException", e);
      throw new SshjClientException(e.getMessage(), e);
    }
  }

  @NotNull
  private SSHClient getSshClient() throws IOException, JSchException, GSSException, LoginException {
    SshSessionConfig config = getSshSessionConfig();
    switch (config.getExecutorType()) {
      case PASSWORD_AUTH:
      case KEY_AUTH:
        return getSSHSessionWithRetry(config);
      default:
        throw new SshjClientException(
            UNKNOWN_EXECUTOR_TYPE_ERROR, new Throwable("Unknown executor type: " + config.getExecutorType()));
    }
  }

  private SSHClient getSSHSessionWithRetry(SshSessionConfig config)
      throws IOException, GSSException, LoginException, JSchException {
    SSHClient session = null;
    int retryCount = 0;
    while (retryCount <= 6 && session == null) {
      try {
        TimeUnit.SECONDS.sleep(1);
        retryCount++;
        session = fetchSSHSession(config, getLogCallback());
      } catch (InterruptedException ie) {
        log.error("exception while fetching ssh session", ie);
        Thread.currentThread().interrupt();
      } catch (IOException | JSchException | LoginException | GSSException e) {
        if (retryCount == 6) {
          return fetchSSHSession(config, getLogCallback());
        }
        log.error("Jschexception while SSH connection with retry count {}", retryCount, e);
      }
    }

    return session;
  }

  private SSHClient fetchSSHSession(SshSessionConfig config, LogCallback logCallback)
      throws IOException, JSchException, LoginException, GSSException {
    DefaultConfig sshjConfig = new DefaultConfig();
    final SSHClient client = new SSHClient(sshjConfig);
    client.addHostKeyVerifier(new PromiscuousVerifier());
    log.info("[SshSessionFactory]: SSHSessionConfig is : {}", config);

    client.connect(config.getHost(), config.getPort());
    client.setTimeout(config.getSshSessionTimeout());
    client.getConnection().getKeepAlive().setKeepAliveInterval(10);
    client.useCompression();

    if (config.getAuthenticationScheme() != null && config.getAuthenticationScheme() == KERBEROS) {
      logCallback.saveExecutionLog("SSH using Kerberos Auth");
      log.info("[SshSessionFactory]: SSH using Kerberos Auth");
      generateTGTUsingSshConfig(config, logCallback);
      client.authGssApiWithMic(config.getKerberosConfig().getPrincipal(),
          new LoginContext(config.getKerberosConfig().getPrincipal()),
          new Oid(config.getKerberosConfig().getPrincipalWithRealm()));
    } else if (config.getAccessType() != null && config.getAccessType() == USER_PASSWORD) {
      log.info("[SshSessionFactory]: SSH using Username Password");
      client.authPassword(config.getUserName(), config.getSshPassword());
    } else if (config.isKeyLess()) {
      log.info("[SshSessionFactory]: SSH using KeyPath");
      String keyPath = getKeyPath();
      if (!new File(keyPath).isFile()) {
        throw new JSchException("File at " + keyPath + " does not exist", new FileNotFoundException());
      }

      OpenSSHKeyFile openSSHKeyFile = new OpenSSHKeyFile();
      client.authPublickey(config.getUserName(), openSSHKeyFile);
      if (isEmpty(config.getKeyPassphrase())) {
        openSSHKeyFile.init(new File(keyPath));
      } else {
        openSSHKeyFile.init(new File(keyPath), new StaticPasswordFinder(new String(config.getKeyPassphrase())));
      }

      client.authPublickey(config.getUserName(), openSSHKeyFile);
    } else if (config.isVaultSSH()) {
      log.info("[SshSessionFactory]: SSH using Vault SSH secret engine with SignedPublicKey: {} ",
          config.getSignedPublicKey());

      OpenSSHKeyFile openSSHKeyFile = new OpenSSHKeyFile();
      openSSHKeyFile.init(new String(getCopyOfKey()), config.getSignedPublicKey());

      OpenSSHKeyV1KeyFile openSSHKeyV1KeyFile = new OpenSSHKeyV1KeyFile();
      openSSHKeyV1KeyFile.init(new String(getCopyOfKey()), config.getSignedPublicKey());

      client.authPublickey(config.getUserName(), openSSHKeyFile, openSSHKeyV1KeyFile);
      log.info("[VaultSSH]: SSH using Vault SSH secret engine with SignedPublicKey is completed: {} ",
          config.getSignedPublicKey());

    } else {
      if (config.getKey() != null && config.getKey().length > 0) {
        // Copy Key because EncryptionUtils has a side effect of modifying the original array
        final char[] copyOfKey = getCopyOfKey();
        log.info("SSH using Key");

        client.loadKeys(new String(copyOfKey), null,
            isEmpty(config.getKeyPassphrase()) ? null
                                               : new StaticPasswordFinder(new String(config.getKeyPassphrase())));
        client.authPublickey(config.getUserName());
        log.info("[VaultSSH]: SSH using Vault SSH secret engine with SignedPublicKey is completed: {} ",
            config.getSignedPublicKey());
      } else {
        log.warn("User password on commandline is not supported...");
        client.authPassword(config.getUserName(), config.getPassword());
      }
    }

    return client;
  }

  @Override
  protected SshjExecSession getExecSession(SshConnection sshConnection) throws SshClientException {
    try {
      SSHClient client = ((SshjConnection) sshConnection).getClient();
      ;
      return SshjExecSession.builder().session(client.startSession()).build();
    } catch (TransportException e) {
      log.error("Transport exception", e);
      throw new SshjClientException("Transport exception " + e.getMessage(), e);
    } catch (ConnectionException e) {
      log.error("Connection exception", e);
      throw new SshjClientException("Connection exception " + e.getMessage(), e);
    }
  }

  @Override
  protected Object getSftpSession(SshConnection sshConnection) throws SshClientException {
    throw new NotImplementedException("Not implemented");
  }
}
