package io.harness.delegate.task.citasks.vm;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_COMMIT_BRANCH;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_COMMIT_LINK;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_COMMIT_SHA;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_REMOTE_URL;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_SOURCE_BRANCH;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_TARGET_BRANCH;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.NETWORK_ID;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.WORKDIR_VOLUME_ID;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.WORKDIR_VOLUME_NAME;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.citasks.CIInitializeTaskHandler;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmInitializeTaskHandler implements CIInitializeTaskHandler {
  @NotNull private Type type = CIInitializeTaskHandler.Type.VM;
  @Inject private HttpHelper httpHelper;

  @Override
  public Type getType() {
    return type;
  }

  public VmTaskExecutionResponse executeTaskInternal(
      CIInitializeTaskParams ciInitializeTaskParams, ILogStreamingTaskClient logStreamingTaskClient) {
    CIVmInitializeTaskParams CIVmInitializeTaskParams = (CIVmInitializeTaskParams) ciInitializeTaskParams;
    log.info(
        "Received request to initialize stage with stage runtime ID {}", CIVmInitializeTaskParams.getStageRuntimeId());
    return callRunnerForSetup(CIVmInitializeTaskParams);
  }

  private VmTaskExecutionResponse callRunnerForSetup(CIVmInitializeTaskParams ciVmInitializeTaskParams) {
    String errMessage = "";
    try {
      Response<SetupVmResponse> response = httpHelper.setupStageWithRetries(convert(ciVmInitializeTaskParams));
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
      errMessage = e.getMessage();
    }

    return VmTaskExecutionResponse.builder()
        .errorMessage(errMessage)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .build();
  }

  private SetupVmRequest convert(CIVmInitializeTaskParams params) {
    Map<String, String> env = new HashMap<>();
    if (isNotEmpty(params.getEnvironment())) {
      env = params.getEnvironment();
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

    SetupVmRequest.Volume workdirVol = SetupVmRequest.Volume.builder()
                                           .hostVolume(SetupVmRequest.HostVolume.builder()
                                                           .id(WORKDIR_VOLUME_ID)
                                                           .name(WORKDIR_VOLUME_NAME)
                                                           .path(params.getWorkingDir())
                                                           .build())
                                           .build();

    SetupVmRequest.Config config = SetupVmRequest.Config.builder()
                                       .envs(params.getEnvironment())
                                       .network(SetupVmRequest.Network.builder().id(NETWORK_ID).build())
                                       .logConfig(SetupVmRequest.LogConfig.builder()
                                                      .url(params.getLogStreamUrl())
                                                      .token(params.getLogSvcToken())
                                                      .accountID(params.getAccountID())
                                                      .build())
                                       .tiConfig(tiConfig)
                                       .volumes(Collections.singletonList(workdirVol))
                                       .build();
    return SetupVmRequest.builder().id(params.getStageRuntimeId()).poolID(params.getPoolID()).config(config).build();
  }
}