/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.mappers;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FrozenExecutionMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_ID = "orgId";
  private final String PROJECT_ID = "projectId";
  private final String STAGE_RUNTIME_ID = "stageRuntimeId";
  private final String STAGE_SETUP_ID = "stageSetupId";
  private final String STEP_RUNTIME_ID = "stepRuntimeId";
  private final String STEP_SETUP_ID = "stepSetupId";
  private final String PLAN_EXECUTION_ID = "planExecutionId";
  private final String PLAN_ID = "planId";
  private final String PIPELINE = "pipeline";
  private final List<FreezeSummaryResponseDTO> manualFreezeList =
      Arrays.asList(FreezeSummaryResponseDTO.builder().build());
  private final List<FreezeSummaryResponseDTO> globalFreezeList =
      Arrays.asList(FreezeSummaryResponseDTO.builder().build());
  private final Ambiance emptyAmbiance = Ambiance.newBuilder().build();

  private Ambiance buildAmbiance() {
    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(STAGE_RUNTIME_ID)
            .setSetupId(STAGE_SETUP_ID)
            .setGroup("STAGE")
            .setStartTs(3)
            .setIdentifier("i3")
            .setStepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setRuntimeId(STEP_RUNTIME_ID)
            .setSetupId(STEP_SETUP_ID)
            .setGroup("SECTION")
            .setStartTs(4)
            .setIdentifier("i4")
            .setStepType(StepType.newBuilder().setType("SERVICE").setStepCategory(StepCategory.STEP).build())
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(stageLevel);
    levels.add(stepLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setPlanId(PLAN_ID)
        .setStageExecutionId(STAGE_RUNTIME_ID)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE).build())
        .putAllSetupAbstractions(
            ImmutableMap.of("accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID))
        .addAllLevels(levels)
        .build();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_toFreezeWithExecution_AmbianceNULL() {
    FrozenExecution frozenExecution =
        FrozenExecutionMapper.toFreezeWithExecution(null, manualFreezeList, globalFreezeList);
    assertThat(frozenExecution).isNull();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_toFreezeWithExecution_FreezeEmpty() {
    FrozenExecution frozenExecution =
        FrozenExecutionMapper.toFreezeWithExecution(emptyAmbiance, new ArrayList<>(), new ArrayList<>());
    assertThat(frozenExecution).isNull();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_toFreezeWithExecution() {
    Ambiance ambiance = buildAmbiance();
    FrozenExecution frozenExecution =
        FrozenExecutionMapper.toFreezeWithExecution(ambiance, manualFreezeList, globalFreezeList);
    assertThat(frozenExecution.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(frozenExecution.getOrgId()).isEqualTo(ORG_ID);
    assertThat(frozenExecution.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(frozenExecution.getPipelineId()).isEqualTo(PIPELINE);
    assertThat(frozenExecution.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(frozenExecution.getStageExecutionId()).isEqualTo(STAGE_RUNTIME_ID);
    assertThat(frozenExecution.getStageNodeId()).isEqualTo(STAGE_SETUP_ID);
    assertThat(frozenExecution.getStepExecutionId()).isEqualTo(STEP_RUNTIME_ID);
    assertThat(frozenExecution.getStepNodeId()).isEqualTo(STEP_SETUP_ID);
    assertThat(frozenExecution.getStepType()).isEqualTo("SERVICE");
    assertThat(frozenExecution.getManualFreezeList()).isSameAs(manualFreezeList);
    assertThat(frozenExecution.getGlobalFreezeList()).isSameAs(globalFreezeList);
  }
}