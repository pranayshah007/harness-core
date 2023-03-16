/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityPlanNodeTest {
  StepType TEST_STEP_TYPE = StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();
  StepType PMS_IDENTITY = StepType.newBuilder().setType("PMS_IDENTITY").build();

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMapPlanNodeToIdentityNode() {
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid("uuid")
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .skipGraphType(SkipType.NOOP)
            .build();
    IdentityPlanNode identityPlanNodeExpected = IdentityPlanNode.builder()
                                                    .uuid("uuid")
                                                    .originalNodeExecutionId("originalNodeExecutionId")
                                                    .identifier("test")
                                                    .name("Test Node")
                                                    .stepType(PMS_IDENTITY)
                                                    .skipGraphType(SkipType.NOOP)
                                                    .build();
    IdentityPlanNode identityPlanNodeActual =
        IdentityPlanNode.mapPlanNodeToIdentityNode(planNode, PMS_IDENTITY, "originalNodeExecutionId");
    assertThat(identityPlanNodeExpected).isEqualTo(identityPlanNodeActual);

    IdentityPlanNode identityPlanNodeWithAlwaysSkipGraph =
        IdentityPlanNode.mapPlanNodeToIdentityNode(planNode, PMS_IDENTITY, "originalNodeExecutionId", true);
    identityPlanNodeExpected = IdentityPlanNode.builder()
                                   .uuid("uuid")
                                   .originalNodeExecutionId("originalNodeExecutionId")
                                   .identifier("test")
                                   .name("Test Node")
                                   .stepType(PMS_IDENTITY)
                                   .skipGraphType(SkipType.SKIP_NODE)
                                   .build();
    assertThat(identityPlanNodeExpected).isEqualTo(identityPlanNodeWithAlwaysSkipGraph);
  }
}
