/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getShellExecutorConfig;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.exception.InvalidRequestException;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutorConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(CDP)
@Singleton
public class WinRmCleanupCommandHandler implements CommandHandler {
  private final ShellExecutorFactoryNG shellExecutorFactory;

  @Inject
  public WinRmCleanupCommandHandler(ShellExecutorFactoryNG shellExecutorFactoryNG) {
    this.shellExecutorFactory = shellExecutorFactoryNG;
  }

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof WinrmTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof NgCleanupCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    ShellExecutorConfig config = getShellExecutorConfig((WinrmTaskParameters) parameters, commandUnit);
    AbstractScriptExecutor executor =
        shellExecutorFactory.getExecutor(config, logStreamingTaskClient, commandUnitsProgress, true);
    closeLogStreamWithSuccess(executor.getLogCallback());
    return ExecuteCommandResponse.builder().status(SUCCESS).build();
  }
}