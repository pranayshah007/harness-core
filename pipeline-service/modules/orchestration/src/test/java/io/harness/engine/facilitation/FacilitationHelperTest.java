/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.facilitation.facilitator.sync.SyncFacilitator;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.PIPELINE)
public class FacilitationHelperTest extends OrchestrationTestBase {
  @Inject @InjectMocks private FacilitationHelper facilitationHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testFacilitateExecutionCore() {
    String nodeExecutionId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .facilitatorObtainment(FacilitatorObtainment.newBuilder().setType(SyncFacilitator.FACILITATOR_TYPE).build())
            .serviceName("CD")
            .identifier("DUMMY")
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    FacilitatorResponseProto facilitatorResponse = facilitationHelper.calculateFacilitatorResponse(ambiance, planNode);
    assertThat(facilitatorResponse.getExecutionMode()).isEqualTo(ExecutionMode.SYNC);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testCustomFacilitatorPresent() {
    boolean customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                       .setType(FacilitatorType.newBuilder().setType("CUSTOM").build())
                                       .build())
            .build());

    assertThat(customPresent).isTrue();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.TASK).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.TASK_CHAIN).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();

    customPresent = facilitationHelper.customFacilitatorPresent(
        PlanNode.builder()
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                    .build())
            .build());

    assertThat(customPresent).isFalse();
  }
}
