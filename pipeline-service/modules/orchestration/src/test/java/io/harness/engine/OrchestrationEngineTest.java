/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.plan.NodeType.PLAN_NODE;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.execution.strategy.NodeExecutionStrategyFactory;
import io.harness.engine.pms.execution.strategy.plan.PlanExecutionStrategy;
import io.harness.engine.pms.execution.strategy.plannode.PlanNodeExecutionStrategy;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.plan.NodeType;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class OrchestrationEngineTest extends OrchestrationTestBase {
  @Mock PlanNodeExecutionStrategy planNodeExecutionStrategy;
  @Mock PlanExecutionStrategy planExecutionStrategy;
  @Mock NodeExecutionStrategyFactory factory;
  @Mock PlanService planService;
  @Inject @InjectMocks private OrchestrationEngine orchestrationEngine;

  @Before
  public void setup() {
    doReturn(planExecutionStrategy).when(factory).obtainStrategy(NodeType.PLAN);
    doReturn(planNodeExecutionStrategy).when(factory).obtainStrategy(PLAN_NODE);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Plan plan = Plan.builder().build();
    when(planExecutionStrategy.runNode(ambiance, plan, null)).thenReturn(PlanExecution.builder().build());
    orchestrationEngine.runNode(ambiance, plan, null);
    verify(planExecutionStrategy).runNode(ambiance, plan, null);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNextNode() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    PlanNode node = PlanNode.builder().build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    when(planNodeExecutionStrategy.runNextNode(ambiance, node, nodeExecution, null))
        .thenReturn(NodeExecution.builder().build());
    orchestrationEngine.runNextNode(ambiance, node, nodeExecution, null);
    verify(planNodeExecutionStrategy).runNextNode(ambiance, node, nodeExecution, null);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartNodeExecution() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    doNothing().when(planNodeExecutionStrategy).startExecution(ambiance);
    orchestrationEngine.startNodeExecution(ambiance);
    verify(planNodeExecutionStrategy).startExecution(eq(ambiance));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitationResponse() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    FacilitatorResponseProto fr = FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.SYNC).build();
    doNothing().when(planNodeExecutionStrategy).processFacilitationResponse(ambiance, fr);
    orchestrationEngine.processFacilitatorResponse(ambiance, fr);
    verify(planNodeExecutionStrategy).processFacilitationResponse(eq(ambiance), eq(fr));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessStepResponse() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    StepResponseProto sr = StepResponseProto.newBuilder().setStatus(Status.FAILED).build();
    doNothing().when(planNodeExecutionStrategy).processStepResponse(ambiance, sr);
    orchestrationEngine.processStepResponse(ambiance, sr);
    verify(planNodeExecutionStrategy).processStepResponse(eq(ambiance), eq(sr));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessAdviserResponse() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    AdviserResponse ar =
        AdviserResponse.newBuilder().setEndPlanAdvise(EndPlanAdvise.newBuilder().setIsAbort(false).build()).build();
    doNothing().when(planNodeExecutionStrategy).processAdviserResponse(ambiance, ar);
    orchestrationEngine.processAdviserResponse(ambiance, ar);
    verify(planNodeExecutionStrategy).processAdviserResponse(eq(ambiance), eq(ar));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleError() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    Exception ex = new InvalidRequestException("INVALID_REQUEST");
    doNothing().when(planNodeExecutionStrategy).handleError(ambiance, ex);
    orchestrationEngine.handleError(ambiance, ex);
    verify(planNodeExecutionStrategy).handleError(eq(ambiance), eq(ex));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConcludeExecution() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    doNothing()
        .when(planNodeExecutionStrategy)
        .concludeExecution(ambiance, Status.RUNNING, Status.SUCCEEDED, EnumSet.noneOf(Status.class));
    orchestrationEngine.concludeNodeExecution(ambiance, Status.RUNNING, Status.SUCCEEDED, EnumSet.noneOf(Status.class));
    verify(planNodeExecutionStrategy)
        .concludeExecution(eq(ambiance), eq(Status.RUNNING), eq(Status.SUCCEEDED), eq(EnumSet.noneOf(Status.class)));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestEndNodeExecution() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    doNothing().when(planNodeExecutionStrategy).endNodeExecution(ambiance);
    orchestrationEngine.endNodeExecution(ambiance);
    verify(planNodeExecutionStrategy).endNodeExecution(eq(ambiance));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleSdkResponseEvent() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    SdkResponseEventProto eventProto = SdkResponseEventProto.newBuilder()
                                           .setAmbiance(ambiance)
                                           .setSdkResponseEventType(SdkResponseEventType.HANDLE_FACILITATE_RESPONSE)
                                           .build();
    doNothing().when(planNodeExecutionStrategy).handleSdkResponseEvent(eventProto);
    orchestrationEngine.handleSdkResponseEvent(eventProto);
    verify(planNodeExecutionStrategy).handleSdkResponseEvent(eq(eventProto));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResume() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setNodeType(PLAN_NODE.toString()).build())
            .build();
    doNothing().when(planNodeExecutionStrategy).startExecution(ambiance);
    Map<String, ResponseDataProto> response = new HashMap<>();
    orchestrationEngine.resumeNodeExecution(ambiance, response, false);
    verify(planNodeExecutionStrategy).resumeNodeExecution(eq(ambiance), eq(response), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestInitiateNode() {
    String planExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    String runtimeId = generateUuid();

    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).setPlanId(planId).build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(StepType.newBuilder().setType("TEST").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .build();

    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);

    orchestrationEngine.initiateNode(ambiance, planNode.getUuid(), runtimeId, null);

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    verify(planNodeExecutionStrategy).runNode(ambianceCaptor.capture(), eq(planNode), eq(null));
    Ambiance captured = ambianceCaptor.getValue();

    assertThat(captured.getLevelsCount()).isEqualTo(1);
    assertThat(captured.getLevels(0).getSetupId()).isEqualTo(planNode.getUuid());
    assertThat(captured.getLevels(0).getRuntimeId()).isEqualTo(runtimeId);
  }
}
