/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.delegate.task.shell.winrm.WinRmUtils.getShellExecutorConfig;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getStatus;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getWinRmSessionConfig;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutorConfig;

import software.wings.core.winrm.executors.WinRmExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class WinRmScriptCommandHandler implements CommandHandler {
  private final WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  private final WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  private final ShellExecutorFactoryNG shellExecutorFactory;

  @Inject
  public WinRmScriptCommandHandler(WinRmExecutorFactoryNG winRmExecutorFactoryNG,
      WinRmConfigAuthEnhancer winRmConfigAuthEnhancer, ShellExecutorFactoryNG shellExecutorFactory) {
    this.winRmExecutorFactoryNG = winRmExecutorFactoryNG;
    this.winRmConfigAuthEnhancer = winRmConfigAuthEnhancer;
    this.shellExecutorFactory = shellExecutorFactory;
  }

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof WinrmTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }
    WinrmTaskParameters winRmCommandTaskParameters = (WinrmTaskParameters) parameters;
    if (!(commandUnit instanceof ScriptCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    return executeCommand(
        logStreamingTaskClient, commandUnitsProgress, winRmCommandTaskParameters, (ScriptCommandUnit) commandUnit);
  }

  private ExecuteCommandResponse executeCommand(ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress, WinrmTaskParameters winRmCommandTaskParameters,
      ScriptCommandUnit scriptCommandUnit) {
    BaseScriptExecutor executor =
        getExecutor(logStreamingTaskClient, commandUnitsProgress, winRmCommandTaskParameters, scriptCommandUnit);

    try {
      final ExecuteCommandResponse executeCommandResponse;
      if (winRmCommandTaskParameters.isExecuteOnDelegate()) {
        executeCommandResponse =
            executeOnDelegate(winRmCommandTaskParameters, (AbstractScriptExecutor) executor, scriptCommandUnit);
      } else {
        executeCommandResponse =
            executeOnRemote(winRmCommandTaskParameters, (WinRmExecutor) executor, scriptCommandUnit);
      }

      closeLogStream(executor.getLogCallback(), getStatus(executeCommandResponse));
      return executeCommandResponse;
    } catch (Exception e) {
      closeLogStreamWithError(executor.getLogCallback());
      throw e;
    }
  }

  private ExecuteCommandResponse executeOnDelegate(
      WinrmTaskParameters taskParameters, AbstractScriptExecutor executor, ScriptCommandUnit scriptCommandUnit) {
    return executor.executeCommandString(scriptCommandUnit.getCommand(), taskParameters.getOutputVariables(),
        taskParameters.getSecretOutputVariables(), null);
  }

  private ExecuteCommandResponse executeOnRemote(
      WinrmTaskParameters winRmCommandTaskParameters, WinRmExecutor executor, ScriptCommandUnit commandUnit) {
    return executor.executeCommandString(commandUnit.getCommand(), winRmCommandTaskParameters.getOutputVariables(),
        winRmCommandTaskParameters.getSecretOutputVariables(), null);
  }

  private BaseScriptExecutor getExecutor(ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress, WinrmTaskParameters taskParameters, NgCommandUnit commandUnit) {
    if (taskParameters.isExecuteOnDelegate()) {
      ShellExecutorConfig config = getShellExecutorConfig(taskParameters, commandUnit);
      return shellExecutorFactory.getExecutor(config, logStreamingTaskClient, commandUnitsProgress, true);
    }

    WinRmSessionConfig config = getWinRmSessionConfig(commandUnit, taskParameters, winRmConfigAuthEnhancer);

    return winRmExecutorFactoryNG.getExecutor(config, taskParameters.isDisableWinRMCommandEncodingFFSet(),
        taskParameters.isWinrmScriptCommandSplit(), logStreamingTaskClient, commandUnitsProgress);
  }
}
