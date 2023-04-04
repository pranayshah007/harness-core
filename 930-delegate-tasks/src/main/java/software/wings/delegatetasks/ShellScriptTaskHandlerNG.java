/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.delegate.task.shell.SshExecutorFactoryNG;
import io.harness.k8s.K8sConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionManager;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShellScriptTaskHandlerNG {
  public static final String COMMAND_UNIT = "Execute";

  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  @Inject private SshExecutorFactoryNG sshExecutorFactoryNG;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;

  public DelegateResponseData handle(TaskParameters parameters, ILogStreamingTaskClient iLogStreamingTaskClient) {
    ShellScriptTaskParametersNG taskParameters = (ShellScriptTaskParametersNG) parameters;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    if (taskParameters.isExecuteOnDelegate()) {
      ShellExecutorConfig shellExecutorConfig = getShellExecutorConfig(taskParameters);
      ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(shellExecutorConfig, commandUnitsProgress);
      ExecuteCommandResponse executeCommandResponse =
          executor.executeCommandString(taskParameters.getScript(), taskParameters.getOutputVars());
      return ShellScriptTaskResponseNG.builder()
          .executeCommandResponse(executeCommandResponse)
          .status(executeCommandResponse.getStatus())
          .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } else {
      try {
        SshSessionConfig sshSessionConfig = getSshSessionConfig(taskParameters);
        ScriptSshExecutor executor = sshExecutorFactoryNG.getExecutor(sshSessionConfig, commandUnitsProgress);
        ExecuteCommandResponse executeCommandResponse =
            executor.executeCommandString(taskParameters.getScript(), taskParameters.getOutputVars());
        return ShellScriptTaskResponseNG.builder()
            .executeCommandResponse(executeCommandResponse)
            .status(executeCommandResponse.getStatus())
            .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      } catch (Exception e) {
        log.error("Bash Script Failed to execute.", e);
        return ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.FAILURE)
            .errorMessage("Bash Script Failed to execute. Reason: " + e.getMessage())
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      } finally {
        SshSessionManager.evictAndDisconnectCachedSession(taskParameters.getExecutionId(), taskParameters.getHost());
      }
    }
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

  private SshSessionConfig getSshSessionConfig(ShellScriptTaskParametersNG taskParameters) {
    SshSessionConfig sshSessionConfig = sshSessionConfigMapper.getSSHSessionConfig(
        taskParameters.getSshKeySpecDTO(), taskParameters.getEncryptionDetails());

    sshSessionConfig.setAccountId(taskParameters.getAccountId());
    sshSessionConfig.setExecutionId(taskParameters.getExecutionId());
    sshSessionConfig.setHost(taskParameters.getHost());
    sshSessionConfig.setWorkingDirectory(taskParameters.getWorkingDirectory());
    sshSessionConfig.setCommandUnitName(COMMAND_UNIT);
    return sshSessionConfig;
  }

  private ShellExecutorConfig getShellExecutorConfig(ShellScriptTaskParametersNG taskParameters) {
    String kubeConfigFileContent = taskParameters.getScript().contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH)
            && taskParameters.getK8sInfraDelegateConfig() != null
        ? containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(
            taskParameters.getK8sInfraDelegateConfig(), taskParameters.getWorkingDirectory())
        : "";

    return ShellExecutorConfig.builder()
        .accountId(taskParameters.getAccountId())
        .executionId(taskParameters.getExecutionId())
        .commandUnitName(COMMAND_UNIT)
        .workingDirectory(taskParameters.getWorkingDirectory())
        .environment(taskParameters.getEnvironmentVariables())
        .kubeConfigContent(kubeConfigFileContent)
        .scriptType(taskParameters.getScriptType())
        .build();
  }
}