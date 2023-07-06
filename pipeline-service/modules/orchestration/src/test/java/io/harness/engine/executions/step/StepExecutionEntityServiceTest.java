/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.step;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.step.StepExecutionEntity;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.execution.step.StepExecutionEntityUpdateDTO;
import io.harness.execution.step.approval.harness.HarnessApprovalStepExecutionDetails;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.StepExecutionEntityRepository;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.ImmutableMap;
import com.mongodb.client.result.UpdateResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class StepExecutionEntityServiceTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";
  private static final String RUNTIME_ID = "runtimeId";
  private static final String SETUP_ID = "setupId";
  private static final String STEP_ID = "stepId";
  private static final String STEP_NAME = "stepName";
  private static final Scope scope = Scope.builder()
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .build();
  private final Ambiance ambiance =
      Ambiance.newBuilder()
          .setPlanExecutionId(PLAN_EXECUTION_ID)
          .putAllSetupAbstractions(ImmutableMap.of("accountId", ACCOUNT_IDENTIFIER, "orgIdentifier", ORG_IDENTIFIER,
              "projectIdentifier", PROJECT_IDENTIFIER))
          .setStageExecutionId(STAGE_EXECUTION_ID)
          .setMetadata(ExecutionMetadata.newBuilder()
                           .setExecutionUuid(generateUuid())
                           .setPipelineIdentifier(PIPELINE_ID)
                           .build())
          .addLevels(Level.newBuilder()
                         .setIdentifier(STEP_ID)
                         .setStepType(StepType.newBuilder()
                                          .setType(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType())
                                          .setStepCategory(StepCategory.STEP)
                                          .build())
                         .setRuntimeId(RUNTIME_ID)
                         .setStartTs(2)
                         .setSetupId(SETUP_ID)
                         .build())
          .build();

  StepExecutionEntity stepExecutionEntity = StepExecutionEntity.builder()
                                                .stageExecutionId(STAGE_EXECUTION_ID)
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .projectIdentifier(PROJECT_IDENTIFIER)
                                                .pipelineIdentifier(PIPELINE_ID)
                                                .stepIdentifier(STEP_ID)
                                                .stepName(STEP_NAME)
                                                .stepExecutionId(RUNTIME_ID)
                                                .stepType(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType())
                                                .startts(2L)
                                                .status(Status.RUNNING)
                                                .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().name(STEP_NAME).identifier(STEP_ID).build();
  @Mock private StepExecutionEntityRepository stepExecutionEntityRepository;
  @InjectMocks private StepExecutionEntityServiceImpl stepExecutionEntityService;

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFindStepExecutionEntity() {
    stepExecutionEntityService.findStepExecutionEntity(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, RUNTIME_ID);

    verify(stepExecutionEntityRepository).findByStepExecutionId(eq(RUNTIME_ID), eq(scope));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCreateStepExecutionEntity() {
    when(stepExecutionEntityRepository.save(any())).thenReturn(null);
    stepExecutionEntityService.createStepExecutionEntity(ambiance, Status.RUNNING);
    ArgumentCaptor<StepExecutionEntity> argumentCaptor = ArgumentCaptor.forClass(StepExecutionEntity.class);
    verify(stepExecutionEntityRepository).save(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
        .isEqualToComparingFieldByField(StepExecutionEntity.builder()
                                            .stageExecutionId(STAGE_EXECUTION_ID)
                                            .planExecutionId(PLAN_EXECUTION_ID)
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                            .pipelineIdentifier(PIPELINE_ID)
                                            .stepIdentifier(STEP_ID)
                                            .stepExecutionId(RUNTIME_ID)
                                            .stepType(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType())
                                            .startts(2L)
                                            .status(Status.RUNNING)
                                            .build());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateStepExecutionEntity() {
    when(stepExecutionEntityRepository.findByStepExecutionId(eq(RUNTIME_ID), eq(scope))).thenReturn(null);
    StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO = StepExecutionEntityUpdateDTO.builder().build();
    stepExecutionEntityService.updateStepExecutionEntity(
        ambiance, stepExecutionEntityUpdateDTO, Status.APPROVAL_WAITING);
    verify(stepExecutionEntityRepository, times(1))
        .save(StepExecutionEntity.builder()
                  .stageExecutionId(STAGE_EXECUTION_ID)
                  .planExecutionId(PLAN_EXECUTION_ID)
                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                  .orgIdentifier(ORG_IDENTIFIER)
                  .projectIdentifier(PROJECT_IDENTIFIER)
                  .pipelineIdentifier(PIPELINE_ID)
                  .stepIdentifier(STEP_ID)
                  .stepExecutionId(RUNTIME_ID)
                  .stepType(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType())
                  .startts(2L)
                  .status(Status.APPROVAL_WAITING)
                  .build());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateStatusWithUnacknowledged() {
    when(stepExecutionEntityRepository.findByStepExecutionId(eq(RUNTIME_ID), eq(scope)))
        .thenReturn(stepExecutionEntity);
    when(stepExecutionEntityRepository.update(scope, RUNTIME_ID, new HashMap<>()))
        .thenReturn(UpdateResult.unacknowledged());
    StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO = StepExecutionEntityUpdateDTO.builder().build();
    assertThatThrownBy(()
                           -> stepExecutionEntityService.updateStepExecutionEntity(
                               ambiance, stepExecutionEntityUpdateDTO, Status.APPROVAL_WAITING))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Unable to update StepExecutionEntity, accountIdentifier: accountIdentifier, orgIdentifier: orgIdentifier, projectIdentifier: projectIdentifier, executionId: runtimeId");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateStepExecutionEntityFoundRecord() {
    when(stepExecutionEntityRepository.findByStepExecutionId(eq(RUNTIME_ID), eq(scope)))
        .thenReturn(stepExecutionEntity);
    StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO =
        StepExecutionEntityUpdateDTO.builder()
            .status(Status.FAILED)
            .stepExecutionDetails(HarnessApprovalStepExecutionDetails.builder().build())
            .endTs(3L)
            .failureInfo(FailureInfo.newBuilder().build())
            .build();
    Map<String, Object> updates = new HashMap<>();
    updates.put(StepExecutionEntityKeys.executionDetails, stepExecutionEntityUpdateDTO.getStepExecutionDetails());
    updates.put(StepExecutionEntityKeys.status, stepExecutionEntityUpdateDTO.getStatus());
    updates.put(StepExecutionEntityKeys.endts, stepExecutionEntityUpdateDTO.getEndTs());
    updates.put(StepExecutionEntityKeys.failureInfo, stepExecutionEntityUpdateDTO.getFailureInfo());
    when(stepExecutionEntityRepository.update(scope, RUNTIME_ID, updates))
        .thenReturn(UpdateResult.acknowledged(1, null, null));

    stepExecutionEntityService.updateStepExecutionEntity(
        ambiance, stepExecutionEntityUpdateDTO, Status.APPROVAL_WAITING);
    verify(stepExecutionEntityRepository, times(1)).update(scope, RUNTIME_ID, updates);
  }
}
