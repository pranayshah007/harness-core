/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plan.NodeEntity;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.NodeEntityRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanServiceImplTest extends OrchestrationTestBase {
  @Inject PlanService planService;
  @Inject NodeEntityRepository nodeEntityRepository;

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
    Plan plan = buildAndSavePlan();
    assertThat(plan.getUuid()).isNotNull();
    assertThat(plan.getVersion()).isZero();
    // As PlanNodes are stored as empty
    assertThat(plan.getPlanNodes()).isEmpty();
    assertThat(plan.getValidUntil()).isAfterOrEqualTo(Date.from(now.plusMonths(6).toInstant()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetch() {
    OffsetDateTime now = OffsetDateTime.now();
    Plan plan = buildAndSavePlan();
    Plan fetchedPlan = planService.fetchPlan(plan.getUuid());
    assertThat(fetchedPlan.getUuid()).isNotNull();
    assertThat(fetchedPlan.getVersion()).isZero();
    // As PlanNodes are stored as empty
    assertThat(fetchedPlan.getPlanNodes()).isEmpty();
    assertThat(fetchedPlan.getValidUntil()).isAfterOrEqualTo(Date.from(now.plusMonths(6).toInstant()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFetchNode() {
    Plan plan = buildAndSavePlan();
    PlanNode fetchNode = planService.fetchNode(plan.getUuid(), DUMMY_NODE_1_ID);
    assertThat(fetchNode.getUuid()).isNotNull();
    assertThat(fetchNode.getUuid()).isEqualTo(DUMMY_NODE_1_ID);
    assertThat(fetchNode.getName()).isEqualTo("Dummy Node 1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteNodesForGivenIds() {
    Plan plan = buildAndSavePlan();
    PlanNode fetchNode = planService.fetchNode(plan.getUuid(), DUMMY_NODE_1_ID);
    assertThat(fetchNode.getUuid()).isNotNull();
    assertThat(fetchNode.getUuid()).isEqualTo(DUMMY_NODE_1_ID);
    assertThat(fetchNode.getName()).isEqualTo("Dummy Node 1");

    planService.deleteNodesForGivenIds(Set.of(DUMMY_NODE_1_ID, DUMMY_NODE_2_ID, DUMMY_NODE_3_ID));
    assertThatThrownBy(() -> planService.fetchNode(plan.getUuid(), DUMMY_NODE_1_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLForNodesForGivenPlanId() {
    Plan plan = buildAndSavePlan();
    PlanNode fetchNode = planService.fetchNode(plan.getUuid(), DUMMY_NODE_1_ID);
    assertThat(fetchNode.getUuid()).isNotNull();
    assertThat(fetchNode.getUuid()).isEqualTo(DUMMY_NODE_1_ID);
    assertThat(fetchNode.getName()).isEqualTo("Dummy Node 1");

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    planService.updateTTLForNodesForGivenPlanId(plan.getUuid(), ttlExpiry);
    Optional<NodeEntity> optionalNodeEntity = nodeEntityRepository.findById(DUMMY_NODE_1_ID);
    assertThat(optionalNodeEntity).isPresent();
    assertThat(optionalNodeEntity.get().getValidUntil()).isEqualTo(ttlExpiry);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeletePlansForGivenIds() {
    Plan plan = buildAndSavePlan();
    Plan fetchPlan = planService.fetchPlan(plan.getUuid());
    assertThat(fetchPlan).isNotNull();
    assertThat(fetchPlan.getUuid()).isEqualTo(plan.getUuid());

    planService.deletePlansForGivenIds(Set.of(plan.getUuid()));
    assertThatThrownBy(() -> planService.fetchPlan(plan.getUuid())).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLPlansForGivenIds() {
    Plan plan = buildAndSavePlan();
    Plan fetchPlan = planService.fetchPlan(plan.getUuid());
    assertThat(fetchPlan).isNotNull();
    assertThat(fetchPlan.getUuid()).isEqualTo(plan.getUuid());

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    planService.updateTTLForPlans(Set.of(plan.getUuid()), ttlExpiry);
    Plan newPlanResult = planService.fetchPlan(plan.getUuid());
    assertThat(newPlanResult.getValidUntil()).isEqualTo(ttlExpiry);
  }

  private Plan buildAndSavePlan() {
    Plan plan = Plan.builder()
                    .planNode(PlanNode.fromPlanNodeProto(PlanNodeProto.newBuilder()
                                                             .setUuid(DUMMY_NODE_1_ID)
                                                             .setName("Dummy Node 1")
                                                             .setStepType(DUMMY_STEP_TYPE)
                                                             .setIdentifier("dummy1")
                                                             .build()))
                    .planNode(PlanNode.fromPlanNodeProto(PlanNodeProto.newBuilder()
                                                             .setUuid(DUMMY_NODE_2_ID)
                                                             .setName("Dummy Node 2")
                                                             .setStepType(DUMMY_STEP_TYPE)
                                                             .setIdentifier("dummy2")
                                                             .build()))
                    .planNode(PlanNode.fromPlanNodeProto(PlanNodeProto.newBuilder()
                                                             .setUuid(DUMMY_NODE_3_ID)
                                                             .setName("Dummy Node 3")
                                                             .setStepType(DUMMY_STEP_TYPE)
                                                             .setIdentifier("dummy3")
                                                             .build()))
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .build();
    return planService.save(plan);
  }
}
