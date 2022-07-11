/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

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
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).planNode(planNode).build();
    assertThat(OrchestrationUtils.isStageNode(nodeExecution)).isTrue();
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
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).planNode(planNode).build();
    assertThat(OrchestrationUtils.isPipelineNode(nodeExecution)).isTrue();
    assertThat(OrchestrationUtils.isStageNode(nodeExecution)).isFalse();
  }
}
