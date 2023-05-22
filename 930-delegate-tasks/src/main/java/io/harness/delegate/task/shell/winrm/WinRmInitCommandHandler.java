/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getShellExecutorConfig;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getStatus;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getWinRmSessionConfig;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getWorkingDir;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutorConfig;

import software.wings.core.winrm.executors.WinRmExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Singleton
public class WinRmInitCommandHandler implements CommandHandler {
  @Inject private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Inject private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Inject private ShellExecutorFactoryNG shellExecutorFactory;

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof WinrmTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof NgInitCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    WinrmTaskParameters winRmCommandTaskParameters = (WinrmTaskParameters) parameters;

    for (NgCommandUnit cu : parameters.getCommandUnits()) {
      if (NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType())) {
        ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) cu;
        scriptCommandUnit.setCommand(scriptCommandUnit.getScript());
      }
    }

    CommandExecutionStatus commandExecutionStatus =
        executeCommand(logStreamingTaskClient, commandUnitsProgress, winRmCommandTaskParameters, commandUnit);

    return ExecuteCommandResponse.builder().status(commandExecutionStatus).build();
  }

  private CommandExecutionStatus executeCommand(ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress, WinrmTaskParameters winRmCommandTaskParameters,
      NgCommandUnit commandUnit) {
    if (winRmCommandTaskParameters.isExecuteOnDelegate()) {
      return executeOnDelegate(logStreamingTaskClient, commandUnitsProgress, winRmCommandTaskParameters, commandUnit);
    } else {
      return executeOnRemote(logStreamingTaskClient, commandUnitsProgress, winRmCommandTaskParameters, commandUnit);
    }
  }

  @NotNull
  private CommandExecutionStatus executeOnDelegate(ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress, WinrmTaskParameters taskParameters, NgCommandUnit commandUnit) {
    ShellExecutorConfig config = getShellExecutorConfig(taskParameters, commandUnit);

    AbstractScriptExecutor executor =
        shellExecutorFactory.getExecutor(config, logStreamingTaskClient, commandUnitsProgress, true);
    try {
      ExecuteCommandResponse executeCommandResponse =
          executor.executeCommandString(getInitCommand("/tmp"), Collections.emptyList());

      final CommandExecutionStatus status = getStatus(executeCommandResponse);

      executor.getLogCallback().saveExecutionLog("Command finished with status " + status, LogLevel.INFO, status);

      return status;
    } catch (Exception e) {
      executor.getLogCallback().saveExecutionLog("Command finished with status " + CommandExecutionStatus.FAILURE,
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private CommandExecutionStatus executeOnRemote(ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress, WinrmTaskParameters winRmCommandTaskParameters,
      NgCommandUnit commandUnit) {
    WinRmSessionConfig config = getWinRmSessionConfig(commandUnit, winRmCommandTaskParameters, winRmConfigAuthEnhancer);

    WinRmExecutor executor =
        winRmExecutorFactoryNG.getExecutor(config, winRmCommandTaskParameters.isDisableWinRMCommandEncodingFFSet(),
            winRmCommandTaskParameters.isWinrmScriptCommandSplit(), logStreamingTaskClient, commandUnitsProgress);

    return executor.executeCommandString(getInitCommand(getWorkingDir(commandUnit.getDestinationPath())));
  }

  private String getInitCommand(String workingDirectory) {
    String script = "$RUNTIME_PATH=[System.Environment]::ExpandEnvironmentVariables(\"%s\")%n"
        + "if(!(Test-Path \"$RUNTIME_PATH\"))%n"
        + "{%n"
        + "    New-Item -ItemType Directory -Path \"$RUNTIME_PATH\"%n"
        + "    Write-Host \"$RUNTIME_PATH Folder Created Successfully.\"%n"
        + "}%n"
        + "else%n"
        + "{%n"
        + "    Write-Host \"${RUNTIME_PATH} Folder already exists.\"%n"
        + "}";

    return String.format(script, workingDirectory);
  }
}
