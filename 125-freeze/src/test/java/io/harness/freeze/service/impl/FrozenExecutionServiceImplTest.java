/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.FrozenExecutionRepository;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

public class FrozenExecutionServiceImplTest {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "oId";
  private final String PROJ_IDENTIFIER = "pId";
  private final String FREEZE_IDENTIFIER = "freezeId";
  private final String STAGE_RUNTIME_ID = "stageRuntimeId";
  private final String STAGE_SETUP_ID = "stageSetupId";
  private final String STEP_RUNTIME_ID = "stepRuntimeId";
  private final String STEP_SETUP_ID = "stepSetupId";
  private final String PLAN_EXECUTION_ID = "planExecutionId";
  private final String PLAN_ID = "planId";
  private final String PIPELINE = "pipeline";

  @Mock FrozenExecutionRepository frozenExecutionRepository;
  @Mock TransactionTemplate transactionTemplate;
  @InjectMocks FrozenExecutionServiceImpl frozenExecutionService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateFrozenExecution() {
    FreezeSummaryResponseDTO globalFreezeSummaryResponseDTO = FreezeSummaryResponseDTO.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .freezeScope(Scope.PROJECT)
                                                                  .identifier(FREEZE_IDENTIFIER)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .type(FreezeType.GLOBAL)
                                                                  .build();

    doReturn(null).when(transactionTemplate).execute(any());
    frozenExecutionService.createFrozenExecution(
        buildAmbiance(), null, Collections.singletonList(globalFreezeSummaryResponseDTO));
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateFrozenExecutionFailure() {
    FreezeSummaryResponseDTO globalFreezeSummaryResponseDTO = FreezeSummaryResponseDTO.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .freezeScope(Scope.PROJECT)
                                                                  .identifier(FREEZE_IDENTIFIER)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .type(FreezeType.GLOBAL)
                                                                  .build();

    frozenExecutionService.createFrozenExecution(null, null, Collections.singletonList(globalFreezeSummaryResponseDTO));
    verify(transactionTemplate, times(0)).execute(any());
  }

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
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "orgIdentifier", ORG_IDENTIFIER, "projectIdentifier", PROJ_IDENTIFIER))
        .addAllLevels(levels)
        .build();
  }
}
