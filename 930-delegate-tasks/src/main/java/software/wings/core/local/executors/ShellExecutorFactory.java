/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.local.executors;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.core.ssh.executors.FileBasedProcessScriptExecutor;
import software.wings.core.ssh.executors.FileBasedScriptExecutor;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@TargetModule(HarnessModule._960_API_SERVICES)
public class ShellExecutorFactory {
  @Inject private DelegateLogService logService;
  @Inject private DelegateFileManager fileService;

  public ScriptProcessExecutor getExecutor(ShellExecutorConfig config) {
    return new ScriptProcessExecutor(getExecutionLogCallback(config), true, config);
  }

  public ScriptProcessExecutor getExecutor(ShellExecutorConfig config, boolean shouldSaveExecutionLogs) {
    return new ScriptProcessExecutor(getExecutionLogCallback(config), shouldSaveExecutionLogs, config);
  }

  public FileBasedScriptExecutor getFileBasedExecutor(ShellExecutorConfig config) {
    return new FileBasedProcessScriptExecutor(fileService, getExecutionLogCallback(config), true, config);
  }

  ExecutionLogCallback getExecutionLogCallback(ShellExecutorConfig config) {
    return new ExecutionLogCallback(
        logService, config.getAccountId(), config.getAppId(), config.getExecutionId(), config.getCommandUnitName());
  }
}
