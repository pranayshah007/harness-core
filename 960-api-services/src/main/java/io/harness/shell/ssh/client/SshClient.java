/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.shell.ssh.SshUtils.getCacheKey;

import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.exception.SshClientException;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

// external interface; created using SshFactory
@Slf4j
public abstract class SshClient implements AutoCloseable {
  @Getter(AccessLevel.PROTECTED) @Setter(AccessLevel.PROTECTED) private SshSessionConfig sshSessionConfig;
  @Getter(AccessLevel.PROTECTED) @Setter private LogCallback logCallback;
  private final List<SshConnection> connectionCache = new ArrayList<>();

  protected char[] getCopyOfKey() {
    return Arrays.copyOf(sshSessionConfig.getKey(), sshSessionConfig.getKey().length);
  }

  public ExecResponse exec(ExecRequest request) throws SshClientException {
    try {
      SshConnection sshConnection = getCachedConnection(request);
      return execInternal(request, sshConnection);
    } catch (SshClientException se) {
      if (!request.isRetry() && se.getCode() == ErrorCode.SSH_RETRY) {
        log.info("Retrying exec on host {}...", sshSessionConfig.getHost());
        request.setRetry(true);
        return exec(request);
      } else {
        throw se;
      }
    }
  }

  public ScpResponse scpUpload(ScpRequest request) throws SshClientException {
    try {
      SshConnection sshConnection = getCachedConnection(request);
      return scpUploadInternal(request, sshConnection);
    } catch (SshClientException se) {
      if (!request.isRetry() && se.getCode() == ErrorCode.SSH_RETRY) {
        log.info("Retrying scp on host {}...", sshSessionConfig.getHost());
        request.setRetry(true);
        return scpUpload(request);
      } else {
        throw se;
      }
    }
  }

  public SftpResponse sftpDownload(SftpRequest request) throws SshClientException {
    try {
      SshConnection sshConnection = getCachedConnection(request);
      return sftpDownloadInternal(request, sshConnection);
    } catch (SshClientException se) {
      if (!request.isRetry() && se.getCode() == ErrorCode.SSH_RETRY) {
        log.info("Retrying sftp on host {}...", sshSessionConfig.getHost());
        request.setRetry(true);
        return sftpDownload(request);
      } else {
        throw se;
      }
    }
  }

  private SshConnection getCachedConnection(BaseSshRequest request) {
    SshConnection connection;
    if (connectionCache.isEmpty() || request.isRetry()) {
      log.info("No connection found. Create new connection for executionId : {}, hostName: {}",
          getSshSessionConfig().getExecutionId(), getSshSessionConfig().getHost());
      connection = getConnection();
      connectionCache.add(connection);
    } else {
      connection = connectionCache.get(connectionCache.size() - 1);
      try {
        // Unnecessary but required test before session reuse for Jsch.
        // test channel. http://stackoverflow.com/questions/16127200/jsch-how-to-keep-the-session-alive-and-up
        testSession(connection);
      } catch (SshClientException ex) {
        log.info("Test failure. Creating new connection for executionId : {}, hostName: {}",
            getSshSessionConfig().getExecutionId(), getSshSessionConfig().getHost());
        connection = getConnection();
        connectionCache.add(connection);
      }
    }
    return connection;
  }

  protected abstract ScpResponse scpUploadInternal(ScpRequest commandData, SshConnection connection)
      throws SshClientException;

  protected abstract SftpResponse sftpDownloadInternal(SftpRequest commandData, SshConnection connection)
      throws SshClientException;
  protected abstract ExecResponse execInternal(ExecRequest commandData, SshConnection sshConnection)
      throws SshClientException;

  public abstract void testConnection() throws SshClientException;
  public abstract void testSession(SshConnection sshConnection) throws SshClientException;
  public abstract SshConnection getConnection() throws SshClientException;
  protected abstract Object getExecSession(SshConnection sshConnection) throws SshClientException;
  protected abstract Object getSftpSession(SshConnection sshConnection) throws SshClientException;
  protected String getKeyPath() {
    String userhome = System.getProperty("user.home");
    String keyPath = userhome + File.separator + ".ssh" + File.separator + "id_rsa";
    if (sshSessionConfig.getKeyPath() != null) {
      keyPath = sshSessionConfig.getKeyPath();
      keyPath = keyPath.replace("$HOME", userhome);
    }
    return keyPath;
  }
  protected void saveExecutionLog(String line) {
    saveExecutionLog(line, RUNNING);
  }
  protected void saveExecutionLog(String line, CommandExecutionStatus commandExecutionStatus) {
    logCallback.saveExecutionLog(line, INFO, commandExecutionStatus);
  }

  @Override
  public void close() {
    if (isNotEmpty(connectionCache)) {
      for (SshConnection connection : connectionCache) {
        try {
          connection.close();
        } catch (Exception ex) {
          log.error("Failed to close connection object for key {}", getCacheKey(getSshSessionConfig()), ex);
        }
      }
    }
  }
}
