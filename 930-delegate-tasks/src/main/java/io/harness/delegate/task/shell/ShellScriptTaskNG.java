/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionManager;
import io.harness.shell.ssh.SshClientManager;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class ShellScriptTaskNG extends AbstractDelegateRunnableTask {
  public static final String COMMAND_UNIT = "Execute";

  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  @Inject private SshExecutorFactoryNG sshExecutorFactoryNG;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;

  public ShellScriptTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ShellScriptTaskParametersNG taskParameters = (ShellScriptTaskParametersNG) parameters;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    if (taskParameters.isExecuteOnDelegate()) {
      try {
        ShellExecutorConfig shellExecutorConfig = getShellExecutorConfig(taskParameters);
        ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(
            shellExecutorConfig, this.getLogStreamingTaskClient(), commandUnitsProgress);
        // TODO: check later
        // if (taskParameters.isLocalOverrideFeatureFlag()) {
        //   taskParameters.setScript(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(taskParameters.getScript()));
        // }
        ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
            taskParameters.getScript(), taskParameters.getOutputVars(), taskParameters.getSecretOutputVars(), null);
        return ShellScriptTaskResponseNG.builder()
            .executeCommandResponse(executeCommandResponse)
            .status(executeCommandResponse.getStatus())
            .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      } catch (Exception e) {
        log.error("Shell Script Failed to execute.", e);
        return ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.FAILURE)
            .errorMessage("Shell Script Failed to execute. Reason: " + e.getMessage())
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      } finally {
        if (isNotEmpty(taskParameters.getExecutionId()) && isNotEmpty(taskParameters.getHost())) {
          SshSessionManager.evictAndDisconnectCachedSession(taskParameters.getExecutionId(), taskParameters.getHost());
          SshClientManager.evictCacheAndDisconnect(taskParameters.getExecutionId(), taskParameters.getHost());
        }
      }
    } else {
      try {
        SshSessionConfig sshSessionConfig = getSshSessionConfig(taskParameters);
        ScriptSshExecutor executor =
            sshExecutorFactoryNG.getExecutor(sshSessionConfig, this.getLogStreamingTaskClient(), commandUnitsProgress);
        ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
            taskParameters.getScript(), taskParameters.getOutputVars(), taskParameters.getSecretOutputVars(), null);
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
        SshClientManager.evictCacheAndDisconnect(taskParameters.getExecutionId(), taskParameters.getHost());
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
    sshSessionConfig.setEnvVariables(taskParameters.getEnvironmentVariables());
    return sshSessionConfig;
  }

  private ShellExecutorConfig getShellExecutorConfig(ShellScriptTaskParametersNG taskParameters) {
    String kubeConfigFileContent = taskParameters.getScript().contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH)
            && taskParameters.getK8sInfraDelegateConfig() != null
        ? containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(
            taskParameters.getK8sInfraDelegateConfig(), taskParameters.getWorkingDirectory())
        : "";

    char[] serviceAccountKeyFileContent = null;
    if (taskParameters.getK8sInfraDelegateConfig() instanceof GcpK8sInfraDelegateConfig) {
      GcpK8sInfraDelegateConfig gcpK8sInfraDelegateConfig =
          (GcpK8sInfraDelegateConfig) taskParameters.getK8sInfraDelegateConfig();
      GcpConnectorDTO gcpConnectorDTO = gcpK8sInfraDelegateConfig.getGcpConnectorDTO();
      if (gcpConnectorDTO.getCredential().getConfig() instanceof GcpManualDetailsDTO) {
        GcpManualDetailsDTO gcpManualDetailsDTO = (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
        List<EncryptedDataDetail> encryptedDataDetails = gcpK8sInfraDelegateConfig.getEncryptionDataDetails();
        secretDecryptionService.decrypt(gcpManualDetailsDTO, encryptedDataDetails);
        serviceAccountKeyFileContent = gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue();
      }
    }

    return ShellExecutorConfig.builder()
        .accountId(taskParameters.getAccountId())
        .executionId(taskParameters.getExecutionId())
        .commandUnitName(COMMAND_UNIT)
        .workingDirectory(taskParameters.getWorkingDirectory())
        .environment(taskParameters.getEnvironmentVariables())
        .kubeConfigContent(kubeConfigFileContent)
        .scriptType(taskParameters.getScriptType())
        .gcpKeyFileContent(serviceAccountKeyFileContent)
        .build();
  }
}
