/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepTypes;
import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.advise.AdviseHandlerFactory;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.advise.handlers.NextStepHandler;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityNodeExecutionStrategyTest extends OrchestrationTestBase {
  @Mock private PmsEventSender eventSender;
  @Mock @Named("EngineExecutorService") ExecutorService executorService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private IdentityNodeResumeHelper identityNodeResumeHelper;
  @Mock private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Mock private AdviseHandlerFactory adviseHandlerFactory;
  @Mock private NextStepHandler nextStepHandler;
  @Mock IdentityNodeExecutionStrategyHelper identityNodeExecutionStrategyHelper;
  @Mock NodeAdviseHelper nodeAdviseHelper;
  @Inject @InjectMocks @Spy IdentityNodeExecutionStrategy executionStrategy;

  private static final StepType TEST_STEP_TYPE =
      StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();

  private static final TriggeredBy triggeredBy = TriggeredBy.newBuilder()
                                                     .putExtraInfo("email", PRASHANTSHARMA)
                                                     .setIdentifier(PRASHANTSHARMA)
                                                     .setUuid(generateUuid())
                                                     .build();
  private static final ExecutionTriggerInfo triggerInfo =
      ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy).build();

  @Before
  public void setUp() {
    initializeLogging();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build())
                            .build();
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder()
                                            .name("Test Node")
                                            .uuid(generateUuid())
                                            .identifier("test")
                                            .stepType(TEST_STEP_TYPE)
                                            .build();
    doReturn(NodeExecution.builder().build())
        .when(executionStrategy)
        .createNodeExecutionInternal(ambiance, identityPlanNode, null, null, null, null);
    executionStrategy.runNode(ambiance, identityPlanNode, null);
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestStartExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String originalNodeExecutionId = generateUuid();
    String planId = generateUuid();

    IdentityPlanNode planNode = IdentityPlanNode.builder()
                                    .name("Test Node")
                                    .uuid(generateUuid())
                                    .identifier("test")
                                    .originalNodeExecutionId(originalNodeExecutionId)
                                    .stepType(TEST_STEP_TYPE)
                                    .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    AdviserResponse adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.END_PLAN).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .adviserResponse(adviserResponse)
                                      .originalNodeExecutionId(originalNodeExecutionId)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    // Test 1: Making status as skipped
    NodeExecution originalExecution = NodeExecution.builder()
                                          .uuid(originalNodeExecutionId)
                                          .ambiance(ambiance)
                                          .adviserResponse(adviserResponse)
                                          .status(Status.SKIPPED)
                                          .build();
    when(planService.fetchNode(planId, planNode.getUuid())).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.get(eq(originalNodeExecutionId))).thenReturn(originalExecution);
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), any(), any(), any())).thenReturn(nodeExecution);
    doReturn(nextStepHandler).when(adviseHandlerFactory).obtainHandler(any());

    executionStrategy.startExecution(ambiance);
    verify(executionStrategy, times(1)).processAdviserResponse(ambiance, adviserResponse);

    // Test 2: Making status as not Skipped and Setting mode type as leaf - SYNC
    originalExecution = NodeExecution.builder()
                            .uuid(originalNodeExecutionId)
                            .ambiance(ambiance)
                            .adviserResponse(adviserResponse)
                            .mode(ExecutionMode.SYNC)
                            .status(Status.SUCCEEDED)
                            .build();

    when(nodeExecutionService.get(eq(originalNodeExecutionId))).thenReturn(originalExecution);
    executionStrategy.startExecution(ambiance);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, originalNodeExecutionId);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, originalNodeExecutionId);

    // Test 3: calling Identity Step
    originalExecution = NodeExecution.builder()
                            .uuid(originalNodeExecutionId)
                            .ambiance(ambiance)
                            .adviserResponse(adviserResponse)
                            .status(Status.SUCCEEDED)
                            .build();
    when(nodeExecutionService.get(eq(originalNodeExecutionId))).thenReturn(originalExecution);
    assertThatCode(() -> executionStrategy.startExecution(ambiance)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestEndExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String originalNodeExecutionId = generateUuid();
    IdentityPlanNode planNode = IdentityPlanNode.builder()
                                    .name("Test Node")
                                    .uuid(generateUuid())
                                    .identifier("test")
                                    .originalNodeExecutionId(originalNodeExecutionId)
                                    .stepType(TEST_STEP_TYPE)
                                    .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    AdviserResponse adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.END_PLAN).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .adviserResponse(adviserResponse)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    executionStrategy.endNodeExecution(ambiance);
    verify(orchestrationEngine, times(1)).endNodeExecution(any());

    // Test 2: adding notifyId in nodeExecution

    NodeExecution nodeExecutionNotifyId = NodeExecution.builder()
                                              .uuid(nodeExecutionId)
                                              .ambiance(ambiance)
                                              .notifyId("notifyId")
                                              .adviserResponse(adviserResponse)
                                              .status(Status.INTERVENTION_WAITING)
                                              .mode(ExecutionMode.SYNC)
                                              .build();
    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), any())).thenReturn(nodeExecutionNotifyId);
    executionStrategy.endNodeExecution(ambiance);

    ArgumentCaptor<String> notifyId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<StepResponseNotifyData> stepResponseNotifyDataArgumentCaptor =
        ArgumentCaptor.forClass(StepResponseNotifyData.class);
    verify(waitNotifyEngine, times(1)).doneWith(notifyId.capture(), stepResponseNotifyDataArgumentCaptor.capture());
    assertThat(notifyId.getValue()).isEqualTo("notifyId");
    StepResponseNotifyData stepResponse = stepResponseNotifyDataArgumentCaptor.getValue();
    assertThat(stepResponse.getNodeUuid()).isEqualTo(planNode.getUuid());
    assertThat(stepResponse.getAdviserResponse()).isEqualTo(adviserResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.INTERVENTION_WAITING);
    assertThat(stepResponse.getIdentifier()).isEqualTo(planNode.getIdentifier());
    assertThat(stepResponse.getNodeExecutionId()).isEqualTo(nodeExecution.getUuid());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestResumeNodeExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String originalNodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder()
                                           .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                           .setRuntimeId(nodeExecutionId)
                                           .build())
                            .build();
    IdentityPlanNode planNode = IdentityPlanNode.builder()
                                    .name("Test Node")
                                    .uuid(generateUuid())
                                    .identifier("test")
                                    .originalNodeExecutionId(originalNodeExecutionId)
                                    .stepType(TEST_STEP_TYPE)
                                    .build();

    AdviserResponse adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.END_PLAN).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .adviserResponse(adviserResponse)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    assertThatCode(() -> executionStrategy.resumeNodeExecution(ambiance, new HashMap<>(), false))
        .doesNotThrowAnyException();

    doThrow(new RuntimeException()).when(identityNodeResumeHelper).resume(any(), any(), eq(false), any());
    assertThatCode(() -> executionStrategy.resumeNodeExecution(ambiance, new HashMap<>(), false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestProcessStepResponse() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String originalNodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder()
                                           .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                           .setRuntimeId(nodeExecutionId)
                                           .build())
                            .build();
    IdentityPlanNode planNode = IdentityPlanNode.builder()
                                    .name("Test Node")
                                    .uuid(generateUuid())
                                    .identifier("test")
                                    .originalNodeExecutionId(originalNodeExecutionId)
                                    .stepType(TEST_STEP_TYPE)
                                    .build();

    AdviserResponse adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.END_PLAN).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .adviserResponse(adviserResponse)
                                      .mode(ExecutionMode.SYNC)
                                      .build();
    doReturn(planNode).when(planService).fetchNode(ambiance.getPlanId(), nodeExecution.getNodeId());

    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(
        nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.SUCCEEDED, null, EnumSet.noneOf(Status.class)))
        .thenReturn(nodeExecution);
    doReturn(nextStepHandler).when(adviseHandlerFactory).obtainHandler(any());

    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().setStatus(Status.SUCCEEDED).build();
    assertThatCode(() -> executionStrategy.processStepResponse(ambiance, stepResponseProto)).doesNotThrowAnyException();

    doThrow(new RuntimeException()).when(nodeExecutionService).updateStatusWithOps(any(), any(), eq(null), any());
    assertThatCode(() -> executionStrategy.processStepResponse(ambiance, stepResponseProto)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testProcessStepResponseWithAdvisors() {
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    IdentityPlanNode planNode =
        IdentityPlanNode.builder()
            .adviserObtainments(Collections.singletonList(AdviserObtainment.getDefaultInstance()))
            .useAdviserObtainments(true)
            .uuid("planNodeId")
            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).nodeId(planNode.getUuid()).status(Status.RUNNING).build();
    StepResponseProto stepResponse = StepResponseProto.newBuilder().setStatus(Status.RUNNING).build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(nodeExecutionId, stepResponse.getStatus(), null, EnumSet.noneOf(Status.class));
    doReturn(planNode).when(planService).fetchNode("planId", "planNodeId");

    executionStrategy.processStepResponse(ambiance, stepResponse);
    verify(nodeAdviseHelper, times(1)).queueAdvisingEvent(nodeExecution, planNode, Status.RUNNING);
    verify(nodeExecutionService, times(0)).getWithFieldsIncluded(any(), any());
    verify(adviseHandlerFactory, times(0)).obtainHandler(any());
  }

  private static Map<String, String> prepareInputArgs() {
    return ImmutableMap.of("accountId", "kmpySmUISimoRrJL6NL73w", "appId", "XEsfW6D_RJm1IaGpDidD3g", "userId",
        triggeredBy.getUuid(), "userName", triggeredBy.getIdentifier(), "userEmail",
        triggeredBy.getExtraInfoOrThrow("email"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateNodeExecution() {
    String originalNodeExecutionId = generateUuid();
    long startTs = System.currentTimeMillis();
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                           .setStartTs(startTs)
                                           .setRuntimeId(uuid)
                                           .build())
                            .build();
    IdentityPlanNode node = IdentityPlanNode.builder().originalNodeExecutionId("OID").build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(uuid)
                                      .ambiance(ambiance)
                                      .levelCount(1)
                                      .status(Status.QUEUED)
                                      .unitProgresses(new ArrayList<>())
                                      .startTs(startTs)
                                      .originalNodeExecutionId(originalNodeExecutionId)
                                      .notifyId("NID")
                                      .parentId("PaID")
                                      .previousId("PrID")
                                      .levelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance))
                                      .skipGraphType(SkipType.NOOP)
                                      .build();
    when(nodeExecutionService.get(anyString()))
        .thenReturn(NodeExecution.builder().uuid(originalNodeExecutionId).build());
    when(nodeExecutionService.save(any(NodeExecution.class))).thenReturn(nodeExecution);
    doNothing().when(pmsGraphStepDetailsService).copyStepDetailsForRetry(anyString(), anyString(), anyString());
    on(identityNodeExecutionStrategyHelper).set("nodeExecutionService", nodeExecutionService);
    on(identityNodeExecutionStrategyHelper).set("pmsGraphStepDetailsService", pmsGraphStepDetailsService);
    doCallRealMethod().when(identityNodeExecutionStrategyHelper).createNodeExecution(any(), any(), any(), any(), any());
    NodeExecution nodeExecution1 =
        executionStrategy.createNodeExecutionInternal(ambiance, node, null, "NID", "PaID", "PrID");
    assertEquals(nodeExecution1, nodeExecution);
    verify(pmsGraphStepDetailsService, times(1)).copyStepDetailsForRetry(anyString(), anyString(), anyString());
    ArgumentCaptor<NodeExecution> mCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    verify(nodeExecutionService).save(mCaptor.capture());
    NodeExecution saveNodeExecution = mCaptor.getValue();
    assertThat(saveNodeExecution.getOriginalNodeExecutionId()).isEqualTo(nodeExecution.getOriginalNodeExecutionId());
    assertThat(saveNodeExecution.getParentId()).isEqualTo(nodeExecution.getParentId());
    assertThat(saveNodeExecution.getAmbiance()).isEqualTo(nodeExecution.getAmbiance());
    assertThat(saveNodeExecution.getNotifyId()).isEqualTo(nodeExecution.getNotifyId());
    assertThat(saveNodeExecution.getNodeId()).isEqualTo(nodeExecution.getNodeId());
    assertThat(saveNodeExecution.getNodeType()).isEqualTo(nodeExecution.getNodeType());
    assertThat(saveNodeExecution.getPreviousId()).isEqualTo(nodeExecution.getPreviousId());
    assertThat(saveNodeExecution.getIdentifier()).isEqualTo(nodeExecution.getIdentifier());
    assertThat(saveNodeExecution.getStepType()).isEqualTo(nodeExecution.getStepType());
    assertThat(saveNodeExecution.getResolvedParams()).isEqualTo(nodeExecution.getResolvedParams());
    assertThat(saveNodeExecution.getStatus()).isEqualTo(nodeExecution.getStatus());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessAdviserResponseWithNullAdviserResponse() {
    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build()).build();
    doNothing().when(executionStrategy).endNodeExecution(ambiance);
    executionStrategy.processAdviserResponse(ambiance, null);
    verify(executionStrategy, times(1)).endNodeExecution(ambiance);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessAdviserResponse() {
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(uuid).build()).build();
    AdviserResponse adviserResponse = AdviserResponse.newBuilder().build();
    doNothing().when(executionStrategy).endNodeExecution(ambiance);
    executionStrategy.processAdviserResponse(ambiance, adviserResponse);
    verify(executionStrategy, times(1)).endNodeExecution(ambiance);
    adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.NEXT_STEP).build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(uuid).build();
    doReturn(nodeExecution).when(nodeExecutionService).update(any(), any());
    doReturn(nextStepHandler).when(adviseHandlerFactory).obtainHandler(AdviseType.NEXT_STEP);
    doNothing().when(nextStepHandler).handleAdvise(nodeExecution, adviserResponse);
    executionStrategy.processAdviserResponse(ambiance, adviserResponse);
    verify(nextStepHandler, times(1)).handleAdvise(nodeExecution, adviserResponse);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleLeafNodes() {
    doNothing().when(executionStrategy).processAdviserResponse(any(), any());
    String nodeUuid = generateUuid();
    List<ExecutableResponse> executableResponse = Arrays.asList(ExecutableResponse.newBuilder().build());
    executionStrategy.handleLeafNodes(Ambiance.newBuilder().build(), NodeExecution.builder().uuid(nodeUuid).build(),
        NodeExecution.builder().status(Status.ABORTED).executableResponses(executableResponse).build());
    verify(executionStrategy, times(1)).processAdviserResponse(any(), any());
    verify(identityNodeExecutionStrategyHelper, times(1)).copyNodeExecutionsForRetriedNodes(any(), any());
    ArgumentCaptor<String> captureUuid = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Status> captorStatus = ArgumentCaptor.forClass(Status.class);
    ArgumentCaptor<Consumer> updateCapture = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(captureUuid.capture(), captorStatus.capture(), updateCapture.capture(), any());

    assertThat(captureUuid.getValue()).isEqualTo(nodeUuid);
    assertThat(updateCapture.getValue()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleLeafNodesWithPipelineStageNode() {
    StepType pipelineStageStepType =
        StepType.newBuilder().setType(OrchestrationStepTypes.PIPELINE_STAGE).setStepCategory(StepCategory.STEP).build();
    String nodeUuid = generateUuid();
    List<ExecutableResponse> executableResponse = Arrays.asList(ExecutableResponse.newBuilder().build());
    doNothing().when(executionStrategy).processAdviserResponse(any(), any());
    executionStrategy.handleLeafNodes(Ambiance.newBuilder().build(), NodeExecution.builder().uuid(nodeUuid).build(),
        NodeExecution.builder().status(Status.ABORTED).executableResponses(executableResponse).build());
    verify(executionStrategy, times(1)).processAdviserResponse(any(), any());
    verify(identityNodeExecutionStrategyHelper, times(1)).copyNodeExecutionsForRetriedNodes(any(), any());
    ArgumentCaptor<String> captureUuid = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Status> captorStatus = ArgumentCaptor.forClass(Status.class);
    ArgumentCaptor<Consumer> updateCapture = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(captureUuid.capture(), captorStatus.capture(), updateCapture.capture(), any());

    assertThat(captureUuid.getValue()).isEqualTo(nodeUuid);
    assertThat(updateCapture.getValue()).isNotNull();
  }
}
