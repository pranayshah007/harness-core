/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.SshSessionConfig;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * A factory for creating ScriptExecutor objects.
 */
@Singleton
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(CDP)
public class SshExecutorFactory {
  @Inject private DelegateFileManager fileService;
  @Inject private DelegateLogService logService;

  /**
   * Gets the executor.
   *
   * @param sshSessionConfig the executor type
   * @return the executor
   */
  public BaseScriptExecutor getExecutor(SshSessionConfig sshSessionConfig) {
    return new ScriptSshExecutor(getExecutionLogCallback(sshSessionConfig), true, sshSessionConfig);
  }

  public BaseScriptExecutor getExecutor(SshSessionConfig sshSessionConfig, boolean shouldSaveExecutionLogs) {
    return new ScriptSshExecutor(getExecutionLogCallback(sshSessionConfig), shouldSaveExecutionLogs, sshSessionConfig);
  }

  public FileBasedScriptExecutor getFileBasedExecutor(SshSessionConfig sshSessionConfig) {
    return new FileBasedSshScriptExecutor(
        fileService, getExecutionLogCallback(sshSessionConfig), true, sshSessionConfig);
  }

  ExecutionLogCallback getExecutionLogCallback(SshSessionConfig config) {
    return new ExecutionLogCallback(
        logService, config.getAccountId(), config.getAppId(), config.getExecutionId(), config.getCommandUnitName());
  }
}
