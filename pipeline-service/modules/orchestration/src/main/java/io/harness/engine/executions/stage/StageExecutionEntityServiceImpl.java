/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.stage;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.stage.StageExecutionEntity;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityBuilder;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;
import io.harness.execution.stage.StageExecutionEntityUpdateDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.StageExecutionEntityRepository;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StageExecutionEntityServiceImpl implements StageExecutionEntityService {
  private StageExecutionEntityRepository stageExecutionEntityRepository;

  @Override
  public StageExecutionEntity createStageExecutionEntity(
      Ambiance ambiance, StageElementParameters stageElementParameters) {
    StageExecutionEntityBuilder stageExecutionEntityBuilder = buildStageExecutionEntity(ambiance);
    return stageExecutionEntityRepository.save(stageExecutionEntityBuilder.build());
  }

  private StageExecutionEntityBuilder buildStageExecutionEntity(Ambiance ambiance) {
    StageExecutionEntityBuilder stageExecutionEntityBuilder =
        StageExecutionEntity.builder()
            .stageExecutionId(ambiance.getStageExecutionId())
            .planExecutionId(ambiance.getPlanExecutionId())
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .stageExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .status(Status.RUNNING)
            .stageStatus(StageStatus.IN_PROGRESS);
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (currentLevel != null) {
      stageExecutionEntityBuilder.stageType(currentLevel.getStepType().getType());
      stageExecutionEntityBuilder.startts(currentLevel.getStartTs());
    }
    return stageExecutionEntityBuilder;
  }

  @Override
  public StageExecutionEntity findStageExecutionEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String stageExecutionId) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    return stageExecutionEntityRepository.findByStageExecutionId(stageExecutionId, scope);
  }

  @Override
  public StageExecutionEntity updateStageExecutionEntity(
      Ambiance ambiance, StageExecutionEntityUpdateDTO stageExecutionEntityUpdateDTO) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String stageExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    StageExecutionEntity stageExecutionEntity =
        findStageExecutionEntity(accountIdentifier, orgIdentifier, projectIdentifier, stageExecutionId);
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    if (stageExecutionEntity == null) {
      log.error(format(
          "StageExecutionEntity should not be null for accountIdentifier %s, orgIdentifier %s, projectIdentifier %s and stageExecutionId %s",
          accountIdentifier, orgIdentifier, projectIdentifier, stageExecutionId));
      stageExecutionEntityRepository.save(
          createStageExecutionEntityFromStageExecutionEntityUpdateDTO(ambiance, stageExecutionEntityUpdateDTO));
    } else {
      Map<String, Object> updates = new HashMap<>();
      getUpdatesFromStageExecutionEntityUpdateDTO(stageExecutionEntity, stageExecutionEntityUpdateDTO, updates);
      update(scope, stageExecutionId, updates);
    }
    return stageExecutionEntityRepository.findByStageExecutionId(stageExecutionId, scope);
  }

  private StageExecutionEntity createStageExecutionEntityFromStageExecutionEntityUpdateDTO(
      Ambiance ambiance, StageExecutionEntityUpdateDTO stageExecutionEntityUpdateDTO) {
    StageExecutionEntityBuilder stageExecutionEntityBuilder = buildStageExecutionEntity(ambiance);
    if (stageExecutionEntityUpdateDTO.getTags() != null) {
      stageExecutionEntityBuilder.tags(toTagsList(stageExecutionEntityUpdateDTO.getTags()));
    }
    if (stageExecutionEntityUpdateDTO.getStageName() != null) {
      stageExecutionEntityBuilder.stageName(stageExecutionEntityUpdateDTO.getStageName());
    }
    if (stageExecutionEntityUpdateDTO.getStageIdentifier() != null) {
      stageExecutionEntityBuilder.stageIdentifier(stageExecutionEntityUpdateDTO.getStageIdentifier());
    }
    if (stageExecutionEntityUpdateDTO.getStageExecutionSummaryDetails() != null) {
      stageExecutionEntityBuilder.stageExecutionSummaryDetails(
          stageExecutionEntityUpdateDTO.getStageExecutionSummaryDetails());
    }
    if (stageExecutionEntityUpdateDTO.getEndTs() != null) {
      stageExecutionEntityBuilder.endts(stageExecutionEntityUpdateDTO.getEndTs());
    }
    if (stageExecutionEntityUpdateDTO.getFailureInfo() != null) {
      stageExecutionEntityBuilder.failureInfo(stageExecutionEntityUpdateDTO.getFailureInfo());
    }
    if (stageExecutionEntityUpdateDTO.getStatus() != null) {
      stageExecutionEntityBuilder.status(stageExecutionEntityUpdateDTO.getStatus());
    }
    if (stageExecutionEntityUpdateDTO.getStageStatus() != null) {
      stageExecutionEntityBuilder.stageStatus(stageExecutionEntityUpdateDTO.getStageStatus());
    }
    return stageExecutionEntityBuilder.build();
  }

  private void getUpdatesFromStageExecutionEntityUpdateDTO(StageExecutionEntity currentStageExecutionEntity,
      StageExecutionEntityUpdateDTO stageExecutionEntityUpdateDTO, Map<String, Object> updates) {
    if (stageExecutionEntityUpdateDTO.getStageExecutionSummaryDetails() != null
        && !stageExecutionEntityUpdateDTO.getStageExecutionSummaryDetails().equals(
            currentStageExecutionEntity.getStageExecutionSummaryDetails())) {
      updates.put(StageExecutionEntityKeys.stageExecutionSummaryDetails,
          stageExecutionEntityUpdateDTO.getStageExecutionSummaryDetails());
    }
    if (stageExecutionEntityUpdateDTO.getStatus() != null
        && !stageExecutionEntityUpdateDTO.getStatus().equals(currentStageExecutionEntity.getStatus())) {
      updates.put(StageExecutionEntityKeys.status, stageExecutionEntityUpdateDTO.getStatus());
    }
    if (stageExecutionEntityUpdateDTO.getStageStatus() != null
        && !stageExecutionEntityUpdateDTO.getStageStatus().equals(currentStageExecutionEntity.getStageStatus())) {
      updates.put(StageExecutionEntityKeys.stageStatus, stageExecutionEntityUpdateDTO.getStageStatus());
    }
    if (stageExecutionEntityUpdateDTO.getEndTs() != null
        && !stageExecutionEntityUpdateDTO.getEndTs().equals(currentStageExecutionEntity.getEndts())) {
      updates.put(StageExecutionEntityKeys.endts, stageExecutionEntityUpdateDTO.getEndTs());
    }
    if (stageExecutionEntityUpdateDTO.getFailureInfo() != null
        && !stageExecutionEntityUpdateDTO.getFailureInfo().equals(currentStageExecutionEntity.getFailureInfo())) {
      updates.put(StageExecutionEntityKeys.failureInfo, stageExecutionEntityUpdateDTO.getFailureInfo());
    }
    if (stageExecutionEntityUpdateDTO.getTags() != null) {
      updates.put(StageExecutionEntityKeys.tags, toTagsList(stageExecutionEntityUpdateDTO.getTags()));
    }
    if (stageExecutionEntityUpdateDTO.getStageName() != null) {
      updates.put(StageExecutionEntityKeys.stageName, stageExecutionEntityUpdateDTO.getStageName());
    }
    if (stageExecutionEntityUpdateDTO.getStageIdentifier() != null) {
      updates.put(StageExecutionEntityKeys.stageIdentifier, stageExecutionEntityUpdateDTO.getStageIdentifier());
    }
  }

  private String[] toTagsList(Map<String, String> tags) {
    final List<String> tagsList = new LinkedList<>();
    tags.forEach((key, value) -> tagsList.add(key + ":" + value));
    if (EmptyPredicate.isNotEmpty(tagsList)) {
      return tagsList.toArray(new String[0]);
    }
    return new String[0];
  }

  @Override
  public void update(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    UpdateResult updateResult = stageExecutionEntityRepository.update(scope, stageExecutionId, updates);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update StageExecutionEntity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stageExecutionId));
    }
  }

  @Override
  public void updateStatus(@NotNull Scope scope, @NotNull final String stageExecutionId, Status status) {
    if (isEmpty(stageExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    UpdateResult updateResult = stageExecutionEntityRepository.updateStatus(scope, stageExecutionId, status);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update stage execution status, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s, status: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stageExecutionId,
          status));
    }
  }
}
