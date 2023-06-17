/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client.sshj;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SSH_RETRY;
import static io.harness.eraro.ErrorCode.UNKNOWN_EXECUTOR_TYPE_ERROR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.shell.AccessType.USER_PASSWORD;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.SshHelperUtils.normalizeError;

import static java.lang.String.format;

import io.harness.eraro.ErrorCode;
import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.client.SshClient;
import io.harness.shell.ssh.client.SshClientType;
import io.harness.shell.ssh.client.SshConnection;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.exception.JschClientException;
import io.harness.shell.ssh.exception.SshClientException;
import io.harness.shell.ssh.exception.SshjClientException;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import com.google.common.base.Charsets;
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import com.jcraft.jsch.JSchException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import net.schmizz.sshj.userauth.password.PasswordFinder;
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
  public SshClientType getType() {
    return SshClientType.SSHJ;
  }

  @Override
  protected ExecResponse execInternal(ExecRequest commandData, SshConnection sshConnection) {
    try (SshjExecSession execSession = getExecSession(sshConnection)) {
      Session session = execSession.getSession();
      saveExecutionLog(format("Connection to %s established", getSshSessionConfig().getHost()));
      session.allocateDefaultPTY();

      if (commandData.isDisplayCommand()) {
        saveExecutionLog(format("Executing command %s ...", commandData.getCommand()));
      } else {
        saveExecutionLog("Executing command ...");
      }

      Session.Command cmd = session.exec(commandData.getCommand());
      String output = IOUtils.readFully(cmd.getInputStream()).toString();
      cmd.join();
      if (isNotEmpty(output)) {
        saveExecutionLog("Script logs: \n" + output);
      } else {
        saveExecutionLog("No logs from the script");
      }
      Integer exitStatus = cmd.getExitStatus();
      saveExecutionLog("Executing command completed");
      return ExecResponse.builder().output(output).exitCode(exitStatus).status(SUCCESS).build();
    } catch (SshClientException ex) {
      if (ex.getCode() == SSH_RETRY) {
        throw ex;
      }
      log.error("Command execution failed with error", ex);
      saveExecutionLog("Command finished with status " + FAILURE, FAILURE);
      return ExecResponse.builder().output(null).exitCode(1).status(FAILURE).build();
    } catch (Exception ex) {
      log.error("Command execution failed with error", ex);
      saveExecutionLog("Command finished with status " + FAILURE, FAILURE);
      return ExecResponse.builder().output(null).exitCode(1).status(FAILURE).build();
    }
  }

  @Override
  public void createDirectoryForScp(ScpRequest request) {
    saveExecutionLog("Creating directory " + request.getRemoteFilePath());
    String command = format("mkdir -p \"%s\"", request.getRemoteFilePath());
    exec(ExecRequest.builder().displayCommand(false).command(command).build());
    saveExecutionLog("Created directory " + request.getRemoteFilePath() + " successfully");
  }

  @Override
  protected ScpResponse scpUploadInternal(ScpRequest scpRequest, SshConnection connection) throws SshClientException {
    try (SshjScpSession scpSession = getScpSession(connection)) {
      try (StreamingInMemorySourceFile sourceFile = new StreamingInMemorySourceFile(scpRequest.getFileProvider())) {
        scpSession.getScpFileTransfer().upload(sourceFile, scpRequest.getRemoteFilePath());
        saveExecutionLog("File successfully transferred to " + getSshSessionConfig().getHost() + ":"
            + scpRequest.getRemoteFilePath());
        return ScpResponse.builder().exitCode(0).status(SUCCESS).build();
      }
    } catch (ConnectionException e) {
      log.error("Connection exception", e);
      if (e.getMessage().contains("open failed")) {
        throw new SshjClientException(SSH_RETRY, "Connection exception " + e.getMessage(), e);
      }
      saveExecutionLog("Command finished with status " + FAILURE, FAILURE);
      throw new SshjClientException("Connection exception " + e.getMessage(), e);
    } catch (Exception e) {
      saveExecutionLogError(
          "File transfer to " + getSshSessionConfig().getHost() + ":" + scpRequest.getRemoteFilePath() + " failed");
      throw new RuntimeException(e);
    }
  }

  @Override
  protected SftpResponse sftpDownloadInternal(SftpRequest sftpRequest, SshConnection connection)
      throws SshClientException {
    try (SshjSftpSession sftpSession = getSftpSession(connection)) {
      SFTPClient sftpClient = sftpSession.getSftpClient();
      try (StreamingInMemoryDestFile dest = new StreamingInMemoryDestFile(new ByteArrayOutputStream())) {
        String path = sftpClient.canonicalize(sftpRequest.getDirectory() + "/" + sftpRequest.getFileName());
        sftpClient.get(path, dest);
        String content = dest.getOutputStream().toString(Charsets.UTF_8);
        if (sftpRequest.isCleanup()) {
          sftpClient.getSFTPEngine().remove(path);
        }
        saveExecutionLog("SFTP Download finished");
        return SftpResponse.builder().success(true).content(content).status(SUCCESS).exitCode(0).build();
      }
    } catch (Exception e) {
      saveExecutionLog("Command finished with status " + FAILURE, FAILURE);
      throw new SshjClientException(e.getMessage());
    }
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
    try (SshjExecSession execSession = getExecSession(sshConnection)) {
      Session session = execSession.getSession();
      Session.Command cmd = session.exec("true");
      String output = IOUtils.readFully(cmd.getInputStream()).toString();
      cmd.join();
      Integer exitStatus = cmd.getExitStatus();
      log.info("Session connection test successful with output {}, exit code {}", output, exitStatus);
    } catch (JSchException ex) {
      log.error("Failed to validate Host: ", ex);
      ErrorCode errorCode = normalizeError(ex);
      throw new JschClientException(errorCode, errorCode.getDescription(), ex);
    } catch (Exception exception) {
      log.error("Failed to connect Host. ", exception);
      throw new JschClientException(exception.getMessage(), exception);
    }
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
        if (e instanceof JSchException) {
          log.error("Exception with retry count {}, message: {}", retryCount, e.getMessage());
        } else {
          log.error("Exception with retry count {}", retryCount, e);
        }
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

    if (config.getAuthenticationScheme() != null && config.getAuthenticationScheme() == KERBEROS) {
      logCallback.saveExecutionLog("SSH using Kerberos Auth");
      log.info("[SshSessionFactory]: SSH using Kerberos Auth");
      generateTGTUsingSshConfig(config, logCallback);
      client.authGssApiWithMic(config.getKerberosConfig().getPrincipal(),
          new LoginContext(config.getKerberosConfig().getPrincipal()),
          new Oid(config.getKerberosConfig().getPrincipalWithRealm()));
    } else if (config.isVaultSSH()) {
      logCallback.saveExecutionLog("SSH using Vault");
      log.info("[SshSessionFactory]: SSH using Vault SSH secret engine with SignedPublicKey: {} ",
          config.getSignedPublicKey());

      client.authPublickey(config.getUserName(), getKeyProviders(config));
      log.info("[VaultSSH]: SSH using Vault SSH secret engine with SignedPublicKey is completed: {} ",
          config.getSignedPublicKey());

    } else if (config.getAccessType() != null && config.getAccessType() == USER_PASSWORD) {
      logCallback.saveExecutionLog("SSH using Username Password");
      log.info("[SshSessionFactory]: SSH using Username Password");
      client.authPassword(config.getUserName(), config.getSshPassword());
    } else if (config.isKeyLess()) {
      logCallback.saveExecutionLog("SSH using KeyPath");
      log.info("[SshSessionFactory]: SSH using KeyPath");
      String keyPath = getKeyPath();
      if (!new File(keyPath).isFile()) {
        throw new JSchException("File at " + keyPath + " does not exist", new FileNotFoundException());
      }
      client.authPublickey(config.getUserName(), getKeyProviders(config));
    } else {
      if (isNotEmpty(config.getKey())) {
        // Copy Key because EncryptionUtils has a side effect of modifying the original array
        log.info("SSH using Key");

        client.authPublickey(config.getUserName(), getKeyProviders(config));
        log.info("[VaultSSH]: SSH using Vault SSH secret engine with SignedPublicKey is completed: {} ",
            config.getSignedPublicKey());
      } else {
        log.warn("User password on commandline is not supported...");
        client.authPassword(config.getUserName(), config.getPassword());
      }
    }

    client.useCompression();
    log.info("Socket port {}", client.getSocket().getPort());
    return client;
  }

  private List<KeyProvider> getKeyProviders(SshSessionConfig config) {
    List<KeyProvider> keyProviders = new LinkedList<>();
    OpenSSHKeyFile openSSHKeyFile = new OpenSSHKeyFile();
    if (config.isKeyLess()) {
      openSSHKeyFile.init(new File(config.getKeyPath()), getPasswordFinder(config.getKeyPassphrase()));
    } else {
      openSSHKeyFile.init(
          new String(getCopyOfKey()), config.getSignedPublicKey(), getPasswordFinder(config.getKeyPassphrase()));
    }
    keyProviders.add(openSSHKeyFile);

    OpenSSHKeyV1KeyFile openSSHKeyV1KeyFile = new OpenSSHKeyV1KeyFile();
    if (config.isKeyLess()) {
      openSSHKeyFile.init(new File(config.getKeyPath()), getPasswordFinder(config.getKeyPassphrase()));
    } else {
      openSSHKeyV1KeyFile.init(
          new String(getCopyOfKey()), config.getSignedPublicKey(), getPasswordFinder(config.getKeyPassphrase()));
    }
    keyProviders.add(openSSHKeyV1KeyFile);

    if (isNotEmpty(config.getKey()) && new String(config.getKey()).contains(" OPENSSH ")) {
      OpenSSHKeyFile openSSHKeyFileUsingRSA = new OpenSSHKeyFile();
      openSSHKeyFileUsingRSA.init(new String(config.getKey()).replaceAll(" OPENSSH ", " RSA "),
          config.getSignedPublicKey(), getPasswordFinder(config.getKeyPassphrase()));
      keyProviders.add(openSSHKeyFileUsingRSA);
    }

    return keyProviders;
  }

  private PasswordFinder getPasswordFinder(char[] keyPassphrase) {
    return isNotEmpty(keyPassphrase) ? new StaticPasswordFinder(new String(keyPassphrase)) : null;
  }

  @Override
  protected SshjExecSession getExecSession(SshConnection sshConnection) throws SshClientException {
    try {
      SSHClient client = ((SshjConnection) sshConnection).getClient();
      return SshjExecSession.builder().session(client.startSession()).build();
    } catch (TransportException e) {
      log.error("Transport exception", e);
      throw new SshjClientException("Transport exception " + e.getMessage(), e);
    } catch (ConnectionException e) {
      log.error("Connection exception", e);
      if (e.getMessage().contains("open failed")) {
        throw new SshjClientException(SSH_RETRY, "Connection exception " + e.getMessage(), e);
      }

      throw new SshjClientException("Connection exception " + e.getMessage(), e);
    }
  }

  @Override
  protected SshjSftpSession getSftpSession(SshConnection sshConnection) throws SshClientException {
    try {
      SSHClient client = ((SshjConnection) sshConnection).getClient();
      return SshjSftpSession.builder().sftpClient(client.newSFTPClient()).build();
    } catch (TransportException e) {
      log.error("Transport exception", e);
      throw new SshjClientException("Transport exception " + e.getMessage(), e);
    } catch (ConnectionException e) {
      log.error("Connection exception", e);
      if (e.getMessage().contains("open failed")) {
        throw new SshjClientException(SSH_RETRY, "Connection exception " + e.getMessage(), e);
      }

      throw new SshjClientException("Connection exception " + e.getMessage(), e);
    } catch (IOException e) {
      log.error("Connection exception", e);
      throw new SshjClientException("Connection exception " + e.getMessage(), e);
    }
  }

  @Override
  protected SshjScpSession getScpSession(SshConnection sshConnection) throws SshClientException {
    SSHClient client = ((SshjConnection) sshConnection).getClient();
    return SshjScpSession.builder().scpFileTransfer(client.newSCPFileTransfer()).build();
  }
}
