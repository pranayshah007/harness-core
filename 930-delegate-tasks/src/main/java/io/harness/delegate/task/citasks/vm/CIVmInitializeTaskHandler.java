/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.vm.CIVMConstants.DRONE_COMMIT_BRANCH;
import static io.harness.vm.CIVMConstants.DRONE_COMMIT_LINK;
import static io.harness.vm.CIVMConstants.DRONE_COMMIT_SHA;
import static io.harness.vm.CIVMConstants.DRONE_REMOTE_URL;
import static io.harness.vm.CIVMConstants.DRONE_SOURCE_BRANCH;
import static io.harness.vm.CIVMConstants.DRONE_TARGET_BRANCH;
import static io.harness.vm.CIVMConstants.NETWORK_ID;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.VmServiceStatus;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.ExecuteStepRequestBuilder;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmResponse;
import io.harness.delegate.beans.ci.vm.steps.VmServiceDependency;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.citasks.CIInitializeTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.helper.ProxyVariableHelper;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.delegate.task.citasks.vm.helper.StepExecutionHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.vm.VmExecuteStepUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmInitializeTaskHandler implements CIInitializeTaskHandler {
  @NotNull private Type type = CIInitializeTaskHandler.Type.VM;
  @Inject private HttpHelper httpHelper;
  @Inject private SecretSpecBuilder secretSpecBuilder;
  @Inject private ProxyVariableHelper proxyVariableHelper;
  @Inject private StepExecutionHelper stepExecutionHelper;
  @Inject private VmExecuteStepUtils vmExecuteStepUtils;

  @Override
  public Type getType() {
    return type;
  }

  public VmTaskExecutionResponse executeTaskInternal(
      CIInitializeTaskParams ciInitializeTaskParams, ILogStreamingTaskClient logStreamingTaskClient, String taskId) {
    CIVmInitializeTaskParams ciVmInitializeTaskParams = (CIVmInitializeTaskParams) ciInitializeTaskParams;
    log.info(
        "Received request to initialize stage with stage runtime ID {}", ciVmInitializeTaskParams.getStageRuntimeId());
    VmTaskExecutionResponse response = callRunnerForSetup(ciVmInitializeTaskParams, taskId);
    List<VmServiceStatus> serviceStatuses = new ArrayList<>();
    if (isNotEmpty(ciVmInitializeTaskParams.getServiceDependencies())) {
      for (VmServiceDependency serviceDependency : ciVmInitializeTaskParams.getServiceDependencies()) {
        serviceStatuses.add(startService(serviceDependency, taskId, response.getIpAddress(), ciVmInitializeTaskParams));
      }
    }
    response.setServiceStatuses(serviceStatuses);
    return response;
  }

  private VmTaskExecutionResponse callRunnerForSetup(CIVmInitializeTaskParams ciVmInitializeTaskParams, String taskId) {
    String errMessage = "";
    try {
      Response<SetupVmResponse> response =
          httpHelper.setupStageWithRetries(convertSetup(ciVmInitializeTaskParams, taskId));
      if (response.isSuccessful()) {
        return VmTaskExecutionResponse.builder()
            .ipAddress(response.body().getIpAddress())
            .errorMessage("")
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
      } else {
        errMessage = format("failed with code: %d, message: %s", response.code(), response.errorBody());
      }
    } catch (Exception e) {
      log.error("Failed to setup VM in runner", e);
      errMessage = e.toString();
    }

    return VmTaskExecutionResponse.builder()
        .errorMessage(errMessage)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .build();
  }

  private SetupVmRequest convertSetup(CIVmInitializeTaskParams params, String taskId) {
    Map<String, String> env = new HashMap<>();
    List<String> secrets = new ArrayList<>();
    if (isNotEmpty(params.getSecrets())) {
      secrets.addAll(params.getSecrets());
    }
    if (isNotEmpty(params.getEnvironment())) {
      env = params.getEnvironment();
    }

    if (params.getGitConnector() != null) {
      Map<String, SecretParams> secretVars = secretSpecBuilder.decryptGitSecretVariables(params.getGitConnector());
      for (Map.Entry<String, SecretParams> entry : secretVars.entrySet()) {
        String secret = new String(decodeBase64(entry.getValue().getValue()));
        env.put(entry.getKey(), secret);
        secrets.add(secret);
      }
    }
    if (proxyVariableHelper != null && proxyVariableHelper.checkIfProxyIsConfigured()) {
      Map<String, SecretParams> proxyConfiguration = proxyVariableHelper.getProxyConfiguration();
      for (Map.Entry<String, SecretParams> entry : proxyConfiguration.entrySet()) {
        String secret = new String(decodeBase64(entry.getValue().getValue()));
        env.put(entry.getKey(), secret);
        secrets.add(secret);
      }
    }
    SetupVmRequest.TIConfig tiConfig = SetupVmRequest.TIConfig.builder()
                                           .url(params.getTiUrl())
                                           .token(params.getTiSvcToken())
                                           .accountID(params.getAccountID())
                                           .orgID(params.getOrgID())
                                           .projectID(params.getProjectID())
                                           .pipelineID(params.getPipelineID())
                                           .stageID(params.getStageID())
                                           .buildID(params.getBuildID())
                                           .repo(env.getOrDefault(DRONE_REMOTE_URL, ""))
                                           .sha(env.getOrDefault(DRONE_COMMIT_SHA, ""))
                                           .sourceBranch(env.getOrDefault(DRONE_SOURCE_BRANCH, ""))
                                           .targetBranch(env.getOrDefault(DRONE_TARGET_BRANCH, ""))
                                           .commitBranch(env.getOrDefault(DRONE_COMMIT_BRANCH, ""))
                                           .commitLink(env.getOrDefault(DRONE_COMMIT_LINK, ""))
                                           .build();

    SetupVmRequest.Config config = SetupVmRequest.Config.builder()
                                       .envs(env)
                                       .secrets(secrets)
                                       .network(SetupVmRequest.Network.builder().id(NETWORK_ID).build())
                                       .logConfig(SetupVmRequest.LogConfig.builder()
                                                      .url(params.getLogStreamUrl())
                                                      .token(params.getLogSvcToken())
                                                      .accountID(params.getAccountID())
                                                      .indirectUpload(params.isLogSvcIndirectUpload())
                                                      .build())
                                       .tiConfig(tiConfig)
                                       .volumes(getVolumes(params.getVolToMountPath()))
                                       .build();
    return SetupVmRequest.builder()
        .id(params.getStageRuntimeId())
        .correlationID(taskId)
        .poolID(params.getPoolID())
        .config(config)
        .logKey(params.getLogKey())
        .build();
  }

  private List<SetupVmRequest.Volume> getVolumes(Map<String, String> volToMountPath) {
    List<SetupVmRequest.Volume> volumes = new ArrayList<>();
    if (isEmpty(volToMountPath)) {
      return volumes;
    }

    for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
      volumes.add(SetupVmRequest.Volume.builder()
                      .hostVolume(SetupVmRequest.HostVolume.builder()
                                      .id(entry.getKey())
                                      .name(entry.getKey())
                                      .path(entry.getValue())
                                      .build())
                      .build());
    }
    return volumes;
  }

  private VmServiceStatus startService(VmServiceDependency serviceDependency, String taskId, String ipAddress,
      CIVmInitializeTaskParams initializeTaskParams) {
    ExecuteStepRequestBuilder builder = vmExecuteStepUtils.convertService(serviceDependency, initializeTaskParams);
    builder.correlationID(taskId);
    builder.ipAddress(ipAddress);
    ExecuteStepRequest request = builder.build();

    VmTaskExecutionResponse serviceResponse = stepExecutionHelper.callRunnerForStepExecution(request);
    VmServiceStatus.Status status = VmServiceStatus.Status.ERROR;
    if (serviceResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      status = VmServiceStatus.Status.RUNNING;
    }
    return VmServiceStatus.builder()
        .identifier(serviceDependency.getIdentifier())
        .name(serviceDependency.getName())
        .image(serviceDependency.getImage())
        .logKey(serviceDependency.getLogKey())
        .errorMessage(serviceResponse.getErrorMessage())
        .status(status)
        .build();
  }
}
