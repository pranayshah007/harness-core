/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import static io.harness.plan.NodeType.PLAN_NODE;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsLevelUtilsTest extends OrchestrationTestBase {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testBuildLevelFromPlanNode() {
    Level level = PmsLevelUtils.buildLevelFromNode("rid1",
        PlanNode.builder()
            .uuid("uuid")
            .identifier("i1")
            .skipExpressionChain(false)
            .group("g")
            .stepType(StepType.newBuilder().setType("st").setStepCategory(StepCategory.STEP).build())
            .build());
    assertThat(level.getSetupId()).isEqualTo("uuid");
    assertThat(level.getRuntimeId()).isEqualTo("rid1");
    assertThat(level.getIdentifier()).isEqualTo("i1");
    assertThat(level.getRetryIndex()).isEqualTo(0);
    assertThat(level.getSkipExpressionChain()).isEqualTo(false);
    assertThat(level.getGroup()).isEqualTo("g");
    assertThat(level.getStepType().getType()).isEqualTo("st");
    assertThat(level.getStepType().getStepCategory()).isEqualTo(StepCategory.STEP);
    assertThat(level.getStartTs()).isGreaterThan(0);
    assertThat(level.getNodeType()).isEqualTo(PLAN_NODE.toString());

    level = PmsLevelUtils.buildLevelFromNode("rid2", 1,
        PlanNode.builder()
            .uuid("uuid")
            .identifier("i2")
            .skipExpressionChain(true)
            .stepType(StepType.newBuilder().setType("st").setStepCategory(StepCategory.STEP).build())
            .build());
    assertThat(level.getSetupId()).isEqualTo("uuid");
    assertThat(level.getRuntimeId()).isEqualTo("rid2");
    assertThat(level.getIdentifier()).isEqualTo("i2");
    assertThat(level.getRetryIndex()).isEqualTo(1);
    assertThat(level.getSkipExpressionChain()).isEqualTo(true);
    assertThat(level.getGroup()).isBlank();
    assertThat(level.getStepType().getType()).isEqualTo("st");
    assertThat(level.getStepType().getStepCategory()).isEqualTo(StepCategory.STEP);
    assertThat(level.getStartTs()).isGreaterThan(0);
    assertThat(level.getNodeType()).isEqualTo(PLAN_NODE.toString());
  }
}
