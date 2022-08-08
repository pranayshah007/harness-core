/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.shell.ssh.ArtifactCommandUnitHandler;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.FileBasedSshScriptExecutorHelper;
import io.harness.shell.SshSessionConfig;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class FileBasedSshScriptExecutorNG extends FileBasedAbstractScriptExecutorNG {
  private SshSessionConfig config;

  public FileBasedSshScriptExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs, SshSessionConfig config,
      Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers) {
    super(logCallback, shouldSaveExecutionLogs, artifactCommandHandlers);
    this.config = config;
  }

  @Override
  public CommandExecutionStatus scpOneFile(String remoteFilePath, AbstractScriptExecutor.FileProvider fileProvider) {
    CommandExecutionStatus status = FileBasedSshScriptExecutorHelper.scpOneFile(
        remoteFilePath, fileProvider, config, logCallback, shouldSaveExecutionLogs);
    logCallback.saveExecutionLog("Command finished with status " + status, LogLevel.INFO, status);
    return status;
  }
}
