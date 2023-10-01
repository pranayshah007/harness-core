/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class OrchestrationUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNodeTypeForPlan() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    NodeType nodeType = OrchestrationUtils.currentNodeType(ambiance);
    assertThat(nodeType).isEqualTo(NodeType.PLAN);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNodeTypeForPlanNode() {
    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.toString()).build()).build();
    NodeType nodeType = OrchestrationUtils.currentNodeType(ambiance);
    assertThat(nodeType).isEqualTo(NodeType.PLAN_NODE);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNodeTypeForPlanNodeEmpty() {
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().build()).build();
    NodeType nodeType = OrchestrationUtils.currentNodeType(ambiance);
    assertThat(nodeType).isEqualTo(NodeType.PLAN_NODE);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testIsStageNode() {
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.STAGE).build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode)).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    assertThat(OrchestrationUtils.isStageNode(nodeExecution)).isTrue();
    assertThat(OrchestrationUtils.isPipelineNode(nodeExecution)).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testIsStageOrParallelNode() {
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.STAGE).build())
            .build();

    PlanNode stagesPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.STAGES).build())
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    assertThat(OrchestrationUtils.isStageOrParallelStageNode(nodeExecution)).isTrue();
    assertThat(OrchestrationUtils.isPipelineNode(nodeExecution)).isFalse();

    planNode = PlanNode.builder()
                   .uuid(generateUuid())
                   .identifier("PIPELINE")
                   .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.FORK).build())
                   .build();

    ambiance = Ambiance.newBuilder()
                   .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                   .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode))
                   .build();
    nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    assertThat(OrchestrationUtils.isStageOrParallelStageNode(nodeExecution)).isTrue();
    assertThat(OrchestrationUtils.isStageNode(nodeExecution)).isFalse();
    assertThat(OrchestrationUtils.isPipelineNode(nodeExecution)).isFalse();

    PlanNode stepGroupPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    ambiance = Ambiance.newBuilder()
                   .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepGroupPlanNode))
                   .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode))
                   .build();
    nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    assertThat(OrchestrationUtils.isStageOrParallelStageNode(nodeExecution)).isFalse();
    assertThat(OrchestrationUtils.isStageNode(nodeExecution)).isFalse();
    assertThat(OrchestrationUtils.isPipelineNode(nodeExecution)).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testIsPipelineNode() {
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.PIPELINE).build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode)).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    assertThat(OrchestrationUtils.isPipelineNode(nodeExecution)).isTrue();
    assertThat(OrchestrationUtils.isStageNode(nodeExecution)).isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCalculateStatusRunningForQueuedPlanExecution() {
    String planExecutionId = generateUuid();
    try (MockedStatic<StatusUtils> mockStatic = Mockito.mockStatic(StatusUtils.class)) {
      mockStatic.when(() -> StatusUtils.calculateStatus(Collections.EMPTY_LIST, planExecutionId))
          .thenReturn(Status.QUEUED);
      assertThat(OrchestrationUtils.calculateStatusForPlanExecution(Collections.EMPTY_LIST, planExecutionId))
          .isEqualTo(Status.RUNNING);
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetOriginalNodeExecutionIdReturnNullWhenNodeTypeIsPlan() {
    Node node = Mockito.mock(Node.class);
    Mockito.when(node.getNodeType()).thenReturn(NodeType.PLAN);
    assertThat(OrchestrationUtils.getOriginalNodeExecutionId(node)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetOriginalNodeExecutionIdReturnNullWhenNodeTypeIsPlanNode() {
    Node node = Mockito.mock(Node.class);
    Mockito.when(node.getNodeType()).thenReturn(NodeType.PLAN_NODE);
    assertThat(OrchestrationUtils.getOriginalNodeExecutionId(node)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetOriginalNodeExecutionIdWhenNodeTypeIsIdentityPlanNode() {
    IdentityPlanNode node = IdentityPlanNode.builder().originalNodeExecutionId("executionId").build();
    assertThat(OrchestrationUtils.getOriginalNodeExecutionId(node)).isEqualTo("executionId");
  }
}