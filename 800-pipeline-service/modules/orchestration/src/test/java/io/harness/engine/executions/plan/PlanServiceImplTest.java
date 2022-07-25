/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanServiceImplTest extends OrchestrationTestBase {
  @Inject PlanService planService;

  private static final String DUMMY_NODE_1_ID = generateUuid();
  private static final String DUMMY_NODE_2_ID = generateUuid();
  private static final String DUMMY_NODE_3_ID = generateUuid();

  private static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    OffsetDateTime now = OffsetDateTime.now();
    Plan plan = buildAnsSavePlan();
    assertThat(plan.getUuid()).isNotNull();
    assertThat(plan.getVersion()).isEqualTo(0);
    assertThat(plan.getNodes()).hasSize(3);
    assertThat(plan.getValidUntil()).isAfterOrEqualTo(Date.from(now.plusMonths(6).toInstant()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetch() {
    OffsetDateTime now = OffsetDateTime.now();
    Plan plan = buildAnsSavePlan();
    Plan fetchedPlan = planService.fetchPlan(plan.getUuid());
    assertThat(fetchedPlan.getUuid()).isNotNull();
    assertThat(fetchedPlan.getVersion()).isEqualTo(0);
    assertThat(fetchedPlan.getNodes()).hasSize(3);
    assertThat(fetchedPlan.getValidUntil()).isAfterOrEqualTo(Date.from(now.plusMonths(6).toInstant()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetchNode() {
    Plan plan = buildAnsSavePlan();
    PlanNode fetchNode = planService.fetchNode(plan.getUuid(), DUMMY_NODE_1_ID);
    assertThat(fetchNode.getUuid()).isNotNull();
    assertThat(fetchNode.getUuid()).isEqualTo(DUMMY_NODE_1_ID);
    assertThat(fetchNode.getName()).isEqualTo("Dummy Node 1");
  }

  private Plan buildAnsSavePlan() {
    Plan plan = Plan.builder()
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
    return planService.save(plan);
  }
}
