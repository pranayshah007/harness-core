/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.agent;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.INFO;

import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.connection.TestResponse;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

// external interface; created using SshFactory
@Slf4j
public abstract class SshClient {
  @Getter(AccessLevel.PROTECTED) @Setter(AccessLevel.PROTECTED) private SshSessionConfig sshSessionConfig;
  @Getter(AccessLevel.PROTECTED) @Setter(AccessLevel.PROTECTED) private LogCallback logCallback;
  protected static final String SSH_NETWORK_PROXY = "SSH_NETWORK_PROXY";
  protected static final String UUID = generateUuid();
  /**
   * The constant log.
   */
  protected static final int MAX_BYTES_READ_PER_CHANNEL =
      1024 * 1024 * 1024; // TODO: Read from config. 1 GB per channel for now.

  protected static final int CHUNK_SIZE = 512 * 1024; // 512KB
  /**
   * The constant DEFAULT_SUDO_PROMPT_PATTERN.
   */
  protected static final String DEFAULT_SUDO_PROMPT_PATTERN = "^\\[sudo\\] password for .+: .*";
  /**
   * The constant LINE_BREAK_PATTERN.
   */
  protected static final String LINE_BREAK_PATTERN = "\\R+";
  protected Pattern lineBreakPattern = Pattern.compile(LINE_BREAK_PATTERN);
  protected Pattern sudoPasswordPromptPattern = Pattern.compile(DEFAULT_SUDO_PROMPT_PATTERN);
  /**
   * The constant log.
   */
  protected static final String CHANNEL_IS_NOT_OPENED = "channel is not opened.";

  protected char[] getCopyOfKey() {
    return Arrays.copyOf(sshSessionConfig.getKey(), sshSessionConfig.getKey().length);
  }

  public abstract ExecResponse exec(ExecRequest commandData);
  public abstract TestResponse test();
  public abstract ScpResponse scpUpload(ScpRequest commandData);

  public abstract SftpResponse sftpUpload(SftpRequest commandData);

  protected abstract Object getClient();
  protected abstract Object getExecSession(SshConnection sshConnection);
  protected abstract Object getSftpSession(SshConnection sshConnection);
  protected abstract void configureProxy();
  protected String getKeyPath() {
    String userhome = System.getProperty("user.home");
    String keyPath = userhome + File.separator + ".ssh" + File.separator + "id_rsa";
    if (sshSessionConfig.getKeyPath() != null) {
      keyPath = sshSessionConfig.getKeyPath();
      keyPath = keyPath.replace("$HOME", userhome);
    }
    return keyPath;
  }
  protected void init(SshSessionConfig config, LogCallback logCallback) {
    this.sshSessionConfig = config;
    this.logCallback = logCallback;
  }

  protected void saveExecutionLog(String line) {
    saveExecutionLog(line, RUNNING);
  }

  protected void saveExecutionLog(String line, CommandExecutionStatus commandExecutionStatus) {
    logCallback.saveExecutionLog(line, INFO, commandExecutionStatus);
  }
}
