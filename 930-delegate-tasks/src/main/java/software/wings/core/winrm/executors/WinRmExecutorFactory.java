/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.winrm.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.winrm.DefaultWinRmExecutor;
import io.harness.delegate.task.winrm.WinRmSessionConfig;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.core.ssh.executors.FileBasedWinRmExecutor;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class WinRmExecutorFactory {
  @Inject private DelegateLogService logService;
  @Inject private DelegateFileManager delegateFileManager;

  public WinRmExecutor getExecutor(WinRmSessionConfig config, boolean disableCommandEncoding) {
    return new DefaultWinRmExecutor(getExecutionLogCallback(config), true, config, disableCommandEncoding);
  }

  public WinRmExecutor getExecutor(
      WinRmSessionConfig config, boolean disableCommandEncoding, boolean shouldSaveExecutionLogs) {
    return new DefaultWinRmExecutor(
        getExecutionLogCallback(config), shouldSaveExecutionLogs, config, disableCommandEncoding);
  }

  public FileBasedWinRmExecutor getFiledBasedWinRmExecutor(WinRmSessionConfig config, boolean disableCommandEncoding) {
    return new FileBasedWinRmExecutor(
        getExecutionLogCallback(config), delegateFileManager, true, config, disableCommandEncoding);
  }

  ExecutionLogCallback getExecutionLogCallback(WinRmSessionConfig config) {
    return new ExecutionLogCallback(
        logService, config.getAccountId(), config.getAppId(), config.getExecutionId(), config.getCommandUnitName());
  }
}
