/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationBeansTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanTest extends OrchestrationBeansTestBase {
  private static final String PLAN_ID = generateUuid();
  private static final String DUMMY_NODE_1_ID = generateUuid();
  private static final String DUMMY_NODE_2_ID = generateUuid();
  private static final String DUMMY_NODE_3_ID = generateUuid();

  private static final StepType DUMMY_STEP_TYPE = StepType.newBuilder().setType("DUMMY").build();

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetchNode() {
    Plan plan = buildDummyPlan();
    PlanNodeProto node1 = plan.fetchNode(DUMMY_NODE_1_ID);
    assertThat(node1).isNotNull();
    assertThat(node1.getName()).isEqualTo("Dummy Node 1");

    PlanNodeProto node2 = plan.fetchNode(DUMMY_NODE_2_ID);
    assertThat(node2).isNotNull();
    assertThat(node2.getName()).isEqualTo("Dummy Node 2");

    assertThatThrownBy(() -> plan.fetchNode(generateUuid())).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetchStartingNode() {
    Plan plan = buildDummyPlan();
    PlanNodeProto startingNode = plan.fetchStartingNode();
    assertThat(startingNode).isNotNull();
    assertThat(startingNode.getName()).isEqualTo("Dummy Node 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestIsEmpty() {
    Plan plan = Plan.builder().build();
    assertThat(plan.isEmpty()).isEqualTo(true);
  }

  private Plan buildDummyPlan() {
    return Plan.builder()
        .node(PlanNodeProto.newBuilder()
                  .setUuid(DUMMY_NODE_1_ID)
                  .setName("Dummy Node 1")
                  .setStepType(DUMMY_STEP_TYPE)
                  .setIdentifier("dummy1")
                  .build())
        .node(PlanNodeProto.newBuilder()
                  .setUuid(DUMMY_NODE_2_ID)
                  .setName("Dummy Node 2")
                  .setStepType(DUMMY_STEP_TYPE)
                  .setIdentifier("dummy2")
                  .build())
        .node(PlanNodeProto.newBuilder()
                  .setUuid(DUMMY_NODE_3_ID)
                  .setName("Dummy Node 3")
                  .setStepType(DUMMY_STEP_TYPE)
                  .setIdentifier("dummy3")
                  .build())
        .startingNodeId(DUMMY_NODE_1_ID)
        .build();
  }
}
