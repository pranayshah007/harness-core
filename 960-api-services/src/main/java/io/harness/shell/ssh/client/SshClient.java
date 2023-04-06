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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

// external interface; created using SshFactory
@Slf4j
public abstract class SshClient implements AutoCloseable {
  @Getter(AccessLevel.PROTECTED) @Setter(AccessLevel.PROTECTED) private SshSessionConfig sshSessionConfig;
  @Getter(AccessLevel.PROTECTED) @Setter private LogCallback logCallback;
  private final List<SshConnection> connectionCache = new LinkedList<>();

  protected char[] getCopyOfKey() {
    return Arrays.copyOf(sshSessionConfig.getKey(), sshSessionConfig.getKey().length);
  }

  public ExecResponse exec(ExecRequest commandData) throws SshClientException {
    SshConnection sshConnection = getCachedConnection();
    return execInternal(commandData, sshConnection);
  }

  public ScpResponse scpUpload(ScpRequest commandData) throws SshClientException {
    SshConnection sshConnection = getCachedConnection();
    return scpUploadInternal(commandData, sshConnection);
  }

  public SftpResponse sftpDownload(SftpRequest commandData) throws SshClientException {
    SshConnection sshConnection = getCachedConnection();
    return sftpDownloadInternal(commandData, sshConnection);
  }

  private synchronized SshConnection getCachedConnection() {
    if (connectionCache.isEmpty()) {
      log.info("No connection found. Create new connection for executionId : {}, hostName: {}",
          getSshSessionConfig().getExecutionId(), getSshSessionConfig().getHost());
      connectionCache.add(getConnection());
    }

    SshConnection sshConnection = connectionCache.get(connectionCache.size() - 1);

    try {
      testSession(sshConnection);
    } catch (SshClientException ex) {
      log.info("Test failure. Creating new connection for executionId : {}, hostName: {}",
          getSshSessionConfig().getExecutionId(), getSshSessionConfig().getHost());
      sshConnection = getConnection();
      connectionCache.add(sshConnection);
    }
    return sshConnection;
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
