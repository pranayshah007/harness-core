/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.CustomDeploymentInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.exception.ExceptionUtils;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.K8sConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionData;
import io.harness.shell.ShellExecutorConfig;

import software.wings.sm.states.customdeploymentng.InstanceMapperUtils;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CustomDeploymentInstanceSyncPerpetualTaskExecuter implements PerpetualTaskExecutor {
  public static final String COMMAND_UNIT = "Execute";
  public static final String OUTPUT_PATH_KEY = "INSTANCE_OUTPUT_PATH";
  public static final String WORKING_DIRECTORY = "/tmp";
  private static final String SUCCESS_RESPONSE_MSG = "success";

  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  static Function<InstanceMapperUtils.HostProperties, CustomDeploymentServerInstanceInfo> jsonMapper = hostProperties
      -> CustomDeploymentServerInstanceInfo.builder()
             .hostId(hostProperties.getHostName())
             .hostName(hostProperties.getHostName())
             .properties(hostProperties.getOtherPropeties())
             .build();
  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the CustomDeployment InstanceSync perpetual task executor for task id: {}", taskId);
    CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), CustomDeploymentNGInstanceSyncPerpetualTaskParams.class);
    return executeCustomDeploymentInstanceSyncTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeCustomDeploymentInstanceSyncTask(
      PerpetualTaskId taskId, CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams) {
    final ShellScriptTaskResponseNG response = executeScript(taskParams, taskId.getId());
    Map<String, String> output = ((ShellExecutionData) response.getExecuteCommandResponse().getCommandExecutionData())
                                     .getSweepingOutputEnvVariables();
    final List<CustomDeploymentServerInstanceInfo> customDeploymentServerInstanceInfos =
        InstanceMapperUtils.mapJsonToInstanceElements(taskParams.getInstanceAttributesMap(),
            taskParams.getInstancesListPath(), output.get(OUTPUT_PATH_KEY), jsonMapper);
    customDeploymentServerInstanceInfos.forEach(
        serverInstanceInfo -> serverInstanceInfo.setInstanceFetchScript(taskParams.getScript()));

    List<ServerInstanceInfo> serverInstanceInfos =
        customDeploymentServerInstanceInfos.stream().map(ServerInstanceInfo.class ::cast).collect(Collectors.toList());

    log.info("CustomDeployment Instance sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }
  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    CustomDeploymentInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        CustomDeploymentInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg =
          format("Failed to publish CustomDeployment instance sync result PerpetualTaskId [%s], accountId [%s]",
              taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }
  private ShellScriptTaskResponseNG executeScript(
      CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams, String taskId) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      ShellScriptTaskParametersNGBuilder taskParametersNGBuilder = ShellScriptTaskParametersNG.builder();
      TaskParameters taskParames = taskParametersNGBuilder.accountId(taskParams.getAccountId())
                                       .environmentVariables(new HashMap<>())
                                       .script(taskParams.getScript())
                                       .executeOnDelegate(true)
                                       .scriptType(ScriptType.BASH)
                                       .workingDirectory(WORKING_DIRECTORY)
                                       .outputVars(Collections.singletonList(OUTPUT_PATH_KEY))
                                       .build();
      ShellScriptTaskParametersNG taskParameters = (ShellScriptTaskParametersNG) taskParames;
      ShellExecutorConfig shellExecutorConfig = getShellExecutorConfig(taskParameters);
      ScriptProcessExecutor executor =
          shellExecutorFactory.getExecutor(shellExecutorConfig, null, commandUnitsProgress);
      ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
          taskParameters.getScript(), taskParameters.getOutputVars(), taskParameters.getSecretOutputVars(), null);
      return ShellScriptTaskResponseNG.builder()
          .executeCommandResponse(executeCommandResponse)
          .status(executeCommandResponse.getStatus())
          .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception ex) {
      log.error("Exception Occured While Running Custom Deployment NG Perpetual Task:{}, Message: {}", taskId,
          ExceptionUtils.getMessage(ex));
      return ShellScriptTaskResponseNG.builder()
          .status(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    }
  }
  private ShellExecutorConfig getShellExecutorConfig(ShellScriptTaskParametersNG taskParameters) {
    String kubeConfigFileContent = taskParameters.getScript().contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH)
            && taskParameters.getK8sInfraDelegateConfig() != null
        ? containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(taskParameters.getK8sInfraDelegateConfig())
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

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
