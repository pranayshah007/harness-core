/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapper;
import io.harness.cdng.artifact.steps.beans.ArtifactStepParameters;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ArtifactStep implements TaskExecutable<ArtifactStepParameters, ArtifactTaskResponse> {
  private static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(3);

  @Inject private ArtifactStepHelper artifactStepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<ArtifactStepParameters> getStepParametersClass() {
    return ArtifactStepParameters.class;
  }

  @Override
  public TaskRequest obtainTask(
      Ambiance ambiance, ArtifactStepParameters stepParameters, StepInputPackage inputPackage) {
    ArtifactConfig finalArtifact = artifactStepHelper.applyArtifactsOverlay(stepParameters);
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (finalArtifact.isPrimaryArtifact()) {
      logCallback.saveExecutionLog("Processing primary artifact...");
      logCallback.saveExecutionLog(String.format(
          "Primary artifact info: %s", ArtifactUtils.getLogInfo(finalArtifact, finalArtifact.getSourceType())));
    } else {
      logCallback.saveExecutionLog(String.format("Processing sidecar artifact [%s]...", finalArtifact.getIdentifier()));
      logCallback.saveExecutionLog(String.format("Sidecar artifact [%s] info: %s", finalArtifact.getIdentifier(),
          ArtifactUtils.getLogInfo(finalArtifact, finalArtifact.getSourceType())));
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ArtifactSourceDelegateRequest artifactSourceDelegateRequest =
        artifactStepHelper.toSourceDelegateRequest(finalArtifact, ambiance);
    final ArtifactTaskParameters taskParameters = ArtifactTaskParameters.builder()
                                                      .accountId(accountId)
                                                      .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                      .attributes(artifactSourceDelegateRequest)
                                                      .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .taskType(artifactStepHelper.getArtifactStepTaskType(finalArtifact).name())
                                  .parameters(new Object[] {taskParameters})
                                  .timeout(DEFAULT_TIMEOUT)
                                  .build();

    String taskName = artifactStepHelper.getArtifactStepTaskType(finalArtifact).getDisplayName() + ": "
        + taskParameters.getArtifactTaskType().getDisplayName();
    if (finalArtifact.isPrimaryArtifact()) {
      logCallback.saveExecutionLog(LogHelper.color(
          "Starting delegate task to fetch details of primary artifact", LogColor.Cyan, LogWeight.Bold));
    } else {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Starting delegate task to fetch details of sidecar artifact [%s]",
                              finalArtifact.getIdentifier()),
              LogColor.Cyan, LogWeight.Bold));
    }
    List<TaskSelector> delegateSelectors = artifactStepHelper.getDelegateSelectors(finalArtifact, ambiance);
    return TaskRequestsUtils.prepareTaskRequestWithTaskSelector(ambiance, taskData, referenceFalseKryoSerializer,
        TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), false, taskName, delegateSelectors);
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, ArtifactStepParameters stepParameters,
      ThrowingSupplier<ArtifactTaskResponse> responseDataSupplier) throws Exception {
    ArtifactConfig finalArtifact = artifactStepHelper.applyArtifactsOverlay(stepParameters);
    ArtifactTaskResponse taskResponse = responseDataSupplier.get();

    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (finalArtifact.isPrimaryArtifact()) {
      logCallback.saveExecutionLog(LogHelper.color(String.format("Fetched details of primary artifact [status: %s]",
                                                       taskResponse.getCommandExecutionStatus().name()),
          LogColor.Cyan, LogWeight.Bold));
    } else {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Fetched details of sidecar artifact [%s] [status: %s]",
                              finalArtifact.getIdentifier(), taskResponse.getCommandExecutionStatus().name()),
              LogColor.Cyan, LogWeight.Bold));
    }

    if (taskResponse != null && taskResponse.getArtifactTaskExecutionResponse() != null
        && taskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses() != null) {
      logCallback.saveExecutionLog(LogHelper.color(
          taskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0).describe(),
          LogColor.Green, LogWeight.Bold));
    }

    switch (taskResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        return StepResponse.builder()
            .status(Status.SUCCEEDED)
            .stepOutcome(
                StepOutcome.builder()
                    .name("output")
                    .outcome(ArtifactResponseToOutcomeMapper.toArtifactOutcome(finalArtifact,
                        taskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0), true))
                    .build())
            .build();
      case FAILURE:
        throw new ArtifactServerException("Artifact delegate task failed: " + taskResponse.getErrorMessage());
      default:
        throw new ArtifactServerException("Unhandled command execution status: "
            + (taskResponse.getCommandExecutionStatus() == null ? "null"
                                                                : taskResponse.getCommandExecutionStatus().name()));
    }
  }
}
