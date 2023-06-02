/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.plan.ExecutionMode.NORMAL;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.MarkAsFailureAdvise;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class MarkAsFailureAdviseHandlerTest extends CategoryTest {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;
  @Mock private NodeExecutionServiceImpl nodeExecutionServiceImpl;
  @InjectMocks private MarkAsFailureAdviseHandler markAsFailureAdviseHandler;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void handleAdviseWhenNextNodeIsIsEmpty() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();
    doNothing().when(engine).endNodeExecution(any());
    markAsFailureAdviseHandler.handleAdvise(
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder()
                          .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
                          .build())
            .status(Status.FAILED)
            .build(),
        adviserResponse);
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void handleAdviseWithoutFailedNode() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();
    NodeExecution updatedNodeExecution = NodeExecution.builder().status(Status.FAILED).build();

    doNothing().when(engine).endNodeExecution(any());
    doReturn(updatedNodeExecution).when(nodeExecutionServiceImpl).updateStatusWithOps(any(), any(), any(), any());

    markAsFailureAdviseHandler.handleAdvise(
        NodeExecution.builder()
            .status(Status.EXPIRED)
            .ambiance(Ambiance.newBuilder()
                          .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
                          .build())
            .build(),
        adviserResponse);
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void handleAdviseWithNextNodeId() {
    String nodeExecutionId = generateUuid();
    String nextNodeId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setMarkAsFailureAdvise(MarkAsFailureAdvise.newBuilder().setNextNodeId(nextNodeId).build())
            .build();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nextNodeId)
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setNodeType("PLAN_NODE").build())
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .parentId(generateUuid())
                                      .notifyId(generateUuid())
                                      .planNode(PlanNode.builder().build())
                                      .build();

    when(planService.fetchNode(planId, nextNodeId)).thenReturn(planNode);
    doNothing().when(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    markAsFailureAdviseHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).runNextNode(ambianceArgumentCaptor.capture(), eq(planNode), eq(nodeExecution), eq(null));

    assertThat(ambianceArgumentCaptor.getValue().getLevelsCount()).isEqualTo(1);
    assertThat(ambianceArgumentCaptor.getValue().getLevels(0).getSetupId()).isEqualTo(nextNodeId);
  }
}
