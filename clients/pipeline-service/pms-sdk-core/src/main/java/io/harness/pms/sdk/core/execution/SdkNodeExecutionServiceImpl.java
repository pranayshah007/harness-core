/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.AddExecutableResponseRequest;
import io.harness.pms.contracts.execution.events.FacilitatorResponseRequest;
import io.harness.pms.contracts.execution.events.HandleProgressRequest;
import io.harness.pms.contracts.execution.events.HandleStepResponseRequest;
import io.harness.pms.contracts.execution.events.QueueTaskRequest;
import io.harness.pms.contracts.execution.events.ResumeNodeExecutionRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.execution.events.SuspendChainRequest;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.pms.sdk.core.execution.async.AsyncProgressData;
import io.harness.pms.sdk.core.response.publishers.SdkResponseEventPublisher;
import io.harness.pms.sdk.core.steps.io.ResponseDataMapper;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class SdkNodeExecutionServiceImpl implements SdkNodeExecutionService {
  @Inject private ResponseDataMapper responseDataMapper;
  @Inject private SdkResponseEventPublisher sdkResponseEventPublisher;

  @Override
  public void suspendChainExecution(Ambiance ambiance, SuspendChainRequest suspendChainRequest) {
    sdkResponseEventPublisher.publishEvent(SdkResponseEventProto.newBuilder()
                                               .setSdkResponseEventType(SdkResponseEventType.SUSPEND_CHAIN)
                                               .setSuspendChainRequest(suspendChainRequest)
                                               .setAmbiance(ambiance)
                                               .build());
  }

  @Override
  public void addExecutableResponse(Ambiance ambiance, ExecutableResponse executableResponse) {
    AddExecutableResponseRequest executableResponseRequest =
        AddExecutableResponseRequest.newBuilder().setExecutableResponse(executableResponse).build();

    SdkResponseEventProto sdkResponseEvent = SdkResponseEventProto.newBuilder()
                                                 .setSdkResponseEventType(SdkResponseEventType.ADD_EXECUTABLE_RESPONSE)
                                                 .setAddExecutableResponseRequest(executableResponseRequest)
                                                 .setAmbiance(ambiance)
                                                 .build();
    sdkResponseEventPublisher.publishEvent(sdkResponseEvent);
  }

  @Override
  public void handleStepResponse(
      Ambiance ambiance, @NonNull StepResponseProto stepResponse, ExecutableResponse executableResponse) {
    HandleStepResponseRequest.Builder responseRequestBuilder =
        HandleStepResponseRequest.newBuilder().setStepResponse(stepResponse);
    if (executableResponse != null) {
      responseRequestBuilder.setExecutableResponse(executableResponse);
    }
    SdkResponseEventProto sdkResponseEventProto =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.HANDLE_STEP_RESPONSE)
            .setHandleStepResponseRequest(responseRequestBuilder.build())
            .setAmbiance(ambiance)
            .build();

    sdkResponseEventPublisher.publishEvent(sdkResponseEventProto);
  }

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseData> response, boolean asyncError) {
    Map<String, ResponseDataProto> responseDataBytes = responseDataMapper.toResponseDataProtoV2(response);

    ResumeNodeExecutionRequest resumeNodeExecutionRequest =
        ResumeNodeExecutionRequest.newBuilder().putAllResponseData(responseDataBytes).setAsyncError(asyncError).build();
    SdkResponseEventProto sdkResponseEvent = SdkResponseEventProto.newBuilder()
                                                 .setSdkResponseEventType(SdkResponseEventType.RESUME_NODE_EXECUTION)
                                                 .setResumeNodeExecutionRequest(resumeNodeExecutionRequest)
                                                 .setAmbiance(ambiance)
                                                 .build();

    sdkResponseEventPublisher.publishEvent(sdkResponseEvent);
  }

  @Override
  public void handleFacilitationResponse(
      Ambiance ambiance, @NonNull String notifyId, FacilitatorResponseProto facilitatorResponseProto) {
    FacilitatorResponseRequest facilitatorResponseRequest = FacilitatorResponseRequest.newBuilder()
                                                                .setFacilitatorResponse(facilitatorResponseProto)
                                                                .setNotifyId(notifyId)
                                                                .build();

    sdkResponseEventPublisher.publishEvent(SdkResponseEventProto.newBuilder()
                                               .setFacilitatorResponseRequest(facilitatorResponseRequest)
                                               .setSdkResponseEventType(SdkResponseEventType.HANDLE_FACILITATE_RESPONSE)
                                               .setAmbiance(ambiance)
                                               .build());
  }

  @Override
  public void handleAdviserResponse(Ambiance ambiance, @NonNull String notifyId, AdviserResponse adviserResponse) {
    SdkResponseEventProto handleAdviserResponseRequest =
        SdkResponseEventUtils.getSdkResponse(ambiance, notifyId, adviserResponse);
    sdkResponseEventPublisher.publishEvent(handleAdviserResponseRequest);
  }

  @Override
  public void handleEventError(
      NodeExecutionEventType eventType, Ambiance ambiance, String eventNotifyId, FailureInfo failureInfo) {
    SdkResponseEventProto handleEventErrorRequest =
        SdkResponseEventUtils.getSdkErrorResponse(eventType, ambiance, eventNotifyId, failureInfo);
    sdkResponseEventPublisher.publishEvent(handleEventErrorRequest);
  }

  @Override
  public void spawnChild(Ambiance ambiance, SpawnChildRequest spawnChildRequest) {
    sdkResponseEventPublisher.publishEvent(SdkResponseEventProto.newBuilder()
                                               .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILD)
                                               .setSpawnChildRequest(spawnChildRequest)
                                               .setAmbiance(ambiance)
                                               .build());
  }

  @Override
  public void handleProgressResponse(Ambiance ambiance, ProgressData progressData) {
    String progressJson = RecastOrchestrationUtils.toJson(progressData);
    sdkResponseEventPublisher.publishEvent(SdkResponseEventProto.newBuilder()
                                               .setSdkResponseEventType(SdkResponseEventType.HANDLE_PROGRESS)
                                               .setAmbiance(ambiance)
                                               .setProgressRequest(HandleProgressRequest.newBuilder()
                                                                       .setStatus(calculateRequestStatus(progressData))
                                                                       .setProgressJson(progressJson)
                                                                       .build())

                                               .build());
  }

  private Status calculateRequestStatus(ProgressData progressData) {
    return progressData instanceof AsyncProgressData ? ((AsyncProgressData) progressData).getStatus() : Status.NO_OP;
  }

  @Override
  public void spawnChildren(Ambiance ambiance, SpawnChildrenRequest spawnChildrenRequest) {
    sdkResponseEventPublisher.publishEvent(SdkResponseEventProto.newBuilder()
                                               .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
                                               .setSpawnChildrenRequest(spawnChildrenRequest)
                                               .setAmbiance(ambiance)
                                               .build());
  }

  @Override
  public void queueTaskRequest(Ambiance ambiance, QueueTaskRequest queueTaskRequest) {
    sdkResponseEventPublisher.publishEvent(SdkResponseEventProto.newBuilder()
                                               .setSdkResponseEventType(SdkResponseEventType.QUEUE_TASK)
                                               .setQueueTaskRequest(queueTaskRequest)
                                               .setAmbiance(ambiance)
                                               .build());
  }
}
