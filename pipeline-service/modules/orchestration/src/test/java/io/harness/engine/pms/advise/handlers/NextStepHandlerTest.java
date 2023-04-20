/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.plan.ExecutionMode.NORMAL;
import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NextStepHandlerTest extends CategoryTest {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;

  @Inject @InjectMocks private NextStepHandler nextStepHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void handleAdviseWhenNextNodeIsIsEmpty() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();
    doNothing().when(engine).endNodeExecution(any());
    nextStepHandler.handleAdvise(
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder()
                          .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
                          .build())
            .build(),
        adviserResponse);
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void handleAdviseWithNextNodeId() {
    String nodeExecutionId = generateUuid();
    String nextNodeId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(nextNodeId).build())
            .build();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nextNodeId)
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).setPlanId(planId).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .planNode(PlanNode.builder().build())
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .parentId(generateUuid())
                                      .notifyId(generateUuid())
                                      .build();

    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(any(), eq(NodeProjectionUtils.withAmbiance));
    when(planService.fetchNode(planId, nextNodeId)).thenReturn(planNode);
    doNothing().when(nodeExecutionService).updateV2(eq(nodeExecutionId), any());

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).runNextNode(ambianceArgumentCaptor.capture(), eq(planNode), eq(nodeExecution), eq(null));

    assertThat(ambianceArgumentCaptor.getValue().getLevelsCount()).isEqualTo(1);
    assertThat(ambianceArgumentCaptor.getValue().getLevels(0).getSetupId()).isEqualTo(nextNodeId);
    verify(planService, times(0)).saveIdentityNodesForMatrix(any(), any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateIdentityNodeIfRequired() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();
    PlanNode planNode = PlanNode.builder()
                            .uuid("uuid")
                            .identifier("nodeId")
                            .name("nodeName")
                            .stepType(StepType.newBuilder().build())
                            .build();
    // node already of identityType. Same will be returned.
    assertThat(nextStepHandler.createIdentityNodeIfRequired(identityPlanNode, NodeExecution.builder().build(), NORMAL))
        .isEqualTo(identityPlanNode);
    // NodeExecution.parentId is empty. Same node will be returned.
    assertThat(nextStepHandler.createIdentityNodeIfRequired(planNode,
                   NodeExecution.builder()
                       .ambiance(Ambiance.newBuilder().setPlanExecutionId("planExecutinoId").build())
                       .build(),
                   NORMAL))
        .isEqualTo(planNode);

    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq("parentId"), any());
    assertThat(nextStepHandler.createIdentityNodeIfRequired(
                   planNode, NodeExecution.builder().parentId("parentId").build(), NORMAL))
        .isEqualTo(planNode);

    // Till now, same node has been returned all time. So, no interaction with planService.
    verify(planService, times(0)).saveIdentityNodesForMatrix(any(), any());

    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.IDENTITY_PLAN_NODE.name()).build())
                               .build())
                 .uuid("parentId")
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq("parentId"), any());
    doReturn(NodeExecution.builder()
                 .planNode(IdentityPlanNode.builder().build())
                 .uuid("originalNodeExecutionId")
                 .nextId("nextId")
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq("originalNodeExecutionId"), any());
    // Since currentNode is of type planNode and parentNodeExecution.nodeType is identityPlanNode. So identityNode will
    // be created for current node.
    Node savedIdentityNode = nextStepHandler.createIdentityNodeIfRequired(planNode,
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().setPlanId("planId").build())
            .originalNodeExecutionId("originalNodeExecutionId")
            .parentId("parentId")
            .build(),
        NORMAL);
    assertThat(savedIdentityNode.getName()).isEqualTo(planNode.getName());
    assertThat(savedIdentityNode.getIdentifier()).isEqualTo(planNode.getIdentifier());
    assertThat(savedIdentityNode.getStepType()).isEqualTo(planNode.getStepType());
    assertThat(((IdentityPlanNode) savedIdentityNode).getOriginalNodeExecutionId()).isEqualTo("nextId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateIdentityNodeInRBMode() {
    PlanNode planNode = PlanNode.builder()
                            .uuid("uuid")
                            .identifier("nodeId")
                            .name("nodeName")
                            .stepType(StepType.newBuilder().build())
                            .preserveInRollbackMode(true)
                            .build();
    assertThat(nextStepHandler.createIdentityNodeIfRequired(planNode, null, PIPELINE_ROLLBACK)).isEqualTo(planNode);
  }
}
