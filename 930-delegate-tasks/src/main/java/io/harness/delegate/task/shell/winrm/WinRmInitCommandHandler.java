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
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
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
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.BaseScriptExecutor;
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
  private static final String AZURE_CLI_CHECK_SCRIPT = "az devops -h";

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

    BaseScriptExecutor executor =
        getExecutor(logStreamingTaskClient, commandUnitsProgress, winRmCommandTaskParameters, commandUnit);

    try {
      checkIfDownloadAzureUniversalArtifactSupported(executor, winRmCommandTaskParameters);
      CommandExecutionStatus commandExecutionStatus = executeCommand(executor, winRmCommandTaskParameters, commandUnit);
      closeLogStream(executor.getLogCallback(), commandExecutionStatus);
      return ExecuteCommandResponse.builder().status(commandExecutionStatus).build();
    } catch (Exception e) {
      closeLogStreamWithError(executor.getLogCallback());
      throw e;
    }
  }

  private CommandExecutionStatus executeCommand(
      BaseScriptExecutor executor, WinrmTaskParameters winRmCommandTaskParameters, NgCommandUnit commandUnit) {
    if (winRmCommandTaskParameters.isExecuteOnDelegate()) {
      return executeOnDelegate((AbstractScriptExecutor) executor);
    } else {
      return executeOnRemote((WinRmExecutor) executor, commandUnit);
    }
  }

  @NotNull
  private CommandExecutionStatus executeOnDelegate(AbstractScriptExecutor executor, String script) {
    ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(script, Collections.emptyList());
    return getStatus(executeCommandResponse);
  }

  @NotNull
  private CommandExecutionStatus executeOnDelegate(AbstractScriptExecutor executor) {
    return executeOnDelegate(executor, getInitCommand("/tmp"));
  }

  private CommandExecutionStatus executeOnRemote(WinRmExecutor executor, String script) {
    return executor.executeCommandString(script);
  }

  private CommandExecutionStatus executeOnRemote(WinRmExecutor executor, NgCommandUnit commandUnit) {
    return executeOnRemote(executor, getInitCommand(getWorkingDir(commandUnit.getDestinationPath())));
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

  private void checkIfDownloadAzureUniversalArtifactSupported(
      BaseScriptExecutor executor, WinrmTaskParameters winRmCommandTaskParameters) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = winRmCommandTaskParameters.getArtifactDelegateConfig();
    if (artifactDelegateConfig instanceof AzureArtifactDelegateConfig) {
      AzureArtifactDelegateConfig azureArtifactDelegateConfig = (AzureArtifactDelegateConfig) artifactDelegateConfig;
      if (AzureArtifactsProtocolType.upack.name().equals(azureArtifactDelegateConfig.getPackageType())) {
        for (NgCommandUnit cu : winRmCommandTaskParameters.getCommandUnits()) {
          if (NGCommandUnitType.DOWNLOAD_ARTIFACT.equals(cu.getCommandUnitType())) {
            checkIfAzureCliInstalled(executor, winRmCommandTaskParameters);
            break;
          }
        }
      }
    }
  }

  private void checkIfAzureCliInstalled(BaseScriptExecutor executor, WinrmTaskParameters winRmCommandTaskParameters) {
    final CommandExecutionStatus status;
    if (winRmCommandTaskParameters.isExecuteOnDelegate()) {
      status = executeOnDelegate((AbstractScriptExecutor) executor, AZURE_CLI_CHECK_SCRIPT);
    } else {
      status = executeOnRemote((WinRmExecutor) executor, AZURE_CLI_CHECK_SCRIPT);
    }

    if (!CommandExecutionStatus.SUCCESS.equals(status)) {
      throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.AZURE_CLI_INSTALLATION_CHECK_HINT,
          SshExceptionConstants.AZURE_CLI_INSTALLATION_CHECK_EXPLANATION,
          new SshCommandExecutionException(SshExceptionConstants.AZURE_CLI_INSTALLATION_CHECK_FAILED));
    }
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
