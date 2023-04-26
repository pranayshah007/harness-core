/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client.jsch;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ERROR_IN_GETTING_CHANNEL_STREAMS;
import static io.harness.eraro.ErrorCode.SSH_CONNECTION_ERROR;
import static io.harness.eraro.ErrorCode.SSH_RETRY;
import static io.harness.eraro.ErrorCode.UNKNOWN_ERROR;
import static io.harness.eraro.ErrorCode.UNKNOWN_EXECUTOR_TYPE_ERROR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.shell.AccessType.USER_PASSWORD;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.SshHelperUtils.normalizeError;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;
import static io.harness.shell.ssh.SshUtils.CHANNEL_IS_NOT_OPENED;
import static io.harness.shell.ssh.SshUtils.CHUNK_SIZE;
import static io.harness.shell.ssh.SshUtils.JSCH_SCP_ALLOWED_BYTES;
import static io.harness.shell.ssh.SshUtils.LINE_BREAK_PATTERN;
import static io.harness.shell.ssh.SshUtils.MAX_BYTES_READ_PER_CHANNEL;
import static io.harness.shell.ssh.SshUtils.SSH_NETWORK_PROXY;
import static io.harness.shell.ssh.SshUtils.SUDO_PASSWORD_PROMPT_PATTERN;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.Misc;
import io.harness.network.Http;
import io.harness.security.EncryptionUtils;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshUserInfo;
import io.harness.shell.ssh.client.SshClient;
import io.harness.shell.ssh.client.SshClientType;
import io.harness.shell.ssh.client.SshConnection;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.exception.JschClientException;
import io.harness.shell.ssh.exception.SshClientException;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;
import io.harness.stream.BoundedInputStream;

import com.google.common.base.Charsets;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class JschClient extends SshClient {
  public JschClient(SshSessionConfig config, LogCallback logCallback) {
    setSshSessionConfig(config);
    setLogCallback(logCallback);
  }

  @Override
  protected ExecResponse execInternal(ExecRequest commandData, SshConnection sshConnection) {
    StringBuilder output = new StringBuilder();
    CommandExecutionStatus commandExecutionStatus = FAILURE;

    try (JschExecSession session = getExecSession(sshConnection)) {
      ChannelExec channel = session.getChannel();
      // Allocate a Pseudo-Terminal.
      channel.setPty(true);
      // InputStream: stdout/stderr of the command. All data arriving in SSH_MSG_CHANNEL_DATA messages from the remote
      // side can be read from this stream.
      // OutputStream: input. All data written to this stream will be sent in SSH_MSG_CHANNEL_DATA messages to the
      // remote side.
      // Both should be called before connect()
      try (OutputStream outputStream = channel.getOutputStream(); InputStream inputStream = channel.getInputStream()) {
        channel.setCommand(commandData.getCommand());
        saveExecutionLog(format("Connecting to %s ....", getSshSessionConfig().getHost()));
        // This will connect + send data to remote
        channel.connect(getSshSessionConfig().getSocketConnectTimeout());
        saveExecutionLog(format("Connection to %s established", getSshSessionConfig().getHost()));
        if (commandData.isDisplayCommand()) {
          saveExecutionLog(format("Executing command %s ...", commandData.getCommand()));
        } else {
          saveExecutionLog("Executing command ...");
        }

        int totalBytesRead = 0;
        byte[] byteBuffer = new byte[1024];
        String text = "";

        while (true) {
          while (inputStream.available() > 0) {
            int numOfBytesRead = inputStream.read(byteBuffer, 0, 1024);
            if (numOfBytesRead < 0) {
              break;
            }
            totalBytesRead += numOfBytesRead;
            if (totalBytesRead >= MAX_BYTES_READ_PER_CHANNEL) {
              // TODO: better error reporting
              throw new JschClientException(UNKNOWN_ERROR);
            }
            String dataReadFromTheStream = new String(byteBuffer, 0, numOfBytesRead, UTF_8);
            output.append(dataReadFromTheStream);

            text += dataReadFromTheStream;
            text = processStreamData(text, false, outputStream);
          }

          if (text.length() > 0) {
            text = processStreamData(text, true, outputStream); // finished reading. update logs
          }

          if (channel.isClosed()) {
            commandExecutionStatus = channel.getExitStatus() == 0 ? SUCCESS : FAILURE;
            return ExecResponse.builder()
                .output(output.toString())
                .exitCode(channel.getExitStatus())
                .status(commandExecutionStatus)
                .build();
          }
          sleep(Duration.ofSeconds(1));
        }
      }
    } catch (JSchException jsch) {
      // check retry
      if (CHANNEL_IS_NOT_OPENED.equals(jsch.getMessage())) {
        throw new JschClientException(ErrorCode.SSH_RETRY, jsch);
      } else {
        handleException(jsch);
        log.error("Command execution failed with error", jsch);
        return ExecResponse.builder().output(output.toString()).exitCode(1).status(commandExecutionStatus).build();
      }
    } catch (SshClientException ex) {
      if (ex.getCode() == SSH_RETRY) {
        throw ex;
      }
      handleException(ex);
      log.error("Command execution failed with error", ex);
      return ExecResponse.builder().output(output.toString()).exitCode(1).status(commandExecutionStatus).build();
    } catch (Exception ex) {
      handleException(ex);
      log.error("Command execution failed with error", ex);
      return ExecResponse.builder().output(output.toString()).exitCode(1).status(commandExecutionStatus).build();
    }
  }

  @Override
  protected SftpResponse sftpDownloadInternal(SftpRequest sftpRequest, SshConnection sshConnection) {
    try (JschSftpSession session = getSftpSession(sshConnection)) {
      ChannelSftp channel = session.getChannel();
      channel.connect(getSshSessionConfig().getSocketConnectTimeout());
      channel.cd(sftpRequest.getDirectory());
      InputStream inputStream = channel.get(sftpRequest.getFileName(), CHUNK_SIZE);
      BoundedInputStream stream = new BoundedInputStream(inputStream);
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8));
      String content = getContent(bufferedReader);
      if (sftpRequest.isCleanup()) {
        try {
          channel.rm(sftpRequest.getDirectory() + sftpRequest.getFileName());
        } catch (SftpException e) {
          log.error("Failed to delete file {}", sftpRequest.getFileName(), e);
        }
      }
      return SftpResponse.builder()
          .content(content)
          .exitCode(channel.getExitStatus())
          .status(SUCCESS)
          .success(true)
          .build();

    } catch (JSchException jsch) {
      if (CHANNEL_IS_NOT_OPENED.equals(jsch.getMessage())) {
        throw new JschClientException(ErrorCode.SSH_RETRY, jsch);
      }
      handleException(jsch);
      log.error("Sftp failed with error", jsch);
      return SftpResponse.builder().exitCode(1).status(FAILURE).success(false).build();

    } catch (SshClientException ex) {
      if (ex.getCode() == SSH_RETRY) {
        throw ex;
      }
      handleException(ex);
      log.error("Command execution failed with error", ex);
      return SftpResponse.builder().exitCode(1).status(FAILURE).success(false).build();
    } catch (SftpException e) {
      log.error(
          "[ScriptSshExecutor]: Exception occurred during reading file from SFTP server due to " + e.getMessage(), e);
      // No such file found error
      if (e.id == 2) {
        saveExecutionLogError(
            "Error while reading variables to process Script Output. Avoid exiting from script early: " + e);
      }
      return SftpResponse.builder().exitCode(1).status(FAILURE).success(false).build();
    } catch (Exception ex) {
      log.error("Exception occurred during reading file from SFTP server due to " + ex.getMessage(), ex);
      return SftpResponse.builder().exitCode(1).status(FAILURE).success(false).build();
    }
  }

  @Override
  public void testConnection() {
    try (JschConnection ignored = getConnection()) {
    } catch (JschClientException ex) {
      log.error("Failed to connect Host. ", ex);
      if (ex.getCode() != null) {
        throw new JschClientException(ex.getCode(), ex.getCode().getDescription(), ex);
      } else {
        throw new JschClientException(ex.getMessage(), ex);
      }
    } catch (Exception exception) {
      log.error("Failed to connect Host. ", exception);
      throw new JschClientException(exception.getMessage(), exception);
    }
  }

  @Override
  public void testSession(SshConnection sshConnection) {
    try (JschExecSession session = getExecSession(sshConnection)) {
      ChannelExec testChannel = session.getChannel();
      testChannel.setCommand("true");
      testChannel.connect(getSshSessionConfig().getSocketConnectTimeout());
      log.info("Session connection test successful");
    } catch (JSchException ex) {
      log.error("Failed to validate Host: ", ex);
      ErrorCode errorCode = normalizeError(ex);
      throw new JschClientException(errorCode, errorCode.getDescription(), ex);
    } catch (Exception exception) {
      log.error("Failed to connect Host. ", exception);
      throw new JschClientException(exception.getMessage(), exception);
    }
  }

  private String getContent(BufferedReader br) throws IOException {
    StringBuilder sb = new StringBuilder();

    try {
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line);
        sb.append('\n');
      }
      return sb.toString();
    } finally {
      try {
        br.close();
      } catch (IOException e) {
        log.error("Failed to close buffer reader", e);
      }
    }
  }

  @Override
  protected ScpResponse scpUploadInternal(ScpRequest request, SshConnection sshConnection) {
    ScpResponse failureResponse = ScpResponse.builder().success(false).exitCode(1).status(FAILURE).build();
    Pair<String, Long> fileInfo = getFileInfo(request);
    if (null == fileInfo) {
      return failureResponse;
    }

    try (JschExecSession session = getExecSession(sshConnection)) {
      ChannelExec channel = session.getChannel();
      String command =
          format("mkdir -p \"%s\" && scp -r -d -t '%s'", request.getRemoteFilePath(), request.getRemoteFilePath());
      channel.setCommand(command);

      try (OutputStream out = channel.getOutputStream(); InputStream in = channel.getInputStream()) {
        saveExecutionLog(format("Connecting to %s ....", getSshSessionConfig().getHost()));
        channel.connect(getSshSessionConfig().getSocketConnectTimeout());

        if (checkAck(in) != 0) {
          saveExecutionLogError("SCP connection initiation failed");
          return failureResponse;
        } else {
          saveExecutionLog(format("Connection to %s established", getSshSessionConfig().getHost()));
        }

        // send "C0644 filesize filename", where filename should not include '/'
        command = "C0644 " + fileInfo.getValue() + " " + fileInfo.getKey() + "\n";

        out.write(command.getBytes(UTF_8));
        out.flush();
        if (checkAck(in) != 0) {
          saveExecutionLogError("SCP connection initiation failed");
          return failureResponse;
        }
        saveExecutionLog("Begin file transfer " + fileInfo.getKey() + " to " + getSshSessionConfig().getHost() + ":"
            + request.getRemoteFilePath());
        logFileSize(fileInfo.getKey(), fileInfo.getValue());
        request.getFileProvider().downloadToStream(out);
        out.write(new byte[1], 0, 1);
        out.flush();

        if (checkAck(in) != 0) {
          saveExecutionLogError(
              "File transfer to " + getSshSessionConfig().getHost() + ":" + request.getRemoteFilePath() + " failed");
          return failureResponse;
        }
        saveExecutionLog(
            "File successfully transferred to " + getSshSessionConfig().getHost() + ":" + request.getRemoteFilePath());
      }
    } catch (IOException | ExecutionException | JSchException ex) {
      if (ex instanceof FileNotFoundException) {
        saveExecutionLogError("File not found");
      } else if (ex instanceof JSchException) {
        log.error("Command execution failed with error", ex);
        if (CHANNEL_IS_NOT_OPENED.equals(ex.getMessage())) {
          throw new JschClientException(ErrorCode.SSH_RETRY, ex);
        } else {
          handleException(ex);
          saveExecutionLogError("Command execution failed with error " + normalizeError((JSchException) ex));
          return failureResponse;
        }
      } else {
        log.error("Command execution failed with error", ex);
        throw new JschClientException(ERROR_IN_GETTING_CHANNEL_STREAMS, ex);
      }
      return failureResponse;
    } catch (SshClientException ex) {
      if (ex.getCode() == SSH_RETRY) {
        throw ex;
      }
      handleException(ex);
      log.error("Command execution failed with error", ex);
      return failureResponse;
    } catch (Exception ex) {
      log.error("Command execution failed with error", ex);
      return failureResponse;
    }

    return ScpResponse.builder().status(SUCCESS).success(true).exitCode(0).build();
  }

  private Pair<String, Long> getFileInfo(ScpRequest commandData) {
    try {
      return commandData.getFileProvider().getInfo();
    } catch (IOException ex) {
      if (ex instanceof FileNotFoundException) {
        saveExecutionLogError("File not found");
      } else {
        throw new JschClientException(ERROR_IN_GETTING_CHANNEL_STREAMS, ex);
      }
    }
    return null;
  }

  private void logFileSize(String filename, long configFileLength) {
    saveExecutionLog(format("Size of file (%s) to be transferred %.2f %s", filename,
        configFileLength > 1024 ? configFileLength / 1024.0 : configFileLength,
        configFileLength > 1024 ? "(KB) KiloBytes" : "(B) Bytes"));
  }

  /**
   * Check ack.
   *
   * @param in the in
   * @return the int
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private int checkAck(InputStream in) throws IOException {
    int b = in.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if (b == 0) {
      return b;
    } else if (b == -1) {
      return b;
    } else { // error or echoed string on session initiation from remote host
      StringBuilder sb = new StringBuilder();
      if (b > 2) {
        sb.append((char) b);
      }

      int c;
      int totalBytesRead = 0;
      do {
        if (in.available() <= 0) {
          break;
        }
        c = in.read();
        if (c == -1) {
          break;
        }
        totalBytesRead++;
        sb.append((char) c);
      } while (c != '\n' && totalBytesRead <= JSCH_SCP_ALLOWED_BYTES);

      if (b <= 2) {
        saveExecutionLogError(sb.toString());
        return 1;
      }
      log.info("Server response {}", sb);
      return 0;
    }
  }

  private String processStreamData(String text, boolean finishedReading, OutputStream outputStream) throws IOException {
    if (text == null || text.length() == 0) {
      return text;
    }

    String[] lines = LINE_BREAK_PATTERN.split(text);
    if (lines.length == 0) {
      return "";
    }

    for (int i = 0; i < lines.length - 1; i++) { // Ignore last line.
      saveExecutionLog(lines[i]);
    }

    String lastLine = lines[lines.length - 1];
    // last line is processed only if it ends with new line char or stream closed
    if (textEndsAtNewLineChar(text, lastLine) || finishedReading) {
      passwordPromptResponder(lastLine, outputStream);
      saveExecutionLog(lastLine);
      return ""; // nothing left to carry over
    }
    return lastLine;
  }

  private void handleException(Exception ex) {
    RuntimeException rethrow = null;
    if (ex instanceof JSchException) {
      saveExecutionLogError("Command execution failed with error " + normalizeError((JSchException) ex));
    } else if (ex instanceof IOException) {
      log.error("Exception in reading InputStream", ex);
    } else if (ex instanceof RuntimeException) {
      rethrow = (RuntimeException) ex;
    }
    int i = 0;
    Throwable t = ex;
    while (t != null && i++ < Misc.MAX_CAUSES) {
      String msg = ExceptionUtils.getMessage(t);
      if (isNotBlank(msg)) {
        saveExecutionLogError(msg);
      }
      t = t instanceof JSchException ? null : t.getCause();
    }
    if (rethrow != null) {
      throw rethrow;
    }
  }

  private boolean textEndsAtNewLineChar(String text, String lastLine) {
    return lastLine.charAt(lastLine.length() - 1) != text.charAt(text.length() - 1);
  }

  private void passwordPromptResponder(String line, OutputStream outputStream) throws IOException {
    if (matchesPasswordPromptPattern(line)) {
      if (getSshSessionConfig().getSudoAppPassword() != null) {
        outputStream.write((new String(getSshSessionConfig().getSudoAppPassword()) + "\n").getBytes(UTF_8));
        outputStream.flush();
      }
    }
  }

  private boolean matchesPasswordPromptPattern(String line) {
    return SUDO_PASSWORD_PROMPT_PATTERN.matcher(line).find();
  }

  @Override
  public JschConnection getConnection() throws SshClientException {
    try {
      Session session = getJschSession();
      return JschConnection.builder().session(session).build();
    } catch (Exception ex) {
      log.error("Failed to get session due to {}", ex.getMessage(), ex);
      throw new JschClientException("Failed to get session due to " + ex.getMessage());
    }
  }

  @Override
  protected JschExecSession getExecSession(SshConnection sshConnection) {
    try {
      ChannelExec execChannel = (ChannelExec) ((JschConnection) sshConnection).getSession().openChannel("exec");
      return JschExecSession.builder().channel(execChannel).build();
    } catch (JSchException jsch) {
      if (CHANNEL_IS_NOT_OPENED.equals(jsch.getMessage())) {
        throw new JschClientException(ErrorCode.SSH_RETRY, jsch);
      }
      throw new JschClientException(SSH_CONNECTION_ERROR, jsch);
    }
  }

  @Override
  protected JschSftpSession getSftpSession(SshConnection sshConnection) {
    try {
      ChannelSftp sftpChannel = (ChannelSftp) ((JschConnection) sshConnection).getSession().openChannel("sftp");
      return JschSftpSession.builder().channel(sftpChannel).build();
    } catch (JSchException jsch) {
      if (CHANNEL_IS_NOT_OPENED.equals(jsch.getMessage())) {
        throw new JschClientException(ErrorCode.SSH_RETRY, jsch);
      }
      throw new JschClientException(SSH_CONNECTION_ERROR, jsch);
    }
  }

  @Override
  protected Object getScpSession(SshConnection sshConnection) throws SshClientException {
    // SCP in JSCH is done via exec channel so this is not required.
    throw new NotImplementedException();
  }

  private Session getJschSession() {
    SshSessionConfig config = getSshSessionConfig();

    try {
      switch (config.getExecutorType()) {
        case PASSWORD_AUTH:
        case KEY_AUTH:
          return getSSHSessionWithRetry(config);
        case BASTION_HOST:
          return getSSHSessionWithJumpbox(config.getBastionHostConfig());
        default:
          throw new JschClientException(
              UNKNOWN_EXECUTOR_TYPE_ERROR, new Throwable("Unknown executor type: " + config.getExecutorType()));
      }
    } catch (JSchException jschEx) {
      throw new JschClientException(normalizeError(jschEx), normalizeError(jschEx).name(), jschEx);
    }
  }

  private Session getSSHSessionWithRetry(SshSessionConfig config) throws JSchException {
    Session session = null;
    int retryCount = 0;
    while (retryCount <= 6 && session == null) {
      try {
        TimeUnit.SECONDS.sleep(1);
        retryCount++;
        session = fetchSSHSession(config, getLogCallback());
      } catch (InterruptedException ie) {
        log.error("exception while fetching ssh session", ie);
        Thread.currentThread().interrupt();
      } catch (JSchException jse) {
        if (retryCount == 6) {
          return fetchSSHSession(config, getLogCallback());
        }
        log.error("Jschexception while SSH connection with retry count {}", retryCount, jse);
      }
    }

    return session;
  }

  private Session fetchSSHSession(SshSessionConfig config, LogCallback logCallback) throws JSchException {
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
      String keyPath = getKeyPath();
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

      final char[] copyOfKey = getCopyOfKey();
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
        final char[] copyOfKey = getCopyOfKey();
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
        ProxyHTTP proxyHTTP = getProxy();
        session.setProxy(proxyHTTP);
      }
    }
    session.connect(config.getSshConnectionTimeout());

    return session;
  }

  private ProxyHTTP getProxy() {
    String host = Http.getProxyHostName();
    String port = Http.getProxyPort();

    ProxyHTTP proxyHTTP = new ProxyHTTP(host, Integer.parseInt(port));
    proxyHTTP.setUserPasswd(Http.getProxyUserName(), Http.getProxyPassword());

    return proxyHTTP;
  }

  public Session getSSHSessionWithJumpbox(SshSessionConfig config) throws JSchException {
    Session jumpboxSession = getSSHSessionWithRetry(config);
    int forwardingPort = jumpboxSession.setPortForwardingL(0, config.getHost(), config.getPort());
    log.info("portforwarding port " + forwardingPort);
    getLogCallback().saveExecutionLog("portforwarding port " + forwardingPort);

    SshSessionConfig newConfig = aSshSessionConfig()
                                     .withUserName(config.getUserName())
                                     .withPassword(config.getPassword())
                                     .withKey(config.getKey())
                                     .withHost("127.0.0.1")
                                     .withPort(forwardingPort)
                                     .withUseSshj(config.isUseSshj())
                                     .withUseSshClient(config.isUseSshClient())
                                     .build();
    return getSSHSessionWithRetry(newConfig);
  }

  @Override
  public SshClientType getType() {
    return SshClientType.JSCH;
  }

  @Override
  public void createDirectoryForScp(ScpRequest request) {
    // not required
  }
}