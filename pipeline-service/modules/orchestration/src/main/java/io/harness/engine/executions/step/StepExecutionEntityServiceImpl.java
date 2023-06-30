/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.step;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.step.StepExecutionDetails;
import io.harness.execution.step.StepExecutionEntity;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityBuilder;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.execution.step.StepExecutionEntityUpdateDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.StepExecutionEntityRepository;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StepExecutionEntityServiceImpl implements StepExecutionEntityService {
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private StepExecutionEntityRepository stepExecutionEntityRepository;

  @Override
  public StepExecutionEntity createStepExecutionEntity(Ambiance ambiance, Status status) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(ambiance);
    String stepType = isNull(currentStepType) ? null : currentStepType.getType();
    StepExecutionEntityBuilder stepExecutionEntityBuilder =
        StepExecutionEntity.builder()
            .stageExecutionId(ambiance.getStageExecutionId())
            .planExecutionId(ambiance.getPlanExecutionId())
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .stepIdentifier(AmbianceUtils.obtainStepIdentifier(ambiance))
            .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .stepType(stepType)
            .startts(AmbianceUtils.getCurrentLevelStartTs(ambiance))
            .status(status);
    return stepExecutionEntityRepository.save(stepExecutionEntityBuilder.build());
  }

  @Override
  public StepExecutionEntity findStepExecutionEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String stepExecutionId) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    return stepExecutionEntityRepository.findByStepExecutionId(stepExecutionId, scope);
  }

  @Override
  public boolean checkIfStepExecutionEntityExists(Ambiance ambiance) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    StepExecutionEntity stepExecutionEntity =
        findStepExecutionEntity(accountIdentifier, orgIdentifier, projectIdentifier, stepExecutionId);
    return !isNull(stepExecutionEntity);
  }

  @Override
  public StepExecutionEntity updateStepExecutionEntity(Ambiance ambiance, FailureInfo failureInfo,
      StepExecutionDetails stepExecutionDetails, String stepName, Status status) {
    if (pmsFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_STEP_EXECUTION_DATA_SYNC)) {
      try {
        return updateStepExecutionEntity(ambiance,
            StepExecutionEntityUpdateDTO.builder()
                .stepName(stepName)
                .failureInfo(failureInfo)
                .stepExecutionDetails(stepExecutionDetails)
                .build(),
            status);
      } catch (Exception ex) {
        log.error(
            String.format(
                "Unable to update step execution entity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, planExecutionId: %s, stageExecutionId: %s, stepExecutionId: %s",
                AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                AmbianceUtils.getProjectIdentifier(ambiance), ambiance.getPlanExecutionId(),
                ambiance.getStageExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance)),
            ex);
      }
    }
    return null;
  }

  @Override
  public StepExecutionEntity updateStepExecutionEntity(
      Ambiance ambiance, StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO, Status status) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    StepExecutionEntity stepExecutionEntity =
        findStepExecutionEntity(accountIdentifier, orgIdentifier, projectIdentifier, stepExecutionId);
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    if (stepExecutionEntity == null) {
      log.error(format(
          "StepExecutionEntity should not be null for accountIdentifier %s, orgIdentifier %s, projectIdentifier %s and stepExecutionId %s",
          accountIdentifier, orgIdentifier, projectIdentifier, stepExecutionId));
      stepExecutionEntityRepository.save(
          createStepExecutionEntityFromStepExecutionEntityUpdateDTO(ambiance, stepExecutionEntityUpdateDTO, status));
    } else {
      Map<String, Object> updates = new HashMap<>();
      getUpdatesFromStepExecutionEntityUpdateDTO(stepExecutionEntity, stepExecutionEntityUpdateDTO, updates);
      update(scope, stepExecutionId, updates);
    }
    return stepExecutionEntityRepository.findByStepExecutionId(stepExecutionId, scope);
  }

  private StepExecutionEntity createStepExecutionEntityFromStepExecutionEntityUpdateDTO(
      Ambiance ambiance, StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO, Status status) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(ambiance);
    String stepType = isNull(currentStepType) ? null : currentStepType.getType();
    StepExecutionEntityBuilder stepExecutionEntityBuilder =
        StepExecutionEntity.builder()
            .stageExecutionId(ambiance.getStageExecutionId())
            .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .planExecutionId(ambiance.getPlanExecutionId())
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .startts(AmbianceUtils.getCurrentLevelStartTs(ambiance))
            .stepType(stepType)
            .stepIdentifier(AmbianceUtils.obtainStepIdentifier(ambiance))
            .status(status);
    if (stepExecutionEntityUpdateDTO.getStepName() != null) {
      stepExecutionEntityBuilder.stepName(stepExecutionEntityUpdateDTO.getStepName());
    }
    if (stepExecutionEntityUpdateDTO.getStepExecutionDetails() != null) {
      stepExecutionEntityBuilder.executionDetails(stepExecutionEntityUpdateDTO.getStepExecutionDetails());
    }
    if (stepExecutionEntityUpdateDTO.getEndTs() != null) {
      stepExecutionEntityBuilder.endts(stepExecutionEntityUpdateDTO.getEndTs());
    }
    if (stepExecutionEntityUpdateDTO.getFailureInfo() != null) {
      stepExecutionEntityBuilder.failureInfo(stepExecutionEntityUpdateDTO.getFailureInfo());
    }
    if (stepExecutionEntityUpdateDTO.getStatus() != null) {
      stepExecutionEntityBuilder.status(stepExecutionEntityUpdateDTO.getStatus());
    }
    return stepExecutionEntityBuilder.build();
  }

  private void getUpdatesFromStepExecutionEntityUpdateDTO(StepExecutionEntity currentStepExecutionEntity,
      StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO, Map<String, Object> updates) {
    if (stepExecutionEntityUpdateDTO.getStepName() != null
        && !stepExecutionEntityUpdateDTO.getStepName().equals(currentStepExecutionEntity.getStepName())) {
      updates.put(StepExecutionEntityKeys.stepName, stepExecutionEntityUpdateDTO.getStepName());
    }
    if (stepExecutionEntityUpdateDTO.getStepExecutionDetails() != null
        && !stepExecutionEntityUpdateDTO.getStepExecutionDetails().equals(
            currentStepExecutionEntity.getExecutionDetails())) {
      updates.put(StepExecutionEntityKeys.executionDetails, stepExecutionEntityUpdateDTO.getStepExecutionDetails());
    }
    if (stepExecutionEntityUpdateDTO.getStatus() != null
        && !stepExecutionEntityUpdateDTO.getStatus().equals(currentStepExecutionEntity.getStatus())) {
      updates.put(StepExecutionEntityKeys.status, stepExecutionEntityUpdateDTO.getStatus());
    }
    if (stepExecutionEntityUpdateDTO.getEndTs() != null
        && !stepExecutionEntityUpdateDTO.getEndTs().equals(currentStepExecutionEntity.getEndts())) {
      updates.put(StepExecutionEntityKeys.endts, stepExecutionEntityUpdateDTO.getEndTs());
    }
    if (stepExecutionEntityUpdateDTO.getFailureInfo() != null
        && !stepExecutionEntityUpdateDTO.getFailureInfo().equals(currentStepExecutionEntity.getFailureInfo())) {
      updates.put(StepExecutionEntityKeys.failureInfo, stepExecutionEntityUpdateDTO.getFailureInfo());
    }
  }

  @Override
  public void update(Scope scope, String stepExecutionId, Map<String, Object> updates) {
    UpdateResult updateResult = stepExecutionEntityRepository.update(scope, stepExecutionId, updates);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update StepExecutionEntity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stepExecutionId));
    }
  }

  @Override
  public void updateStatus(@NotNull Scope scope, @NotNull final String stepExecutionId, Status status) {
    if (isEmpty(stepExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    UpdateResult updateResult = stepExecutionEntityRepository.updateStatus(scope, stepExecutionId, status);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update step execution status, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s, status: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stepExecutionId,
          status));
    }
  }
}
