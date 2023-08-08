/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;
import static io.harness.delegate.task.shell.winrm.WinRmCommandConstants.SESSION_TIMEOUT;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getWorkingDir;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.shell.winrm.WinRmConfigAuthEnhancer;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import software.wings.core.winrm.executors.WinRmExecutor;

import com.google.inject.Inject;
import java.net.ConnectException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@Slf4j
public class WinRmShellScriptTaskNG extends AbstractDelegateRunnableTask {
  public static final String COMMAND_UNIT = ShellScriptTaskNG.COMMAND_UNIT;
  public static final String INIT_UNIT = "Initialize";

  @Inject private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Inject private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Inject private ShellExecutorFactoryNG shellExecutorFactory;

  public WinRmShellScriptTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    WinRmShellScriptTaskParametersNG shellScriptTaskParameters = (WinRmShellScriptTaskParametersNG) parameters;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    try {
      if (shellScriptTaskParameters.isExecuteOnDelegate()) {
        ShellScriptTaskResponseNG initStatus = executeInitOnDelegate(shellScriptTaskParameters, commandUnitsProgress);
        if (CommandExecutionStatus.FAILURE.equals(initStatus.getStatus())) {
          return initStatus;
        }
        return executeCommandOnDelegate(shellScriptTaskParameters, commandUnitsProgress);
      } else {
        ShellScriptTaskResponseNG initStatus =
            executeInit(shellScriptTaskParameters, commandUnitsProgress, this.getLogStreamingTaskClient());

        if (CommandExecutionStatus.FAILURE.equals(initStatus.getStatus())) {
          return initStatus;
        }
        return executeCommand(shellScriptTaskParameters, commandUnitsProgress, this.getLogStreamingTaskClient());
      }
    } catch (Exception e) {
      log.error("PowerShell Script Failed to execute.", e);
      return ShellScriptTaskResponseNG.builder()
          .status(CommandExecutionStatus.FAILURE)
          .errorMessage("PowerShell script failed to execute. Reason: " + getReasonMessage(e))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    }
  }

  private String getReasonMessage(Exception e) {
    if (ExceptionUtils.cause(ConnectException.class, e) != null) {
      return ExceptionUtils.cause(ConnectException.class, e).getMessage();
    }
    return e.getMessage();
  }

  private ShellScriptTaskResponseNG executeInitOnDelegate(
      WinRmShellScriptTaskParametersNG taskParameters, CommandUnitsProgress commandUnitsProgress) {
    final ShellExecutorConfig config = ShellExecutorConfig.builder()
                                           .accountId(taskParameters.getAccountId())
                                           .executionId(taskParameters.getExecutionId())
                                           .commandUnitName(INIT_UNIT)
                                           .workingDirectory("/tmp")
                                           .environment(taskParameters.getEnvironmentVariables())
                                           .scriptType(ScriptType.POWERSHELL)
                                           .build();

    AbstractScriptExecutor executor =
        shellExecutorFactory.getExecutor(config, getLogStreamingTaskClient(), commandUnitsProgress);

    try {
      ExecuteCommandResponse executeCommandResponse =
          executor.executeCommandString(getInitCommand(taskParameters.getWorkingDirectory()), Collections.emptyList());

      updateStatus(commandUnitsProgress, INIT_UNIT, executeCommandResponse);

      return ShellScriptTaskResponseNG.builder()
          .executeCommandResponse(executeCommandResponse)
          .status(getStatus(executeCommandResponse))
          .errorMessage(getErrorMessage(getStatus(executeCommandResponse)))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      executor.getLogCallback().saveExecutionLog("Command finished with status " + CommandExecutionStatus.FAILURE,
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private ShellScriptTaskResponseNG executeCommandOnDelegate(
      WinRmShellScriptTaskParametersNG taskParameters, CommandUnitsProgress commandUnitsProgress) {
    final ShellExecutorConfig config = ShellExecutorConfig.builder()
                                           .accountId(taskParameters.getAccountId())
                                           .executionId(taskParameters.getExecutionId())
                                           .commandUnitName(COMMAND_UNIT)
                                           .workingDirectory(taskParameters.getWorkingDirectory())
                                           .environment(taskParameters.getEnvironmentVariables())
                                           .scriptType(ScriptType.POWERSHELL)
                                           .build();

    AbstractScriptExecutor executor =
        shellExecutorFactory.getExecutor(config, getLogStreamingTaskClient(), commandUnitsProgress);

    try {
      ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
          taskParameters.getScript(), taskParameters.getOutputVars(), taskParameters.getSecretOutputVars(), null);

      updateStatus(commandUnitsProgress, COMMAND_UNIT, executeCommandResponse);

      return ShellScriptTaskResponseNG.builder()
          .executeCommandResponse(executeCommandResponse)
          .status(getStatus(executeCommandResponse))
          .errorMessage(getErrorMessage(getStatus(executeCommandResponse)))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      executor.getLogCallback().saveExecutionLog("Command finished with status " + CommandExecutionStatus.FAILURE,
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private ShellScriptTaskResponseNG executeInit(WinRmShellScriptTaskParametersNG taskParameters,
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient logStreamingTaskClient) {
    WinRmSessionConfigBuilder configBuilder = WinRmSessionConfig.builder()
                                                  .accountId(taskParameters.getAccountId())
                                                  .executionId(taskParameters.getExecutionId())
                                                  .workingDirectory(getWorkingDir(taskParameters.getWorkingDirectory()))
                                                  .commandUnitName(INIT_UNIT)
                                                  .environment(taskParameters.getEnvironmentVariables())
                                                  .hostname(taskParameters.getHost())
                                                  .timeout(SESSION_TIMEOUT)
                                                  .commandParameters(getCommandParameters(taskParameters));

    WinRmSessionConfig config =
        winRmConfigAuthEnhancer.configureAuthentication((WinRmCredentialsSpecDTO) taskParameters.getSshKeySpecDTO(),
            taskParameters.getEncryptionDetails(), configBuilder, taskParameters.isUseWinRMKerberosUniqueCacheFile());

    WinRmExecutor executor = winRmExecutorFactoryNG.getExecutor(config, taskParameters.isDisableCommandEncoding(),
        taskParameters.isWinrmScriptCommandSplit(), logStreamingTaskClient, commandUnitsProgress);

    try {
      CommandExecutionStatus commandExecutionStatus =
          executor.executeCommandString(getInitCommand(taskParameters.getWorkingDirectory()));

      return ShellScriptTaskResponseNG.builder()
          .status(commandExecutionStatus)
          .errorMessage(getErrorMessage(commandExecutionStatus))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      executor.getLogCallback().saveExecutionLog("Command finished with status " + CommandExecutionStatus.FAILURE,
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private ShellScriptTaskResponseNG executeCommand(WinRmShellScriptTaskParametersNG taskParameters,
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient logStreamingTaskClient) {
    WinRmSessionConfigBuilder configBuilder = WinRmSessionConfig.builder()
                                                  .accountId(taskParameters.getAccountId())
                                                  .executionId(taskParameters.getExecutionId())
                                                  .workingDirectory(getWorkingDir(taskParameters.getWorkingDirectory()))
                                                  .commandUnitName(COMMAND_UNIT)
                                                  .environment(taskParameters.getEnvironmentVariables())
                                                  .hostname(taskParameters.getHost())
                                                  .timeout(SESSION_TIMEOUT)
                                                  .commandParameters(getCommandParameters(taskParameters));

    WinRmSessionConfig config =
        winRmConfigAuthEnhancer.configureAuthentication((WinRmCredentialsSpecDTO) taskParameters.getSshKeySpecDTO(),
            taskParameters.getEncryptionDetails(), configBuilder, taskParameters.isUseWinRMKerberosUniqueCacheFile());

    WinRmExecutor executor = winRmExecutorFactoryNG.getExecutor(config, taskParameters.isDisableCommandEncoding(),
        taskParameters.isWinrmScriptCommandSplit(), logStreamingTaskClient, commandUnitsProgress);

    try {
      ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
          taskParameters.getScript(), taskParameters.getOutputVars(), taskParameters.getSecretOutputVars(), null);

      return ShellScriptTaskResponseNG.builder()
          .executeCommandResponse(executeCommandResponse)
          .status(getStatus(executeCommandResponse))
          .errorMessage(getErrorMessage(getStatus(executeCommandResponse)))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      executor.getLogCallback().saveExecutionLog("Command finished with status " + CommandExecutionStatus.FAILURE,
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private List<WinRmCommandParameter> getCommandParameters(WinRmShellScriptTaskParametersNG taskParameters) {
    List<WinRmCommandParameter> commandParameters = taskParameters.getSshKeySpecDTO() instanceof WinRmCredentialsSpecDTO
        ? ((WinRmCredentialsSpecDTO) taskParameters.getSshKeySpecDTO()).getParameters()
        : Collections.emptyList();
    return commandParameters == null ? Collections.emptyList() : commandParameters;
  }

  private CommandExecutionStatus getStatus(ExecuteCommandResponse executeCommandResponse) {
    if (executeCommandResponse == null) {
      return CommandExecutionStatus.FAILURE;
    }
    if (executeCommandResponse.getStatus() == null) {
      return CommandExecutionStatus.FAILURE;
    }
    return executeCommandResponse.getStatus();
  }

  private String getErrorMessage(CommandExecutionStatus status) {
    switch (status) {
      case QUEUED:
        return "Shell Script execution queued.";
      case FAILURE:
        return "Shell Script execution failed. Please check execution logs.";
      case RUNNING:
        return "Shell Script execution running.";
      case SKIPPED:
        return "Shell Script execution skipped.";
      case SUCCESS:
      default:
        return "";
    }
  }

  private void updateStatus(
      CommandUnitsProgress commandUnitsProgress, String commandUnit, ExecuteCommandResponse executeCommandResponse) {
    CommandUnitProgress existingUnitProgress = commandUnitsProgress.getCommandUnitProgressMap().get(commandUnit);
    if (existingUnitProgress != null) {
      CommandUnitProgress updatedUnitProgress =
          CommandUnitProgress.builder()
              .status(
                  executeCommandResponse != null ? getStatus(executeCommandResponse) : CommandExecutionStatus.FAILURE)
              .startTime(existingUnitProgress.getStartTime())
              .endTime(existingUnitProgress.getEndTime() != 0L ? existingUnitProgress.getEndTime()
                                                               : Instant.now().toEpochMilli())
              .build();

      commandUnitsProgress.getCommandUnitProgressMap().put(commandUnit, updatedUnitProgress);
    }
  }

  private String getInitCommand(String workingDirectory) {
    String script = "echo $PSVersionTable%n"
        + "$RUNTIME_PATH=[System.Environment]::ExpandEnvironmentVariables(\"%s\")%n"
        + "if(!(Test-Path \"$RUNTIME_PATH\"))%n"
        + "{%n"
        + "    New-Item -ItemType Directory -Path \"$RUNTIME_PATH\"%n"
        + "    Write-Host \"$RUNTIME_PATH Folder Created Successfully.\"%n"
        + "}%n"
        + "else%n"
        + "{%n"
        + "    Write-Host \"${RUNTIME_PATH} Folder already exists.\"%n"
        + "}";

    return String.format(script, getWorkingDir(workingDirectory));
  }
}
