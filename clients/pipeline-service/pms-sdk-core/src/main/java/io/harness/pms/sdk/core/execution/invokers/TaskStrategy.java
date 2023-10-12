/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.pms.contracts.execution.Status.TASK_WAITING;
import static io.harness.pms.sdk.core.execution.invokers.StrategyHelper.buildResponseDataSupplier;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.SkipTaskExecutableResponse;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.events.QueueTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest.RequestCase;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.StringOutcome;
import io.harness.pms.sdk.core.execution.ExecuteStrategy;
import io.harness.pms.sdk.core.execution.InterruptPackage;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ProgressPackage;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.executables.TaskExecutable;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponseMapper;
import io.harness.tasks.ProgressData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@SuppressWarnings({"rawtypes", "unchecked"})
@OwnedBy(PIPELINE)
@Slf4j
public class TaskStrategy implements ExecuteStrategy {
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject private StepRegistry stepRegistry;
  @Inject private StrategyHelper strategyHelper;

  @Override
  public void start(InvokerPackage invokerPackage) {
    Ambiance ambiance = invokerPackage.getAmbiance();
    TaskExecutable taskExecutable = extractStep(ambiance);
    final Optional<TaskRequest> taskOptional = taskExecutable.obtainTaskOptional(
        ambiance, invokerPackage.getStepParameters(), invokerPackage.getInputPackage());
    if (taskOptional.isEmpty()) {
      sdkNodeExecutionService.resumeNodeExecution(ambiance, Collections.emptyMap(), false);
      return;
    }
    handleResponse(ambiance, taskOptional.get());
  }

  @Override
  public void resume(ResumePackage resumePackage) {
    Ambiance ambiance = resumePackage.getAmbiance();
    TaskExecutable taskExecutable = extractStep(ambiance);
    StepResponse stepResponse = null;
    try {
      stepResponse = taskExecutable.handleTaskResult(
          ambiance, resumePackage.getStepParameters(), buildResponseDataSupplier(resumePackage.getResponseDataMap()));
    } catch (Exception e) {
      log.error("Exception occurred while calling handleTaskResult", e);
      stepResponse = strategyHelper.handleException(e);
    }
    sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse));
  }

  private void handleResponse(@NonNull Ambiance ambiance, TaskRequest taskRequest) {
    if (RequestCase.SKIPTASKREQUEST == taskRequest.getRequestCase()) {
      sdkNodeExecutionService.handleStepResponse(ambiance,
          StepResponseMapper.toStepResponseProto(
              StepResponse.builder()
                  .status(SKIPPED)
                  .stepOutcome(
                      StepOutcome.builder()
                          .name("skipOutcome")
                          .outcome(
                              StringOutcome.builder().message(taskRequest.getSkipTaskRequest().getMessage()).build())
                          .build())
                  .build()),
          ExecutableResponse.newBuilder()
              .setSkipTask(SkipTaskExecutableResponse.newBuilder()
                               .setMessage(taskRequest.getSkipTaskRequest().getMessage())
                               .build())
              .build());
      return;
    }

    ExecutableResponse executableResponse =
        ExecutableResponse.newBuilder()
            .setTask(
                TaskExecutableResponse.newBuilder()
                    .setTaskCategory(taskRequest.getTaskCategory())
                    .addAllLogKeys(CollectionUtils.emptyIfNull(taskRequest.getDelegateTaskRequest().getLogKeysList()))
                    .addAllUnits(CollectionUtils.emptyIfNull(taskRequest.getDelegateTaskRequest().getUnitsList()))
                    .setTaskName(taskRequest.getDelegateTaskRequest().getTaskName())
                    .build())
            .build();

    QueueTaskRequest queueTaskRequest = QueueTaskRequest.newBuilder()
                                            .putAllSetupAbstractions(ambiance.getSetupAbstractionsMap())
                                            .setTaskRequest(taskRequest)
                                            .setExecutableResponse(executableResponse)
                                            .setStatus(TASK_WAITING)
                                            .build();
    sdkNodeExecutionService.queueTaskRequest(ambiance, queueTaskRequest);
  }

  @Override
  public TaskExecutable extractStep(Ambiance ambiance) {
    return (TaskExecutable) stepRegistry.obtain(AmbianceUtils.getCurrentStepType(ambiance));
  }

  @Override
  public void abort(InterruptPackage interruptPackage) {
    TaskExecutable executable = extractStep(interruptPackage.getAmbiance());
    executable.handleAbort(interruptPackage.getAmbiance(), interruptPackage.getParameters(), interruptPackage.getTask(),
        interruptPackage.isUserMarked());
  }

  @Override
  public void failure(InterruptPackage interruptPackage) {
    TaskExecutable executable = extractStep(interruptPackage.getAmbiance());
    executable.handleFailure(interruptPackage.getAmbiance(), interruptPackage.getParameters(),
        interruptPackage.getTask(), interruptPackage.getMetadata());
  }

  @Override
  public void progress(ProgressPackage progressPackage) {
    Ambiance ambiance = progressPackage.getAmbiance();
    TaskExecutable taskExecutable = extractStep(ambiance);
    ProgressData resp = taskExecutable.handleProgressTask(
        ambiance, progressPackage.getStepParameters(), progressPackage.getProgressData());
    if (resp != null) {
      sdkNodeExecutionService.handleProgressResponse(ambiance, resp);
    }
  }

  @Override
  public void expire(InterruptPackage interruptPackage) {
    TaskExecutable executable = extractStep(interruptPackage.getAmbiance());
    executable.handleExpire(
        interruptPackage.getAmbiance(), interruptPackage.getParameters(), interruptPackage.getTask());
  }
}
