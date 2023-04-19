/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.ArgumentMatchers.any;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.AddExecutableResponseRequest;
import io.harness.pms.contracts.execution.events.AdviserResponseRequest;
import io.harness.pms.contracts.execution.events.EventErrorRequest;
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
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.async.AsyncProgressData;
import io.harness.pms.sdk.core.response.publishers.SdkResponseEventPublisher;
import io.harness.pms.sdk.core.steps.io.ResponseDataMapper;
import io.harness.rule.Owner;
import io.harness.tasks.ProgressData;

import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class SdkNodeExecutionServiceImplTest extends PmsSdkCoreTestBase {
  @Mock ResponseDataMapper responseDataMapper;
  @Mock SdkResponseEventPublisher sdkResponseEventPublisher;

  @InjectMocks SdkNodeExecutionServiceImpl sdkNodeExecutionService;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSuspendChainExecution() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    SuspendChainRequest suspendChainRequest = SuspendChainRequest.newBuilder().build();
    sdkNodeExecutionService.suspendChainExecution(ambiance, suspendChainRequest);
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.SUSPEND_CHAIN)
                          .setSuspendChainRequest(suspendChainRequest)
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddExecutableResponse() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().build();
    AddExecutableResponseRequest suspendChainRequest =
        AddExecutableResponseRequest.newBuilder().setExecutableResponse(executableResponse).build();
    sdkNodeExecutionService.addExecutableResponse(ambiance, executableResponse);
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.ADD_EXECUTABLE_RESPONSE)
                          .setAddExecutableResponseRequest(suspendChainRequest)
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleStepResponse() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().build();
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    HandleStepResponseRequest handleStepResponseRequest = HandleStepResponseRequest.newBuilder()
                                                              .setStepResponse(stepResponseProto)
                                                              .setExecutableResponse(executableResponse)
                                                              .build();
    sdkNodeExecutionService.handleStepResponse(ambiance, stepResponseProto, executableResponse);
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.HANDLE_STEP_RESPONSE)
                          .setHandleStepResponseRequest(handleStepResponseRequest)
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testResumeNodeExecution() {
    Mockito.when(responseDataMapper.toResponseDataProto(any())).thenReturn(new HashMap<>());
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.resumeNodeExecution(ambiance, new HashMap<>(), false);
    ResumeNodeExecutionRequest resumeNodeExecutionRequest =
        ResumeNodeExecutionRequest.newBuilder().putAllResponse(new HashMap<>()).setAsyncError(false).build();
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.RESUME_NODE_EXECUTION)
                          .setResumeNodeExecutionRequest(resumeNodeExecutionRequest)
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleFacilitationResponse() {
    String notifyId = "notifyId";
    Mockito.when(responseDataMapper.toResponseDataProto(any())).thenReturn(new HashMap<>());
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    FacilitatorResponseRequest facilitatorResponseRequest =
        FacilitatorResponseRequest.newBuilder()
            .setFacilitatorResponse(FacilitatorResponseProto.newBuilder().build())
            .setNotifyId(notifyId)
            .build();
    sdkNodeExecutionService.handleFacilitationResponse(
        ambiance, notifyId, FacilitatorResponseProto.newBuilder().build());
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.HANDLE_FACILITATE_RESPONSE)
                          .setFacilitatorResponseRequest(facilitatorResponseRequest)
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleAdviserResponse() {
    String notifyId = "notifyId";
    Mockito.when(responseDataMapper.toResponseDataProto(any())).thenReturn(new HashMap<>());
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    AdviserResponseRequest facilitatorResponseRequest = AdviserResponseRequest.newBuilder()
                                                            .setAdviserResponse(AdviserResponse.newBuilder().build())
                                                            .setNotifyId(notifyId)
                                                            .build();
    sdkNodeExecutionService.handleAdviserResponse(ambiance, notifyId, AdviserResponse.newBuilder().build());
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
                          .setAdviserResponseRequest(facilitatorResponseRequest)
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventError() {
    String notifyId = "notifyId";
    Mockito.when(responseDataMapper.toResponseDataProto(any())).thenReturn(new HashMap<>());
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.handleEventError(
        NodeExecutionEventType.UNKNOWN_NODE_EVENT, ambiance, notifyId, FailureInfo.newBuilder().build());
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.HANDLE_EVENT_ERROR)
                          .setEventErrorRequest(EventErrorRequest.newBuilder()
                                                    .setEventNotifyId(notifyId)
                                                    .setEventType(NodeExecutionEventType.UNKNOWN_NODE_EVENT)
                                                    .setFailureInfo(FailureInfo.newBuilder().build())
                                                    .build())
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSpawnChild() {
    Mockito.when(responseDataMapper.toResponseDataProto(any())).thenReturn(new HashMap<>());
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.spawnChild(ambiance, SpawnChildRequest.newBuilder().build());
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILD)
                          .setSpawnChildRequest(SpawnChildRequest.newBuilder().build())
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSpawnChildren() {
    Mockito.when(responseDataMapper.toResponseDataProto(any())).thenReturn(new HashMap<>());
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.spawnChildren(ambiance, SpawnChildrenRequest.newBuilder().build());
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
                          .setSpawnChildrenRequest(SpawnChildrenRequest.newBuilder().build())
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testQueueTaskRequest() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.queueTaskRequest(ambiance, QueueTaskRequest.newBuilder().build());
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(SdkResponseEventProto.newBuilder()
                          .setSdkResponseEventType(SdkResponseEventType.QUEUE_TASK)
                          .setQueueTaskRequest(QueueTaskRequest.newBuilder().build())
                          .setAmbiance(ambiance)
                          .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleProgressResponse() {
    ProgressData progressData = UnitProgressData.builder().build();
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.handleProgressResponse(ambiance, progressData);
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(
            SdkResponseEventProto.newBuilder()
                .setSdkResponseEventType(SdkResponseEventType.HANDLE_PROGRESS)
                .setProgressRequest(
                    HandleProgressRequest.newBuilder()
                        .setProgressJson("{\"__recast\":\"io.harness.delegate.beans.logstreaming.UnitProgressData\"}")
                        .setStatus(Status.NO_OP)
                        .build())
                .setAmbiance(ambiance)
                .build());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleProgressResponseWithAsync() {
    AsyncProgressData progressData = AsyncProgressData.builder().status(Status.ASYNC_WAITING).build();
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    sdkNodeExecutionService.handleProgressResponse(ambiance, progressData);
    Mockito.verify(sdkResponseEventPublisher)
        .publishEvent(
            SdkResponseEventProto.newBuilder()
                .setSdkResponseEventType(SdkResponseEventType.HANDLE_PROGRESS)
                .setProgressRequest(
                    HandleProgressRequest.newBuilder()
                        .setProgressJson(
                            "{\"__recast\":\"io.harness.pms.sdk.core.execution.async.AsyncProgressData\",\"status\":{\"__recast\":\"io.harness.pms.contracts.execution.Status\",\"__encodedValue\":\"ASYNC_WAITING\"}}")
                        .setStatus(Status.ASYNC_WAITING)
                        .build())
                .setAmbiance(ambiance)
                .build());
  }
}
