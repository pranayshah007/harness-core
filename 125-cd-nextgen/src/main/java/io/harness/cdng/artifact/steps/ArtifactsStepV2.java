package io.harness.cdng.artifact.steps;

import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome.ArtifactsOutcomeBuilder;
import io.harness.cdng.artifact.outcome.SidecarsOutcome;
import io.harness.cdng.artifact.steps.ArtifactsStepV2SweepingOutput.ArtifactsStepV2SweepingOutputBuilder;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceStepsHelper;
import io.harness.cdng.service.steps.ServiceSweepingOutput;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactsStepV2 implements AsyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ARTIFACTS_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
  public static final String ARTIFACTS_STEP_V_2 = "artifacts_step_v2";
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ArtifactStepHelper artifactStepHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, EmptyStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ServiceSweepingOutput serviceSweepingOutput = (ServiceSweepingOutput) sweepingOutputService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT));
    NGServiceConfig ngServiceConfig = null;
    if (serviceSweepingOutput != null) {
      try {
        ngServiceConfig = YamlUtils.read(serviceSweepingOutput.getFinalServiceYaml(), NGServiceConfig.class);
      } catch (IOException e) {
        // Todo:(yogesh) handle exception
        throw new RuntimeException(e);
      }
    }
    final Set<String> taskIds = new HashSet<>();
    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    final ArtifactsStepV2SweepingOutputBuilder artifactsStepV2SweepingOutputBuilder =
        ArtifactsStepV2SweepingOutput.builder();
    if (ngServiceV2InfoConfig != null && ngServiceV2InfoConfig.getServiceDefinition() != null) {
      if (ngServiceV2InfoConfig.getServiceDefinition().getServiceSpec() != null) {
        final ArtifactListConfig artifacts =
            ngServiceV2InfoConfig.getServiceDefinition().getServiceSpec().getArtifacts();
        if (artifacts != null) {
          if (artifacts.getPrimary() != null) {
            String taskId = handle(ambiance, artifacts.getPrimary());
            taskIds.add(taskId);
            ArtifactsStepV2SweepingOutput sweepingOutput =
                artifactsStepV2SweepingOutputBuilder.primaryArtifactTaskId(taskId).build();
            sweepingOutput.getArtifactConfigMap().put(taskId, artifacts.getPrimary().getSpec());
            sweepingOutputService.consume(ambiance, ARTIFACTS_STEP_V_2, sweepingOutput, "");
          }
        }
      }
      return AsyncExecutableResponse.newBuilder().addAllCallbackIds(taskIds).build();
    }
    return AsyncExecutableResponse.newBuilder().addAllCallbackIds(Collections.emptyList()).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    final ArtifactsOutcomeBuilder outcomeBuilder = ArtifactsOutcome.builder();
    final SidecarsOutcome sidecarsOutcome = new SidecarsOutcome();
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);

    OptionalSweepingOutput outputOptional =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2));

    if (!outputOptional.isFound()) {
      // throw exception
      throw new RuntimeException("unexpected");
    }

    ArtifactsStepV2SweepingOutput artifactsSweepingOutput = (ArtifactsStepV2SweepingOutput) outputOptional.getOutput();

    for (String taskId : responseDataMap.keySet()) {
      final ArtifactConfig artifactConfig = artifactsSweepingOutput.getArtifactConfigMap().get(taskId);
      ArtifactTaskResponse taskResponse = (ArtifactTaskResponse) responseDataMap.get(taskId);
      final boolean isPrimary = taskId.equals(artifactsSweepingOutput.getPrimaryArtifactTaskId());
      if (isPrimary) {
        logCallback.saveExecutionLog(LogHelper.color(String.format("Fetched details of primary artifact [status:%s]",
                                                         taskResponse.getCommandExecutionStatus().name()),
            LogColor.Cyan, LogWeight.Bold));
      } else {
        logCallback.saveExecutionLog(
            LogHelper.color(String.format("Fetched details of sidecar artifact [%s] [status: %s]",
                                artifactConfig.getIdentifier(), taskResponse.getCommandExecutionStatus().name()),
                LogColor.Cyan, LogWeight.Bold));
      }

      if (taskResponse.getArtifactTaskExecutionResponse() != null
          && taskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses() != null) {
        logCallback.saveExecutionLog(LogHelper.color(
            taskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0).describe(),
            LogColor.Green, LogWeight.Bold));
      }

      switch (taskResponse.getCommandExecutionStatus()) {
        case SUCCESS:
          ArtifactOutcome artifactOutcome = ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig,
              taskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0), true);
          if (isPrimary) {
            outcomeBuilder.primary(artifactOutcome);
          } else {
            sidecarsOutcome.put(artifactConfig.getIdentifier(), artifactOutcome);
          }
          break;
        case FAILURE:
          throw new ArtifactServerException("Artifact delegate task failed: " + taskResponse.getErrorMessage());
        default:
          throw new ArtifactServerException("Unhandled command execution status: "
              + (taskResponse.getCommandExecutionStatus() == null ? "null"
                                                                  : taskResponse.getCommandExecutionStatus().name()));
      }
    }
    final ArtifactsOutcome artifactsOutcome = outcomeBuilder.sidecars(sidecarsOutcome).build();
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.ARTIFACTS)
                         .outcome(artifactsOutcome)
                         .group(StepCategory.STAGE.name())
                         .build())
        .build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, EmptyStepParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  private String handle(final Ambiance ambiance, final PrimaryArtifact primary) {
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    logCallback.saveExecutionLog("Processing primary artifact...");
    final ArtifactConfig artifactConfig = primary.getSpec();
    logCallback.saveExecutionLog(
        String.format("Primary artifact info: %s", ArtifactUtils.getLogInfo(artifactConfig, primary.getSourceType())));

    ArtifactSourceDelegateRequest artifactSourceDelegateRequest =
        artifactStepHelper.toSourceDelegateRequest(artifactConfig, ambiance);
    final ArtifactTaskParameters taskParameters = ArtifactTaskParameters.builder()
                                                      .accountId(AmbianceUtils.getAccountId(ambiance))
                                                      .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                      .attributes(artifactSourceDelegateRequest)
                                                      .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .taskType(artifactStepHelper.getArtifactStepTaskType(artifactConfig).name())
                                  .parameters(new Object[] {taskParameters})
                                  //                                  .timeout(DEFAULT_TIMEOUT)
                                  .build();

    String taskName = artifactStepHelper.getArtifactStepTaskType(artifactConfig).getDisplayName() + ": "
        + taskParameters.getArtifactTaskType().getDisplayName();
    logCallback.saveExecutionLog(
        LogHelper.color("Starting delegate task to fetch details of primary artifact", LogColor.Cyan, LogWeight.Bold));
    List<TaskSelector> delegateSelectors = artifactStepHelper.getDelegateSelectors(artifactConfig, ambiance);

    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .taskParameters(taskParameters)
            // Todo
            //                                .taskSelectors(delegateSelectors.stream().map(TaskSelector).collect(Collectors.toList()))
            .taskType(artifactStepHelper.getArtifactStepTaskType(artifactConfig).name())
            .executionTimeout(DEFAULT_TIMEOUT)
            .taskSetupAbstraction("ng", "true")
            //            .logStreamingAbstractions(logAbstractions)
            .build();

    return delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
  }
}
