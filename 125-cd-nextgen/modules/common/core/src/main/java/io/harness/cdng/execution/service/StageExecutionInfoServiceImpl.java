/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.service;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.ExecutionInfoUtility;
import io.harness.cdng.execution.ExecutionSummaryDetails;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails.ServiceExecutionSummaryDetailsBuilder;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoBuilder;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.executions.StageExecutionInfoRepository;
import io.harness.utils.StageStatus;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StageExecutionInfoServiceImpl implements StageExecutionInfoService {
  private static final int STAGE_STATUS_KEY_LOCKS_EXPIRE_TIME_HOURS = 1;
  private static final int STAGE_STATUS_KEY_LOCKS_MAXIMUM_SIZE = 5000;
  private static final int LATEST_ITEM_LIMIT = 1;

  // LoadingCache map implementations are thread safe
  private static final LoadingCache<String, Boolean> stageStatusKeyLocks =
      CacheBuilder.newBuilder()
          .expireAfterAccess(STAGE_STATUS_KEY_LOCKS_EXPIRE_TIME_HOURS, TimeUnit.HOURS)
          .maximumSize(STAGE_STATUS_KEY_LOCKS_MAXIMUM_SIZE)
          .build(CacheLoader.from(Boolean::new));

  private StageExecutionInfoRepository stageExecutionInfoRepository;

  @Override
  public StageExecutionInfo save(@Valid @NotNull StageExecutionInfo stageExecutionInfo) {
    return stageExecutionInfoRepository.save(stageExecutionInfo);
  }

  @Override
  public void updateStatus(@NotNull Scope scope, @NotNull final String stageExecutionId, StageStatus stageStatus) {
    if (isEmpty(stageExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    UpdateResult updateResult = stageExecutionInfoRepository.updateStatus(scope, stageExecutionId, stageStatus);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update stage execution status, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s, stageStatus: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stageExecutionId,
          stageStatus));
    }
  }

  @Override
  public void update(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    UpdateResult updateResult = stageExecutionInfoRepository.update(scope, stageExecutionId, updates);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException("Unable to update StageExecutionInfo");
    }
  }

  public void updateOnce(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    // if some other thread already updated DB collection based on stage status key, skip update collection again
    String stageExecutionKey = ExecutionInfoUtility.buildStageStatusKey(scope, stageExecutionId);
    Boolean previousValue = stageStatusKeyLocks.asMap().put(stageExecutionKey, true);
    if (previousValue != null) {
      return;
    }

    UpdateResult updateResult = stageExecutionInfoRepository.update(scope, stageExecutionId, updates);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException("Unable to update StageExecutionInfo");
    }
  }

  public void deleteStageStatusKeyLock(Scope scope, String stageExecutionId) {
    String stageExecutionKey = ExecutionInfoUtility.buildStageStatusKey(scope, stageExecutionId);
    log.info("Deleting stage execution key, stageExecutionKey: {}, stageStatusKeyLocksSize:{}", stageExecutionKey,
        stageStatusKeyLocks.size());

    stageStatusKeyLocks.asMap().remove(stageExecutionKey);
  }

  public Optional<StageExecutionInfo> getLatestSuccessfulStageExecutionInfo(
      @NotNull @Valid ExecutionInfoKey executionInfoKey, @NotNull final String executionId) {
    if (isEmpty(executionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    List<StageExecutionInfo> latestSucceededStageExecutionInfo =
        stageExecutionInfoRepository.listSucceededStageExecutionNotIncludeCurrent(
            executionInfoKey, executionId, LATEST_ITEM_LIMIT);
    return isEmpty(latestSucceededStageExecutionInfo) ? Optional.empty()
                                                      : Optional.ofNullable(latestSucceededStageExecutionInfo.get(0));
  }

  public List<StageExecutionInfo> listLatestSuccessfulStageExecutionInfo(
      @NotNull @Valid ExecutionInfoKey executionInfoKey, @NotNull final String stageExecutionId, int limit) {
    if (isEmpty(stageExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }
    return stageExecutionInfoRepository.listSucceededStageExecutionNotIncludeCurrent(
        executionInfoKey, stageExecutionId, limit);
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(StageExecutionInfoKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(StageExecutionInfoKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(StageExecutionInfoKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }

  @Override
  public void deleteAtAllScopes(Scope scope) {
    Criteria criteria =
        createScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    stageExecutionInfoRepository.deleteAll(criteria);
  }

  @Override
  public StageExecutionInfo findStageExecutionInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String stageExecutionId) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    return stageExecutionInfoRepository.findByStageExecutionId(stageExecutionId, scope);
  }

  @Override
  public StageExecutionInfo createStageExecutionInfo(
      Ambiance ambiance, StageElementParameters stageElementParameters, Level stageLevel) {
    StageExecutionInfoBuilder stageExecutionInfoBuilder =
        StageExecutionInfo.builder()
            .stageExecutionId(ambiance.getStageExecutionId())
            .executionSummaryDetails(ExecutionSummaryDetails.builder().build())
            .planExecutionId(ambiance.getPlanExecutionId())
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .stageIdentifier(stageElementParameters.getIdentifier())
            .stageName(stageElementParameters.getName())
            .status(Status.RUNNING)
            .stageStatus(StageStatus.IN_PROGRESS);
    final List<String> tags = new LinkedList<>();
    stageElementParameters.getTags().forEach((key, value) -> tags.add(key + ":" + value));
    if (stageLevel != null) {
      stageExecutionInfoBuilder.startts(stageLevel.getStartTs());
    }
    if (EmptyPredicate.isNotEmpty(tags)) {
      stageExecutionInfoBuilder.tags(tags.toArray(new String[0]));
    }
    return save(stageExecutionInfoBuilder.build());
  }

  @Override
  public StageExecutionInfo updateStageExecutionInfo(
      Ambiance ambiance, StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    StageExecutionInfo stageExecutionInfo =
        findStageExecutionInfo(accountIdentifier, orgIdentifier, projectIdentifier, stageExecutionId);
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    if (stageExecutionInfo == null) {
      log.error(String.format(
          "StageExecutionInfo should not be null for accountIdentifier %s, orgIdentifier %s, projectIdentifier %s and stageExecutionId %s",
          accountIdentifier, orgIdentifier, projectIdentifier, stageExecutionId));
      save(createStageExecutionInfoFromStageExecutionInfoUpdateDTO(ambiance, stageExecutionInfoUpdateDTO));
    } else {
      Map<String, Object> updates = new HashMap<>();
      updateStageExecutionInfoFromStageExecutionInfoUpdateDTO(stageExecutionInfoUpdateDTO, stageExecutionInfo, updates);
      update(scope, stageExecutionId, updates);
    }
    return stageExecutionInfoRepository.findByStageExecutionId(stageExecutionId, scope);
  }

  @Override
  public Optional<StageExecutionInfo> findById(String id) {
    return stageExecutionInfoRepository.findById(id);
  }

  @Override
  public void delete(String id) {
    stageExecutionInfoRepository.deleteById(id);
  }

  private void updateStageExecutionInfoFromStageExecutionInfoUpdateDTO(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, StageExecutionInfo stageExecutionInfo,
      Map<String, Object> updates) {
    ExecutionSummaryDetails executionSummaryDetails = stageExecutionInfo.getExecutionSummaryDetails() == null
        ? ExecutionSummaryDetails.builder().build()
        : stageExecutionInfo.getExecutionSummaryDetails();
    boolean executionSummaryDetailsUpdated = updateServiceInfo(stageExecutionInfoUpdateDTO, executionSummaryDetails);
    executionSummaryDetailsUpdated =
        executionSummaryDetailsUpdated || updateArtifactSummary(stageExecutionInfoUpdateDTO, executionSummaryDetails);
    executionSummaryDetailsUpdated =
        executionSummaryDetailsUpdated || updateManifestSummary(stageExecutionInfoUpdateDTO, executionSummaryDetails);
    executionSummaryDetailsUpdated = executionSummaryDetailsUpdated
        || updateInfraExecutionSummary(stageExecutionInfoUpdateDTO, executionSummaryDetails);
    executionSummaryDetailsUpdated = executionSummaryDetailsUpdated
        || updateGitOpsExecutionSummary(stageExecutionInfoUpdateDTO, executionSummaryDetails);
    executionSummaryDetailsUpdated =
        executionSummaryDetailsUpdated || updateGitOpsAppSummary(stageExecutionInfoUpdateDTO, executionSummaryDetails);
    executionSummaryDetailsUpdated =
        executionSummaryDetailsUpdated || updateFailureInfo(stageExecutionInfoUpdateDTO, executionSummaryDetails);

    if (executionSummaryDetailsUpdated) {
      updates.put(StageExecutionInfoKeys.executionSummaryDetails, executionSummaryDetails);
    }
    if (stageExecutionInfoUpdateDTO.getStageStatus() != null) {
      updates.put(StageExecutionInfoKeys.stageStatus, stageExecutionInfoUpdateDTO.getStageStatus());
    }
    if (stageExecutionInfoUpdateDTO.getStatus() != null) {
      updates.put(StageExecutionInfoKeys.status, stageExecutionInfoUpdateDTO.getStatus());
    }
    if (stageExecutionInfoUpdateDTO.getEndTs() != null) {
      updates.put(StageExecutionInfoKeys.endts, stageExecutionInfoUpdateDTO.getEndTs());
    }
  }

  private boolean updateInfraExecutionSummary(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getInfraExecutionSummary() != null) {
      executionSummaryDetails.setInfraExecutionSummary(stageExecutionInfoUpdateDTO.getInfraExecutionSummary());
      return true;
    }
    return false;
  }

  private boolean updateGitOpsExecutionSummary(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getGitOpsExecutionSummary() != null) {
      executionSummaryDetails.setGitOpsExecutionSummary(stageExecutionInfoUpdateDTO.getGitOpsExecutionSummary());
      return true;
    }
    return false;
  }

  private boolean updateGitOpsAppSummary(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getGitOpsAppSummary() != null) {
      executionSummaryDetails.setGitOpsAppSummary(stageExecutionInfoUpdateDTO.getGitOpsAppSummary());
      return true;
    }
    return false;
  }

  private boolean updateFailureInfo(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getFailureInfo() != null) {
      executionSummaryDetails.setFailureInfo(stageExecutionInfoUpdateDTO.getFailureInfo());
      return true;
    }
    return false;
  }

  private boolean updateServiceInfo(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getServiceInfo() != null) {
      ServiceExecutionSummaryDetails serviceExecutionSummaryDetails = stageExecutionInfoUpdateDTO.getServiceInfo();
      if (executionSummaryDetails.getServiceInfo() != null
          && executionSummaryDetails.getServiceInfo().getArtifacts() != null) {
        serviceExecutionSummaryDetails.setArtifacts(executionSummaryDetails.getServiceInfo().getArtifacts());
      }
      if (executionSummaryDetails.getServiceInfo() != null
          && executionSummaryDetails.getServiceInfo().getManifests() != null) {
        serviceExecutionSummaryDetails.setManifests(executionSummaryDetails.getServiceInfo().getManifests());
      }
      executionSummaryDetails.setServiceInfo(serviceExecutionSummaryDetails);
      return true;
    }
    return false;
  }

  private boolean updateArtifactSummary(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getArtifactsSummary() != null) {
      ServiceExecutionSummaryDetails.ArtifactsSummary artifactsSummary =
          stageExecutionInfoUpdateDTO.getArtifactsSummary();
      if (executionSummaryDetails.getServiceInfo() != null) {
        executionSummaryDetails.getServiceInfo().setArtifacts(artifactsSummary);
      } else {
        executionSummaryDetails.setServiceInfo(
            ServiceExecutionSummaryDetails.builder().artifacts(artifactsSummary).build());
      }
      return true;
    }
    return false;
  }

  private boolean updateManifestSummary(
      StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO, ExecutionSummaryDetails executionSummaryDetails) {
    if (stageExecutionInfoUpdateDTO.getManifestsSummary() != null) {
      ServiceExecutionSummaryDetails.ManifestsSummary manifestsSummary =
          stageExecutionInfoUpdateDTO.getManifestsSummary();
      if (executionSummaryDetails.getServiceInfo() != null) {
        executionSummaryDetails.getServiceInfo().setManifests(manifestsSummary);
      } else {
        executionSummaryDetails.setServiceInfo(
            ServiceExecutionSummaryDetails.builder().manifests(manifestsSummary).build());
      }
      return true;
    }
    return false;
  }

  private StageExecutionInfo createStageExecutionInfoFromStageExecutionInfoUpdateDTO(
      Ambiance ambiance, StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO) {
    ServiceExecutionSummaryDetails serviceExecutionSummaryDetails = stageExecutionInfoUpdateDTO.getServiceInfo();
    if (serviceExecutionSummaryDetails == null) {
      ServiceExecutionSummaryDetailsBuilder serviceExecutionSummaryDetailsBuilder =
          ServiceExecutionSummaryDetails.builder();
      if (stageExecutionInfoUpdateDTO.getArtifactsSummary() != null) {
        serviceExecutionSummaryDetailsBuilder.artifacts(stageExecutionInfoUpdateDTO.getArtifactsSummary());
      }
      if (stageExecutionInfoUpdateDTO.getManifestsSummary() != null) {
        serviceExecutionSummaryDetailsBuilder.manifests(stageExecutionInfoUpdateDTO.getManifestsSummary());
      }
      serviceExecutionSummaryDetails = serviceExecutionSummaryDetailsBuilder.build();
    } else {
      if (stageExecutionInfoUpdateDTO.getArtifactsSummary() != null) {
        serviceExecutionSummaryDetails.setArtifacts(stageExecutionInfoUpdateDTO.getArtifactsSummary());
      }
      if (stageExecutionInfoUpdateDTO.getManifestsSummary() != null) {
        serviceExecutionSummaryDetails.setManifests(stageExecutionInfoUpdateDTO.getManifestsSummary());
      }
    }
    ExecutionSummaryDetails executionSummaryDetails =
        ExecutionSummaryDetails.builder()
            .serviceInfo(serviceExecutionSummaryDetails)
            .freezeExecutionSummary(stageExecutionInfoUpdateDTO.getFreezeExecutionSummary())
            .failureInfo(stageExecutionInfoUpdateDTO.getFailureInfo())
            .infraExecutionSummary(stageExecutionInfoUpdateDTO.getInfraExecutionSummary())
            .gitOpsExecutionSummary(stageExecutionInfoUpdateDTO.getGitOpsExecutionSummary())
            .gitOpsAppSummary(stageExecutionInfoUpdateDTO.getGitOpsAppSummary())
            .build();

    return StageExecutionInfo.builder()
        .stageExecutionId(ambiance.getStageExecutionId())
        .executionSummaryDetails(executionSummaryDetails)
        .planExecutionId(ambiance.getPlanExecutionId())
        .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
        .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
        .status(Status.RUNNING)
        .stageStatus(StageStatus.IN_PROGRESS)
        .build();
  }
}
