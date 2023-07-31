/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.ERROR_IN_GETTING_CHANNEL_STREAMS;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.shell.ScriptSshExecutor.CHANNEL_IS_NOT_OPENED;
import static io.harness.shell.SshHelperUtils.checkAndSaveExecutionLogErrorFunction;
import static io.harness.shell.SshHelperUtils.checkAndSaveExecutionLogFunction;
import static io.harness.shell.SshHelperUtils.normalizeError;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SshRetryableException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.shell.ssh.SshClientManager;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
public class FileBasedSshScriptExecutorHelper {
  private static final int ALLOWED_BYTES = 1024 * 1024; // 1MB

  public static CommandExecutionStatus scpOneFile(String remoteFilePath,
      AbstractScriptExecutor.FileProvider fileProvider, SshSessionConfig config, LogCallback logCallback,
      boolean shouldSaveExecutionLogs) {
    if (config.isUseSshClient() || config.isVaultSSH()) {
      if (!shouldSaveExecutionLogs) {
        logCallback = new NoopExecutionCallback();
      }
      ScpResponse scpResponse = SshClientManager.scpUpload(
          ScpRequest.builder().fileProvider(fileProvider).remoteFilePath(remoteFilePath).build(), config, logCallback);
      return scpResponse.getStatus();
    } else {
      Consumer<String> saveExecutionLog = checkAndSaveExecutionLogFunction(logCallback, shouldSaveExecutionLogs);
      Consumer<String> saveExecutionLogError =
          checkAndSaveExecutionLogErrorFunction(logCallback, shouldSaveExecutionLogs);
      try {
        return scpOneFile(
            remoteFilePath, fileProvider, false, config, logCallback, saveExecutionLog, saveExecutionLogError);
      } catch (SshRetryableException ex) {
        log.info("As MaxSessions limit reached, fetching new session for executionId: {}, hostName: {}",
            config.getExecutionId(), config.getHost());
        saveExecutionLog.accept(format("Retry connecting to %s ....", config.getHost()));
        return scpOneFile(
            remoteFilePath, fileProvider, true, config, logCallback, saveExecutionLog, saveExecutionLogError);
      }
    }
  }

  @NotNull
  private static CommandExecutionStatus scpOneFile(String remoteFilePath,
      AbstractScriptExecutor.FileProvider fileProvider, boolean isRetry, SshSessionConfig config,
      LogCallback logCallback, Consumer<String> saveExecutionLog, Consumer<String> saveExecutionLogError) {
    CommandExecutionStatus commandExecutionStatus = FAILURE;
    Channel channel = null;
    try {
      Pair<String, Long> fileInfo = fileProvider.getInfo();
      String command = format("mkdir -p \"%s\" && scp -r -d -t '%s'", remoteFilePath, remoteFilePath);
      Session session;
      if (isRetry) {
        session = SshSessionManager.getSimplexSession(config, logCallback);
      } else {
        session = SshSessionManager.getCachedSession(config, logCallback);
      }
      channel = session.openChannel("exec");
      ((ChannelExec) channel).setCommand(command);

      // get I/O streams for remote scp
      try (OutputStream out = channel.getOutputStream(); InputStream in = channel.getInputStream()) {
        saveExecutionLog.accept(format("Connecting to %s ....", config.getHost()));
        channel.connect(config.getSocketConnectTimeout());

        if (checkAck(in, saveExecutionLogError) != 0) {
          saveExecutionLogError.accept("SCP connection initiation failed");
          return FAILURE;
        } else {
          saveExecutionLog.accept(format("Connection to %s established", config.getHost()));
        }

        // send "C0644 filesize filename", where filename should not include '/'
        command = "C0644 " + fileInfo.getValue() + " " + fileInfo.getKey() + "\n";

        out.write(command.getBytes(UTF_8));
        out.flush();
        if (checkAck(in, saveExecutionLogError) != 0) {
          saveExecutionLogError.accept("SCP connection initiation failed");
          return commandExecutionStatus;
        }
        saveExecutionLog.accept(
            "Begin file transfer " + fileInfo.getKey() + " to " + config.getHost() + ":" + remoteFilePath);
        logFileSize(saveExecutionLog, fileInfo.getKey(), fileInfo.getValue());
        fileProvider.downloadToStream(out);
        out.write(new byte[1], 0, 1);
        out.flush();

        if (checkAck(in, saveExecutionLogError) != 0) {
          saveExecutionLogError.accept("File transfer to " + config.getHost() + ":" + remoteFilePath + " failed");
          return commandExecutionStatus;
        }
        commandExecutionStatus = SUCCESS;
        saveExecutionLog.accept("File successfully transferred to " + config.getHost() + ":" + remoteFilePath);
        channel.disconnect();
      }
    } catch (IOException | ExecutionException | JSchException ex) {
      if (ex instanceof FileNotFoundException) {
        saveExecutionLogError.accept("File not found");
      } else if (ex instanceof JSchException) {
        log.error("Command execution failed with error", ex);
        if (!isRetry && CHANNEL_IS_NOT_OPENED.equals(ex.getMessage())) {
          throw new SshRetryableException(ex);
        } else {
          saveExecutionLogError.accept("Command execution failed with error " + normalizeError((JSchException) ex));
        }
      } else {
        throw new WingsException(ERROR_IN_GETTING_CHANNEL_STREAMS, ex);
      }
      return commandExecutionStatus;
    } finally {
      if (channel != null && !channel.isClosed()) {
        log.info("Disconnect channel if still open post execution command");
        channel.disconnect();
      }
    }
    return commandExecutionStatus;
  }

  /**
   * Check ack.
   *
   * @param in the in
   * @param saveExecutionLogError the saveExecutionLogError
   * @return the int
   * @throws IOException Signals that an I/O exception has occurred.
   */
  static int checkAck(InputStream in, Consumer<String> saveExecutionLogError) throws IOException {
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
      } while (c != '\n' && totalBytesRead <= ALLOWED_BYTES);

      if (b <= 2) {
        saveExecutionLogError.accept(sb.toString());
        return 1;
      }
      log.info("Server response {}", sb);
      return 0;
    }
  }

  private static void logFileSize(Consumer<String> saveExecutionLog, String filename, long configFileLength) {
    saveExecutionLog.accept(format("Size of file (%s) to be transferred %.2f %s", filename,
        configFileLength > 1024 ? configFileLength / 1024.0 : configFileLength,
        configFileLength > 1024 ? "(KB) KiloBytes" : "(B) Bytes"));
  }
}
