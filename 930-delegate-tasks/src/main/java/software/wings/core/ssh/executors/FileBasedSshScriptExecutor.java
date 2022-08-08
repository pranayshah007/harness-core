/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.INVALID_EXECUTION_ID;

import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.FileBasedSshScriptExecutorHelper;
import io.harness.shell.ScriptExecutionContext;
import io.harness.shell.SshSessionConfig;

import software.wings.delegatetasks.DelegateFileManager;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileBasedSshScriptExecutor extends FileBasedAbstractScriptExecutor {
  protected SshSessionConfig config;

  /**
   * Instantiates a new abstract ssh executor.
   *  @param delegateFileManager the file service
   * @param logCallback          the log service
   */
  @Inject
  public FileBasedSshScriptExecutor(DelegateFileManager delegateFileManager, LogCallback logCallback,
      boolean shouldSaveExecutionLogs, ScriptExecutionContext config) {
    super(delegateFileManager, logCallback, shouldSaveExecutionLogs);
    if (isEmpty(((SshSessionConfig) config).getExecutionId())) {
      throw new WingsException(INVALID_EXECUTION_ID);
    }
    this.config = (SshSessionConfig) config;
  }

  @Override
  public CommandExecutionStatus scpOneFile(String remoteFilePath, AbstractScriptExecutor.FileProvider fileProvider) {
    return FileBasedSshScriptExecutorHelper.scpOneFile(
        remoteFilePath, fileProvider, config, logCallback, shouldSaveExecutionLogs);
  }

  @Override
  public String getAccountId() {
    return config.getAccountId();
  }

  @Override
  public String getCommandUnitName() {
    return config.getCommandUnitName();
  }

  @Override
  public String getAppId() {
    return config.getAppId();
  }

  @Override
  public String getExecutionId() {
    return config.getExecutionId();
  }

  @Override
  public String getHost() {
    return config.getHost();
  }
}
