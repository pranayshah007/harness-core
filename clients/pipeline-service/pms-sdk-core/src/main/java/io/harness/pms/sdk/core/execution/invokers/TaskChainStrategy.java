/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.core.execution.invokers.StrategyHelper.buildResponseDataSupplier;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskChainExecutableResponse;
import io.harness.pms.contracts.execution.events.QueueTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.ChainDetails;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ProgressableStrategy;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.executables.TaskChainExecutable;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseMapper;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@SuppressWarnings({"rawtypes", "unchecked"})
@OwnedBy(PIPELINE)
@Slf4j
public class TaskChainStrategy extends ProgressableStrategy {
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject private StepRegistry stepRegistry;
  @Inject private StrategyHelper strategyHelper;

  @Override
  public void start(InvokerPackage invokerPackage) {
    Ambiance ambiance = invokerPackage.getAmbiance();
    TaskChainExecutable taskChainExecutable = extractStep(ambiance);

    TaskChainResponse taskChainResponse = taskChainExecutable.startChainLink(
        ambiance, invokerPackage.getStepParameters(), invokerPackage.getInputPackage());
    handleResponse(ambiance, invokerPackage.getStepParameters(), taskChainResponse);
  }

  @Override
  public void resume(ResumePackage resumePackage) {
    Ambiance ambiance = resumePackage.getAmbiance();
    ChainDetails chainDetails = resumePackage.getChainDetails();
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    TaskChainExecutable taskChainExecutable = extractStep(ambiance);
    StepParameters stepParameters = resumePackage.getStepParameters();
    if (chainDetails.isShouldEnd()) {
      StepResponse stepResponse;
      try {
        log.info("Chain end true: Calling finalizeExecution, nodeExecutionId: {}", nodeExecutionId);
        stepResponse = taskChainExecutable.finalizeExecution(ambiance, stepParameters,
            chainDetails.getPassThroughData(), buildResponseDataSupplier(resumePackage.getResponseDataMap()));
      } catch (Exception e) {
        log.error("Exception occurred while calling finalizeExecution, nodeExecutionId: {}", nodeExecutionId, e);
        stepResponse = strategyHelper.handleException(e);
      }
      sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse), null);
    } else {
      try {
        TaskChainResponse chainResponse =
            taskChainExecutable.executeNextLink(ambiance, stepParameters, resumePackage.getStepInputPackage(),
                chainDetails.getPassThroughData(), buildResponseDataSupplier(resumePackage.getResponseDataMap()));
        handleResponse(ambiance, stepParameters, chainResponse);
      } catch (Exception e) {
        log.error("Exception occurred while calling executeNextLink, nodeExecutionId: {}", nodeExecutionId, e);
        sdkNodeExecutionService.handleStepResponse(
            ambiance, StepResponseMapper.toStepResponseProto(strategyHelper.handleException(e)));
      }
    }
  }

  private void handleResponse(
      @NonNull Ambiance ambiance, StepParameters stepParameters, @NonNull TaskChainResponse taskChainResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (taskChainResponse.isChainEnd() && taskChainResponse.getTaskRequest() == null) {
      TaskChainExecutable taskChainExecutable = extractStep(ambiance);
      StepResponse stepResponse = null;
      try {
        stepResponse = taskChainExecutable.finalizeExecution(
            ambiance, stepParameters, taskChainResponse.getPassThroughData(), () -> null);
      } catch (Exception e) {
        log.error("Exception occurred while calling finalizeExecution, nodeExecutionId: {}", nodeExecutionId, e);
        stepResponse = strategyHelper.handleException(e);
      }
      sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse),
          ExecutableResponse.newBuilder()
              .setTaskChain(TaskChainExecutableResponse.newBuilder()
                                .setChainEnd(true)
                                .setPassThroughData(ByteString.copyFrom(
                                    RecastOrchestrationUtils.toBytes(taskChainResponse.getPassThroughData())))
                                .addAllLogKeys(CollectionUtils.emptyIfNull(taskChainResponse.getLogKeys()))
                                .addAllUnits(CollectionUtils.emptyIfNull(taskChainResponse.getUnits()))
                                .build())
              .build());
      return;
    }
    TaskRequest taskRequest = taskChainResponse.getTaskRequest();

    ExecutableResponse executableResponse =
        ExecutableResponse.newBuilder()
            .setTaskChain(
                TaskChainExecutableResponse.newBuilder()
                    .setTaskCategory(taskChainResponse.getTaskRequest().getTaskCategory())
                    .setChainEnd(taskChainResponse.isChainEnd())
                    .setPassThroughData(
                        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(taskChainResponse.getPassThroughData())))
                    .addAllLogKeys(CollectionUtils.emptyIfNull(taskRequest.getDelegateTaskRequest().getLogKeysList()))
                    .addAllUnits(CollectionUtils.emptyIfNull(taskRequest.getDelegateTaskRequest().getUnitsList()))
                    .setTaskName(taskRequest.getDelegateTaskRequest().getTaskName())
                    .build())
            .build();
    QueueTaskRequest queueTaskRequest = QueueTaskRequest.newBuilder()
                                            .putAllSetupAbstractions(ambiance.getSetupAbstractionsMap())
                                            .setTaskRequest(taskRequest)
                                            .setExecutableResponse(executableResponse)
                                            .setStatus(Status.TASK_WAITING)
                                            .build();
    sdkNodeExecutionService.queueTaskRequest(ambiance, queueTaskRequest);
  }

  @Override
  public TaskChainExecutable extractStep(Ambiance ambiance) {
    return (TaskChainExecutable) stepRegistry.obtain(AmbianceUtils.getCurrentStepType(ambiance));
  }
}
