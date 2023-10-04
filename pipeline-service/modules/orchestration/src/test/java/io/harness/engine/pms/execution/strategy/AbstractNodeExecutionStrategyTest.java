/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.execution.SdkResponseProcessorFactory;
import io.harness.engine.pms.execution.modifier.ambiance.AmbianceModifierFactory;
import io.harness.event.handlers.HandleStepResponseRequestProcessor;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class AbstractNodeExecutionStrategyTest extends CategoryTest {
  @Mock ExecutorService executorService;
  HandleStepResponseRequestProcessor handleStepResponseRequestProcessor;
  @Mock SdkResponseProcessorFactory sdkResponseProcessorFactory;
  @Mock AmbianceModifierFactory ambianceModifierFactory;
  AbstractNodeExecutionStrategy abstractNodeExecutionStrategy;
  @Mock OrchestrationEngine orchestrationEngine;
  @Mock NodeAdviseHelper nodeAdviseHelper;
  Ambiance ambiance;
  String accountId = generateUuid();
  Plan node;
  PlanNode planNode;
  NodeExecutionMetadata nodeExecutionMetadata;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.openMocks(this);
    ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    node = Plan.builder().build();
    nodeExecutionMetadata = new NodeExecutionMetadata();
    handleStepResponseRequestProcessor = mock(HandleStepResponseRequestProcessor.class);
    doReturn(handleStepResponseRequestProcessor)
        .when(sdkResponseProcessorFactory)
        .getHandler(SdkResponseEventType.HANDLE_STEP_RESPONSE);
    planNode = PlanNode.builder().executionInputTemplate("setup").build();
    abstractNodeExecutionStrategy = spy(AbstractNodeExecutionStrategy.class);
    FieldUtils.writeField(abstractNodeExecutionStrategy, "ambianceModifierFactory", ambianceModifierFactory, true);
    when(abstractNodeExecutionStrategy.createNodeExecution(ambiance, planNode, nodeExecutionMetadata, "", "", ""))
        .thenReturn(NodeExecution.builder().uuid(accountId).build());
    FieldUtils.writeField(
        abstractNodeExecutionStrategy, "sdkResponseProcessorFactory", sdkResponseProcessorFactory, true);
    FieldUtils.writeField(abstractNodeExecutionStrategy, "orchestrationEngine", orchestrationEngine, true);
    FieldUtils.writeField(abstractNodeExecutionStrategy, "executorService", executorService, true);
    FieldUtils.writeField(abstractNodeExecutionStrategy, "nodeAdviseHelper", nodeAdviseHelper, true);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRunNode() {
    abstractNodeExecutionStrategy.runNode(ambiance, node, nodeExecutionMetadata);
    verify(abstractNodeExecutionStrategy, times(1))
        .runNode(ambiance, node, nodeExecutionMetadata, InitiateMode.CREATE_AND_START);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRunNodeWithInitiateModeCreateAndStart() {
    abstractNodeExecutionStrategy.runNode(ambiance, node, nodeExecutionMetadata, InitiateMode.CREATE_AND_START);
    verify(abstractNodeExecutionStrategy, times(1))
        .createAndRunNodeExecution(ambiance, node, nodeExecutionMetadata, null, null, null);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRunNodeWithInitiateModeCreate() {
    abstractNodeExecutionStrategy.runNode(ambiance, node, nodeExecutionMetadata, InitiateMode.CREATE);
    verify(abstractNodeExecutionStrategy, times(1))
        .createNodeExecution(ambiance, node, nodeExecutionMetadata, null, null, null);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRunNextNode() {
    abstractNodeExecutionStrategy.runNextNode(ambiance, node, NodeExecution.builder().build(), nodeExecutionMetadata);
    verify(abstractNodeExecutionStrategy, times(1))
        .createAndRunNodeExecution(ambiance, node, nodeExecutionMetadata, null, null, null);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateAndRunNodeExecution() {
    abstractNodeExecutionStrategy.createAndRunNodeExecution(ambiance, planNode, nodeExecutionMetadata, "", "", "");
    verify(executorService, times(1)).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateAndRunNodeExecutionWithEmptyExecutionInputTemplate() {
    abstractNodeExecutionStrategy.createAndRunNodeExecution(ambiance, planNode, nodeExecutionMetadata, "", "", "");
    verify(executorService, times(1)).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleSdkResponseEvent() {
    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder().setSdkResponseEventType(SdkResponseEventType.HANDLE_STEP_RESPONSE).build();
    abstractNodeExecutionStrategy.handleSdkResponseEvent(event);
    verify(handleStepResponseRequestProcessor, times(1)).handleEvent(event);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessOrQueueAdvisingEvent() {
    NodeExecution nodeExecution = NodeExecution.builder().uuid("uuid").build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid("planNodeId")
            .identifier("test")
            .stepType(StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build())
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .serviceName("CD")
            .build();
    abstractNodeExecutionStrategy.processOrQueueAdvisingEvent(nodeExecution, planNode, Status.RUNNING);
    verify(nodeAdviseHelper, times(1)).queueAdvisingEvent(eq(nodeExecution), eq(planNode), eq(Status.RUNNING));
  }
}
