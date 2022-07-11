/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identitynode;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.execution.strategy.identity.IdentityNodeExecutionStrategy;
import io.harness.engine.pms.execution.strategy.identity.IdentityNodeResumeHelper;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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
        .createNodeExecution(ambiance, identityPlanNode, null, null, null, null);
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
                                      .planNode(planNode)
                                      .originalNodeExecutionId(originalNodeExecutionId)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    // Test 1: Making status as skipped
    NodeExecution originalExecution = NodeExecution.builder()
                                          .uuid(originalNodeExecutionId)
                                          .ambiance(ambiance)
                                          .adviserResponse(adviserResponse)
                                          .planNode(planNode)
                                          .status(Status.SKIPPED)
                                          .build();
    when(planService.fetchNode(planId, planNode.getUuid())).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.get(eq(originalNodeExecutionId))).thenReturn(originalExecution);
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(Status.SKIPPED), any(), any()))
        .thenReturn(nodeExecution);

    executionStrategy.startExecution(ambiance);
    verify(executionStrategy, times(1)).processAdviserResponse(ambiance, adviserResponse);

    // Test 2: Making status as not Skipped and Setting mode type as leaf - SYNC
    originalExecution = NodeExecution.builder()
                            .uuid(originalNodeExecutionId)
                            .ambiance(ambiance)
                            .adviserResponse(adviserResponse)
                            .planNode(planNode)
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
                            .planNode(planNode)
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
                                      .planNode(planNode)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    executionStrategy.endNodeExecution(ambiance);
    verify(orchestrationEngine, times(1)).endNodeExecution(any());

    // Test 2: adding notifyId in nodeExecution

    NodeExecution nodeExecutionNotifyId = NodeExecution.builder()
                                              .uuid(nodeExecutionId)
                                              .ambiance(ambiance)
                                              .notifyId("notifyId")
                                              .adviserResponse(adviserResponse)
                                              .planNode(planNode)
                                              .status(Status.INTERVENTION_WAITING)
                                              .mode(ExecutionMode.SYNC)
                                              .build();
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecutionNotifyId);
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
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
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
                                      .planNode(planNode)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
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
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
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
                                      .planNode(planNode)
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(
        nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.SUCCEEDED, null, EnumSet.noneOf(Status.class)))
        .thenReturn(nodeExecution);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().setStatus(Status.SUCCEEDED).build();
    assertThatCode(() -> executionStrategy.processStepResponse(ambiance, stepResponseProto)).doesNotThrowAnyException();
  }

  private static Map<String, String> prepareInputArgs() {
    return ImmutableMap.of("accountId", "kmpySmUISimoRrJL6NL73w", "appId", "XEsfW6D_RJm1IaGpDidD3g", "userId",
        triggeredBy.getUuid(), "userName", triggeredBy.getIdentifier(), "userEmail",
        triggeredBy.getExtraInfoOrThrow("email"));
  }
}
