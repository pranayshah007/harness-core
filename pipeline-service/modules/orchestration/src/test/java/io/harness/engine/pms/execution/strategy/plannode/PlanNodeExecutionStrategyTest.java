/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.WaitForExecutionInputHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.advise.AdviseHandlerFactory;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.advise.handlers.NextStepHandler;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.pms.execution.SdkResponseProcessorFactory;
import io.harness.engine.pms.execution.strategy.EndNodeExecutionHelper;
import io.harness.engine.pms.resume.NodeResumeHelper;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.event.handlers.AdviserResponseRequestProcessor;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.expression.common.ExpressionMode;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.AdviserResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.transaction.CannotCreateTransactionException;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanNodeExecutionStrategyTest extends OrchestrationTestBase {
  @Mock @Named("EngineExecutorService") ExecutorService executorService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private FacilitateEventPublisher facilitateEventPublisher;
  @Mock private EndNodeExecutionHelper endNodeExecutionHelper;
  @Mock private NodeResumeHelper resumeHelper;
  @Mock private NodeStartHelper startHelper;
  @Mock private NodeAdviseHelper adviseHelper;
  @Mock private InterruptService interruptService;
  @Mock private PlanService planService;
  @Mock private SdkResponseProcessorFactory processorFactory;
  @Mock private AdviserResponseRequestProcessor adviserResponseProcessor;
  @Mock private WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject @InjectMocks @Spy PlanNodeExecutionStrategy executionStrategy;
  @Mock private NextStepHandler nextStepHandler;
  @Mock private AdviseHandlerFactory adviseHandlerFactory;
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  private static final StepType TEST_STEP_TYPE =
      StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();

  private static final TriggeredBy triggeredBy =
      TriggeredBy.newBuilder().putExtraInfo("email", PRASHANT).setIdentifier(PRASHANT).setUuid(generateUuid()).build();
  private static final ExecutionTriggerInfo triggerInfo =
      ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy).build();

  @Before
  public void setUp() {
    initializeLogging();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build())
                            .build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(generateUuid())
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .build();

    doReturn(NodeExecution.builder().build())
        .when(executionStrategy)
        .createNodeExecution(ambiance, planNode, null, null, null, null);
    doReturn(false).when(pmsFeatureFlagService).isEnabled(any(), any(FeatureName.class));
    executionStrategy.runNode(ambiance, planNode, null);
    verify(executorService).submit(any(Runnable.class));
    doReturn(NodeExecution.builder().uuid("fda").build()).when(nodeExecutionService).save(any());
    // waitForExecutionInputHelper.waitForExecutionInputOrStart() will not be called.FF is off.
    verify(waitForExecutionInputHelper, never()).waitForExecutionInput(any(), any(), any());

    doReturn(true).when(pmsFeatureFlagService).isEnabled(any(), any(FeatureName.class));
    executionStrategy.runNode(ambiance, planNode, null);
    verify(executorService, times(2)).submit(any(Runnable.class));
    // waitForExecutionInputHelper.waitForExecutionInputOrStart() will not be called.FF is on but executionInputTemplate
    // is empty.
    verify(waitForExecutionInputHelper, never()).waitForExecutionInput(any(), any(), any());

    planNode = PlanNode.builder()
                   .name("Test Node")
                   .uuid(generateUuid())
                   .identifier("test")
                   .stepType(TEST_STEP_TYPE)
                   .executionInputTemplate("executionInputTemplate")
                   .facilitatorObtainment(
                       FacilitatorObtainment.newBuilder()
                           .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                           .build())
                   .build();

    executionStrategy.runNode(ambiance, planNode, null);
    // executorService.submit will not be called this time because execution will pause for user input.
    verify(executorService, times(3)).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithCustomFacilitator() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .name("Test Node")
                            .uuid(planNodeId)
                            .identifier("test")
                            .stepType(TEST_STEP_TYPE)
                            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                                       .setType(FacilitatorType.newBuilder().setType("CUSTOM").build())
                                                       .build())
                            .serviceName("CD")
                            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).planNode(planNode).build();

    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);

    executionStrategy.startExecution(ambiance);
    verify(facilitateEventPublisher).publishEvent(eq(ambiance), eq(planNode));
    verify(executionStrategy, times(0)).processFacilitationResponse(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).planNode(planNode).build();
    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponse(any(), any());

    executionStrategy.startExecution(ambiance);
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<FacilitatorResponseProto> facilitatorResponseCaptor =
        ArgumentCaptor.forClass(FacilitatorResponseProto.class);
    verify(executionStrategy)
        .processFacilitationResponse(ambianceCaptor.capture(), facilitatorResponseCaptor.capture());

    assertThat(ambianceCaptor.getValue().getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(facilitatorResponseCaptor.getValue().getExecutionMode()).isEqualTo(ExecutionMode.SYNC);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithWrongExpressionStepParams() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PmsStepParameters stepParameters = PmsStepParameters.parse(Map.of("name", "<+abc>"));

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .stepParameters(stepParameters)
            .whenCondition("\"true\" == \"false\"")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).planNode(planNode).build();

    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponse(any(), any());

    executionStrategy.startExecution(ambiance);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithWrongExpressionStepParamsAndNotSkip() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PmsStepParameters stepParameters = PmsStepParameters.parse(Map.of("name", "<+abc>"));

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .stepParameters(stepParameters)
            .expressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).planNode(planNode).build();
    doThrow(new InvalidRequestException("Exception eval failure"))
        .when(pmsEngineExpressionService)
        .resolve(ambiance, stepParameters, ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);

    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponse(any(), any());
    executionStrategy.startExecution(ambiance);

    verify(executionStrategy).handleError(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResumeNodeExecutionWithStatusRunning() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).status(Status.RUNNING).build();
    Map<String, ResponseDataProto> responseMap = ImmutableMap.of(
        generateUuid(), ResponseDataProto.newBuilder().setResponse(ByteString.copyFromUtf8(generateUuid())).build());
    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), eq(NodeProjectionUtils.fieldsForResume)))
        .thenReturn(nodeExecution);
    executionStrategy.resumeNodeExecution(ambiance, responseMap, false);
    verify(resumeHelper).resume(eq(nodeExecution), eq(responseMap), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResumeNodeExecutionWithStatusAborted() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).status(Status.ABORTED).build();
    Map<String, ResponseDataProto> responseMap = ImmutableMap.of(
        generateUuid(), ResponseDataProto.newBuilder().setResponse(ByteString.copyFromUtf8(generateUuid())).build());
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    executionStrategy.resumeNodeExecution(ambiance, responseMap, false);
    verify(resumeHelper, times(0)).resume(eq(nodeExecution), eq(responseMap), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponse() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();

    when(nodeExecutionService.update(eq(nodeExecutionId), any()))
        .thenReturn(NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .status(Status.QUEUED)
                        .mode(ExecutionMode.ASYNC)
                        .build());
    when(interruptService.checkInterruptsPreInvocation(eq(planExecutionId), eq(nodeExecutionId)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponseWithInterrupt() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();

    when(nodeExecutionService.update(eq(nodeExecutionId), any()))
        .thenReturn(NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .status(Status.QUEUED)
                        .mode(ExecutionMode.ASYNC)
                        .build());
    when(interruptService.checkInterruptsPreInvocation(eq(planExecutionId), eq(nodeExecutionId)))
        .thenReturn(ExecutionCheck.builder().proceed(false).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper, times(0)).startNode(eq(ambiance), eq(facilitatorResponse));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConcludeNodeExecutionNoAdvisers() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .name("Test Node")
                            .uuid(planNodeId)
                            .identifier("test")
                            .stepType(TEST_STEP_TYPE)
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder()
                                                    .uuid(nodeExecutionId)
                                                    .ambiance(ambiance)
                                                    .planNode(planNode)
                                                    .status(Status.INTERVENTION_WAITING)
                                                    .mode(ExecutionMode.ASYNC);

    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), any(), any(), any()))
        .thenReturn(nodeExecutionBuilder.status(Status.FAILED).build());
    doNothing().when(executionStrategy).endNodeExecution(ambiance);

    executionStrategy.concludeExecution(
        ambiance, Status.FAILED, Status.INTERVENTION_WAITING, EnumSet.noneOf(Status.class));
    verify(executionStrategy).endNodeExecution(eq(ambiance));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConcludeNodeExecutionWithAdvisers() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .serviceName("CD")
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder()
                                                    .uuid(nodeExecutionId)
                                                    .planNode(planNode)
                                                    .ambiance(ambiance)
                                                    .status(Status.INTERVENTION_WAITING)
                                                    .mode(ExecutionMode.ASYNC);
    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    NodeExecution updated = nodeExecutionBuilder.status(Status.FAILED).endTs(System.currentTimeMillis()).build();
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(Status.FAILED), any(), any()))
        .thenReturn(updated);

    executionStrategy.concludeExecution(
        ambiance, Status.FAILED, Status.INTERVENTION_WAITING, EnumSet.noneOf(Status.class));
    verify(adviseHelper).queueAdvisingEvent(eq(updated), eq(planNode), eq(Status.INTERVENTION_WAITING));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleErrorWithExceptionManager() {
    ArgumentCaptor<Ambiance> nExCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<StepResponseProto> sCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    NodeExecution nodeExecution = NodeExecution.builder().uuid(generateUuid()).build();
    when(nodeExecutionService.get(nodeExecution.getUuid())).thenReturn(nodeExecution);
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecution.getUuid()).build())
                            .build();
    CannotCreateTransactionException ex = new CannotCreateTransactionException("Cannot Create Transaction");
    executionStrategy.handleError(ambiance, ex);
    verify(executionStrategy).handleStepResponseInternal(nExCaptor.capture(), sCaptor.capture());
    assertThat(AmbianceUtils.obtainCurrentRuntimeId(nExCaptor.getValue())).isEqualTo(nodeExecution.getUuid());
    assertThat(sCaptor.getValue().getFailureInfo()).isNotNull();
    assertThat(sCaptor.getValue().getFailureInfo().getErrorMessage()).isEqualTo("Cannot Create Transaction");
    assertThat(sCaptor.getValue().getFailureInfo().getFailureTypesList().get(0)).isEqualTo(APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void handleStepResponseWithError() {
    String nodeExecutionId = generateUuid();
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("planExecutionId")
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
    when(nodeExecutionService.get(nodeExecution.getUuid())).thenReturn(nodeExecution);
    doThrow(new InvalidRequestException("test"))
        .when(endNodeExecutionHelper)
        .endNodeExecutionWithNoAdvisers(ambiance, stepResponseProto);
    doNothing().when(executionStrategy).handleError(any(), any());
    executionStrategy.processStepResponse(ambiance, stepResponseProto);
    verify(executionStrategy).handleError(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleSdkResponseWithoutError() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).setPlanId(generateUuid()).build();
    AdviserResponseRequest request = AdviserResponseRequest.newBuilder()
                                         .setAdviserResponse(AdviserResponse.newBuilder()
                                                                 .setType(AdviseType.END_PLAN)
                                                                 .setEndPlanAdvise(EndPlanAdvise.newBuilder().build())
                                                                 .build())
                                         .build();
    SdkResponseEventProto event = SdkResponseEventProto.newBuilder()
                                      .setAmbiance(ambiance)
                                      .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
                                      .setAdviserResponseRequest(request)
                                      .build();
    doReturn(adviserResponseProcessor).when(processorFactory).getHandler(SdkResponseEventType.HANDLE_ADVISER_RESPONSE);
    doNothing().when(adviserResponseProcessor).handleEvent(eq(event));
    executionStrategy.handleSdkResponseEvent(event);
    verify(adviserResponseProcessor, times(1)).handleEvent(eq(event));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleSdkResponseWithError() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).setPlanId(generateUuid()).build();
    AdviserResponseRequest request = AdviserResponseRequest.newBuilder()
                                         .setAdviserResponse(AdviserResponse.newBuilder()
                                                                 .setType(AdviseType.END_PLAN)
                                                                 .setEndPlanAdvise(EndPlanAdvise.newBuilder().build())
                                                                 .build())
                                         .build();
    SdkResponseEventProto event = SdkResponseEventProto.newBuilder()
                                      .setAmbiance(ambiance)
                                      .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
                                      .setAdviserResponseRequest(request)
                                      .build();

    InvalidRequestException ex = new InvalidRequestException("Invalid Request");
    doReturn(adviserResponseProcessor).when(processorFactory).getHandler(SdkResponseEventType.HANDLE_ADVISER_RESPONSE);
    doThrow(ex).when(adviserResponseProcessor).handleEvent(eq(event));
    executionStrategy.handleSdkResponseEvent(event);
    verify(adviserResponseProcessor, times(1)).handleEvent(eq(event));
    verify(executionStrategy, times(1)).handleError(eq(ambiance), eq(ex));
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
    long startTs = System.currentTimeMillis();
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setStartTs(startTs).setRuntimeId(uuid).build())
                            .build();
    PlanNode node = PlanNode.builder().name("PLAN_NODE").identifier("plan_node").build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(uuid)
                                      .planNode(node)
                                      .ambiance(ambiance)
                                      .levelCount(1)
                                      .status(Status.QUEUED)
                                      .unitProgresses(new ArrayList<>())
                                      .name(AmbianceUtils.modifyIdentifier(ambiance, node.getName()))
                                      .identifier(AmbianceUtils.modifyIdentifier(ambiance, node.getIdentifier()))
                                      .notifyId("NID")
                                      .parentId("PaID")
                                      .previousId("PrID")
                                      .build();
    when(nodeExecutionService.save(any(NodeExecution.class))).thenReturn(nodeExecution);
    NodeExecution nodeExecution1 = executionStrategy.createNodeExecution(ambiance, node, null, "NID", "PaID", "PrID");
    assertEquals(nodeExecution1, nodeExecution);
    ArgumentCaptor<NodeExecution> mCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    verify(nodeExecutionService).save(mCaptor.capture());
    assertThat(mCaptor.getValue().toString()).isEqualTo(nodeExecution.toString());
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
    doReturn(NodeExecution.builder().build()).when(nodeExecutionService).get(uuid);
    doReturn(nextStepHandler).when(adviseHandlerFactory).obtainHandler(AdviseType.NEXT_STEP);
    doNothing().when(nextStepHandler).handleAdvise(any(), any());
    executionStrategy.processAdviserResponse(ambiance, adviserResponse);
    verify(nextStepHandler, times(1)).handleAdvise(any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEndNodeExecutionWithEmptyNotifyId() {
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(uuid).build()).build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    executionStrategy.endNodeExecution(ambiance);
    verify(orchestrationEngine, times(1)).endNodeExecution(any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEndNodeExecution() {
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(uuid).build()).build();
    String notifyId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder().notifyId(notifyId).build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    executionStrategy.endNodeExecution(ambiance);
    verify(waitNotifyEngine, times(1)).doneWith(any(), any(StepResponseNotifyData.class));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternalWithEmptyAdviserObtainments() {
    String uuid = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().build();
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanId(planId).addLevels(Level.newBuilder().setSetupId(uuid).build()).build();
    doReturn(planNode).when(planService).fetchNode(planId, uuid);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(endNodeExecutionHelper, times(1)).endNodeExecutionWithNoAdvisers(ambiance, stepResponseProto);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternalWithUpdatedNodeExecutionNull() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, setupId);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    doReturn(null).when(endNodeExecutionHelper).handleStepResponsePreAdviser(ambiance, stepResponseProto);
    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(adviseHelper, times(0)).queueAdvisingEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternal() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, setupId);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    doReturn(NodeExecution.builder().build())
        .when(endNodeExecutionHelper)
        .handleStepResponsePreAdviser(ambiance, stepResponseProto);
    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(adviseHelper, times(1)).queueAdvisingEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testPerformPreFacilitationChecksWithIsRetryNotEqualToZero() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).setRetryIndex(1).build())
            .build();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    ExecutionCheck executionCheck = executionStrategy.performPreFacilitationChecks(ambiance, planNode);
    assertTrue(executionCheck.isProceed());
    assertEquals(executionCheck.getReason(), "Node is retried.");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testResolveParameters() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).setRetryIndex(1).build())
            .build();
    PlanNode planNode = PlanNode.builder()
                            .expressionMode(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)
                            .stepParameters(new PmsStepParameters())
                            .build();
    doReturn(new Object()).when(pmsEngineExpressionService).resolve(ambiance, new PmsStepParameters(), true);
    MockedStatic<PmsStepParameters> utilities = Mockito.mockStatic(PmsStepParameters.class);
    utilities.when(() -> PmsStepParameters.parse(any(OrchestrationMap.class))).thenReturn(new PmsStepParameters());
    MockedStatic<OrchestrationMapBackwardCompatibilityUtils> utilities1 =
        Mockito.mockStatic(OrchestrationMapBackwardCompatibilityUtils.class);
    utilities1
        .when(() -> OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(any(PmsStepParameters.class)))
        .thenReturn(new OrchestrationMap());
    executionStrategy.resolveParameters(ambiance, planNode);
    verify(pmsEngineExpressionService, times(1))
        .resolve(ambiance, planNode.getStepParameters(), planNode.getExpressionMode());
    verify(nodeExecutionService, times(1)).updateV2(any(), any());
  }
}
