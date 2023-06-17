/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.CacheResponseMetadataDTO;
import io.harness.pms.pipeline.ExecutionSummaryInfoDTO;
import io.harness.pms.pipeline.ExecutorInfoDTO;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineValidationResponseDTO;
import io.harness.pms.pipeline.RecentExecutionInfo;
import io.harness.pms.pipeline.RecentExecutionInfoDTO;
import io.harness.pms.pipeline.api.PipelineRequestInfoDTO;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.pipeline.yaml.PipelineYaml;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PMSPipelineDtoMapper {
  public static final String BOOLEAN_TRUE_VALUE = "true";
  public PMSPipelineResponseDTO writePipelineDto(PipelineEntity pipelineEntity) {
    return PMSPipelineResponseDTO.builder()
        .yamlPipeline(pipelineEntity.getYaml())
        .version(pipelineEntity.getVersion())
        .modules(pipelineEntity.getFilters().keySet())
        .gitDetails(getEntityGitDetails(pipelineEntity))
        .entityValidityDetails(getEntityValidityDetails(pipelineEntity))
        .cacheResponse(getCacheResponse(pipelineEntity))
        .storeType(pipelineEntity.getStoreType())
        .build();
  }

  public EntityGitDetails getEntityGitDetails(PipelineEntity pipelineEntity) {
    return pipelineEntity.getStoreType() == null ? EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity)
        : pipelineEntity.getStoreType() == StoreType.REMOTE
        ? GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()
        : null;
  }

  public CacheResponseMetadataDTO getCacheResponse(PipelineEntity pipelineEntity) {
    if (pipelineEntity.getStoreType() == StoreType.REMOTE) {
      return getCacheResponseFromGitContext();
    }
    return null;
  }

  private EntityGitDetails getEntityGitDetailsForMetadataResponse(PipelineEntity pipelineEntity) {
    EntityGitDetails entityGitDetails = pipelineEntity.getStoreType() == null
        ? EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity)
        : pipelineEntity.getStoreType() == StoreType.REMOTE ? GitAwareContextHelper.getEntityGitDetails(pipelineEntity)
                                                            : null;
    if (entityGitDetails != null) {
      entityGitDetails.setRepoUrl(pipelineEntity.getRepoURL());
    }
    return entityGitDetails;
  }

  public EntityValidityDetails getEntityValidityDetails(PipelineEntity pipelineEntity) {
    return pipelineEntity.getStoreType() != null || !pipelineEntity.isEntityInvalid()
        ? EntityValidityDetails.builder().valid(true).build()
        : EntityValidityDetails.builder().valid(false).invalidYaml(pipelineEntity.getYaml()).build();
  }

  public PipelineEntity toPipelineEntity(String accountId, String orgId, String projectId, String yaml) {
    try {
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
      if (NGExpressionUtils.matchesInputSetPattern(basicPipeline.getIdentifier())) {
        throw new InvalidRequestException("Pipeline identifier cannot be runtime input");
      }
      return PipelineEntity.builder()
          .yaml(yaml)
          .accountId(accountId)
          .orgIdentifier(orgId)
          .projectIdentifier(projectId)
          .name(basicPipeline.getName())
          .identifier(basicPipeline.getIdentifier())
          .description(basicPipeline.getDescription())
          .tags(TagMapper.convertToList(basicPipeline.getTags()))
          .allowStageExecutions(basicPipeline.isAllowStageExecutions())
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  public PipelineEntity toSimplifiedPipelineEntity(
      String accountId, String orgId, String projectId, String pipelineId, String pipelineName, String yaml) {
    try {
      PipelineYaml pipelineYaml = YamlUtils.read(yaml, PipelineYaml.class);
      // give priority to yaml name
      if (EmptyPredicate.isNotEmpty(pipelineYaml.getName())) {
        pipelineName = pipelineYaml.getName();
      }
      if (isEmpty(pipelineName)) {
        throw new InvalidRequestException("Pipeline name cannot be empty");
      }

      if (isEmpty(pipelineId)) {
        pipelineId = IdentifierGeneratorUtils.getId(pipelineName);
      }
      if (NGExpressionUtils.matchesInputSetPattern(pipelineId)) {
        throw new InvalidRequestException("Pipeline identifier cannot be runtime input");
      }
      return PipelineEntity.builder()
          .yaml(yaml)
          .accountId(accountId)
          .orgIdentifier(orgId)
          .projectIdentifier(projectId)
          .name(pipelineName)
          .identifier(pipelineId)
          .tags(TagMapper.convertToList(null))
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  public PipelineEntity toPipelineEntity(String accountId, String orgId, String projectId, String pipelineName,
      String yaml, Boolean isDraft, String pipelineVersion) {
    PipelineEntity pipelineEntity;
    // Use the pipeline name from api request only for V1 yaml
    if (pipelineVersion != null && !pipelineVersion.equals(PipelineVersion.V0)) {
      // PipelineId is passed as null since it gets created using pipelineName
      pipelineEntity = toSimplifiedPipelineEntity(accountId, orgId, projectId, null, pipelineName, yaml);
    } else {
      pipelineEntity = toPipelineEntity(accountId, orgId, projectId, yaml);
    }
    if (isDraft == null) {
      isDraft = false;
    }
    pipelineEntity.setIsDraft(isDraft);
    pipelineEntity.setHarnessVersion(pipelineVersion);
    return pipelineEntity;
  }

  public PipelineEntity toPipelineEntityWithPipelineId(String accountId, String orgId, String projectId,
      String pipelineId, String pipelineName, String yaml, Boolean isDraft, String pipelineVersion) {
    PipelineEntity pipelineEntity;
    // Use pipelineId for V1 yaml only since we can't change it if name gets changed
    if (pipelineVersion != null && !pipelineVersion.equals(PipelineVersion.V0)) {
      pipelineEntity = toSimplifiedPipelineEntity(accountId, orgId, projectId, pipelineId, pipelineName, yaml);
    } else {
      pipelineEntity = toPipelineEntity(accountId, orgId, projectId, yaml);
    }
    if (isDraft == null) {
      isDraft = false;
    }
    pipelineEntity.setIsDraft(isDraft);
    pipelineEntity.setHarnessVersion(pipelineVersion);
    return pipelineEntity;
  }

  public PipelineEntity toPipelineEntity(
      PipelineRequestInfoDTO requestInfoDTO, String accountId, String orgId, String projectId, Boolean isDraft) {
    try {
      if (NGExpressionUtils.matchesInputSetPattern(requestInfoDTO.getIdentifier())) {
        throw new InvalidRequestException("Pipeline identifier cannot be runtime input");
      }
      BasicPipeline basicPipeline = YamlUtils.read(requestInfoDTO.getYaml(), BasicPipeline.class);
      if (isNotEmpty(basicPipeline.getIdentifier())
          && !basicPipeline.getIdentifier().equals(requestInfoDTO.getIdentifier())) {
        throw new InvalidRequestException(String.format("Expected Pipeline identifier in YAML to be [%s], but was [%s]",
            requestInfoDTO.getIdentifier(), basicPipeline.getIdentifier()));
      }
      if (isNotEmpty(basicPipeline.getName()) && !basicPipeline.getName().equals(requestInfoDTO.getName())) {
        throw new InvalidRequestException(String.format("Expected Pipeline name in YAML to be [%s], but was [%s]",
            requestInfoDTO.getName(), basicPipeline.getName()));
      }
      if (isNotEmpty(basicPipeline.getOrgIdentifier()) && !basicPipeline.getOrgIdentifier().equals(orgId)) {
        throw new InvalidRequestException(
            String.format("Expected Pipeline Organization identifier in YAML to be [%s], but was [%s]", orgId,
                basicPipeline.getOrgIdentifier()));
      }
      if (isNotEmpty(basicPipeline.getProjectIdentifier()) && !basicPipeline.getProjectIdentifier().equals(projectId)) {
        throw new InvalidRequestException(
            String.format("Expected Pipeline Project identifier in YAML to be [%s], but was [%s]", projectId,
                basicPipeline.getProjectIdentifier()));
      }
      if (isNotEmpty(basicPipeline.getDescription()) && isNotEmpty(requestInfoDTO.getDescription())
          && !basicPipeline.getDescription().equals(requestInfoDTO.getDescription())) {
        throw new InvalidRequestException(
            String.format("Expected Pipeline description in YAML to be [%s], but was [%s]",
                requestInfoDTO.getDescription(), basicPipeline.getDescription()));
      }
      if (isNotEmpty(basicPipeline.getTags()) && isNotEmpty(requestInfoDTO.getTags())
          && !basicPipeline.getTags().equals(requestInfoDTO.getTags())) {
        throw new InvalidRequestException(String.format("Expected Pipeline tags in YAML to be [%s], but was [%s]",
            requestInfoDTO.getTags(), basicPipeline.getTags()));
      }
      PipelineEntity pipelineEntity = PipelineEntity.builder()
                                          .yaml(requestInfoDTO.getYaml())
                                          .accountId(accountId)
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .name(requestInfoDTO.getName())
                                          .identifier(requestInfoDTO.getIdentifier())
                                          .description(requestInfoDTO.getDescription())
                                          .tags(TagMapper.convertToList(requestInfoDTO.getTags()))
                                          // allowStageExecutions will still be extracted from Yaml
                                          .allowStageExecutions(basicPipeline.isAllowStageExecutions())
                                          .build();

      if (isDraft == null) {
        isDraft = false;
      }
      pipelineEntity.setIsDraft(isDraft);
      return pipelineEntity;
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  public PipelineEntity toPipelineEntityWithVersion(
      String accountId, String orgId, String projectId, String pipelineId, String yaml, String ifMatch) {
    PipelineEntity pipelineEntity = toPipelineEntity(accountId, orgId, projectId, yaml);
    PipelineEntity withVersion = pipelineEntity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    if (!withVersion.getIdentifier().equals(pipelineId)) {
      throw new InvalidRequestException(String.format(
          "Expected Pipeline identifier in YAML to be [%s], but was [%s]", pipelineId, pipelineEntity.getIdentifier()));
    }
    return withVersion;
  }

  public PipelineEntity toPipelineEntityWithVersion(String accountId, String orgId, String projectId, String pipelineId,
      String pipelineName, String yaml, String ifMatch, Boolean isDraft, String pipelineVersion) {
    PipelineEntity pipelineEntity = toPipelineEntityWithPipelineId(
        accountId, orgId, projectId, pipelineId, pipelineName, yaml, isDraft, pipelineVersion);
    PipelineEntity withVersion = pipelineEntity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    if (!Objects.equals(pipelineId, withVersion.getIdentifier())) {
      throw new InvalidRequestException(String.format(
          "Expected Pipeline identifier in YAML to be [%s], but was [%s]", pipelineId, pipelineEntity.getIdentifier()));
    }
    return withVersion;
  }

  public PMSPipelineSummaryResponseDTO preparePipelineSummary(PipelineEntity pipelineEntity, Boolean getMetadataOnly) {
    if (Boolean.TRUE.equals(getMetadataOnly)) {
      return preparePipelineSummary(pipelineEntity, getEntityGitDetailsForMetadataResponse(pipelineEntity));
    }
    return preparePipelineSummary(pipelineEntity, getEntityGitDetails(pipelineEntity));
  }

  public PMSPipelineSummaryResponseDTO preparePipelineSummaryForListView(
      PipelineEntity pipelineEntity, Map<String, PipelineMetadataV2> pipelineMetadataMap) {
    // For List View, getEntityGitDetails(...) method cant be used because for REMOTE pipelines. That is because
    // GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata() cannot be used, because there won't be any
    // SCM Context set in the List call.
    EntityGitDetails entityGitDetails = getEntityGitDetailsForMetadataResponse(pipelineEntity);
    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO =
        preparePipelineSummary(pipelineEntity, entityGitDetails);
    List<RecentExecutionInfoDTO> recentExecutionsInfo =
        prepareRecentExecutionsInfo(pipelineMetadataMap.get(pipelineEntity.getIdentifier()));
    pmsPipelineSummaryResponseDTO.setRecentExecutionsInfo(recentExecutionsInfo);
    return pmsPipelineSummaryResponseDTO;
  }

  private PMSPipelineSummaryResponseDTO preparePipelineSummary(
      PipelineEntity pipelineEntity, EntityGitDetails entityGitDetails) {
    if (pipelineEntity.getIsDraft() == null) {
      pipelineEntity.setIsDraft(false);
    }
    if (entityGitDetails != null) {
      entityGitDetails.setRepoUrl(pipelineEntity.getRepoURL());
    }
    return PMSPipelineSummaryResponseDTO.builder()
        .identifier(pipelineEntity.getIdentifier())
        .description(pipelineEntity.getDescription())
        .name(pipelineEntity.getName())
        .tags(TagMapper.convertToMap(pipelineEntity.getTags()))
        .version(pipelineEntity.getVersion())
        .numOfStages(pipelineEntity.getStageCount())
        .executionSummaryInfo(getExecutionSummaryInfoDTO(pipelineEntity))
        .lastUpdatedAt(pipelineEntity.getLastUpdatedAt())
        .createdAt(pipelineEntity.getCreatedAt())
        .modules(pipelineEntity.getFilters().keySet())
        .filters(ModuleInfoMapper.getModuleInfo(pipelineEntity.getFilters()))
        .stageNames(pipelineEntity.getStageNames())
        .storeType(pipelineEntity.getStoreType())
        .connectorRef(pipelineEntity.getConnectorRef())
        .gitDetails(entityGitDetails)
        .entityValidityDetails(getEntityValidityDetails(pipelineEntity))
        .isDraft(pipelineEntity.getIsDraft())
        .build();
  }

  public List<RecentExecutionInfoDTO> prepareRecentExecutionsInfo(PipelineMetadataV2 pipelineMetadataV2) {
    if (pipelineMetadataV2 == null) {
      return Collections.emptyList();
    }
    List<RecentExecutionInfo> recentExecutionInfoFromMetadata = pipelineMetadataV2.getRecentExecutionInfoList();
    if (EmptyPredicate.isEmpty(recentExecutionInfoFromMetadata)) {
      return Collections.emptyList();
    }
    return recentExecutionInfoFromMetadata.stream()
        .map(PMSPipelineDtoMapper::prepareRecentExecutionInfo)
        .collect(Collectors.toList());
  }

  public RecentExecutionInfoDTO prepareRecentExecutionInfo(RecentExecutionInfo recentExecutionInfo) {
    ExecutionTriggerInfo triggerInfo = recentExecutionInfo.getExecutionTriggerInfo();
    ExecutorInfoDTO executorInfo = ExecutorInfoDTO.builder()
                                       .triggerType(triggerInfo.getTriggerType())
                                       .username(triggerInfo.getTriggeredBy().getIdentifier())
                                       .email(triggerInfo.getTriggeredBy().getExtraInfoOrDefault("email", null))
                                       .build();
    return RecentExecutionInfoDTO.builder()
        .planExecutionId(recentExecutionInfo.getPlanExecutionId())
        .status(ExecutionStatus.getExecutionStatus(recentExecutionInfo.getStatus()))
        .startTs(recentExecutionInfo.getStartTs())
        .endTs(recentExecutionInfo.getEndTs())
        .executorInfo(executorInfo)
        .parentStageInfo(recentExecutionInfo.getParentStageInfo())
        .runSequence(recentExecutionInfo.getRunSequence())
        .build();
  }

  public PipelineEntity toPipelineEntity(String accountId, String yaml) {
    try {
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
      if (NGExpressionUtils.matchesInputSetPattern(basicPipeline.getIdentifier())) {
        throw new InvalidRequestException("Pipeline identifier cannot be runtime input");
      }
      return PipelineEntity.builder()
          .yaml(yaml)
          .accountId(accountId)
          .orgIdentifier(basicPipeline.getOrgIdentifier())
          .projectIdentifier(basicPipeline.getProjectIdentifier())
          .name(basicPipeline.getName())
          .identifier(basicPipeline.getIdentifier())
          .description(basicPipeline.getDescription())
          .tags(TagMapper.convertToList(basicPipeline.getTags()))
          .allowStageExecutions(basicPipeline.isAllowStageExecutions())
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  private ExecutionSummaryInfoDTO getExecutionSummaryInfoDTO(PipelineEntity pipelineEntity) {
    return ExecutionSummaryInfoDTO.builder()
        .deployments(getNumberOfDeployments(pipelineEntity))
        .numOfErrors(getNumberOfErrorsLast7Days(pipelineEntity))
        .lastExecutionStatus(pipelineEntity.getExecutionSummaryInfo() != null
                ? pipelineEntity.getExecutionSummaryInfo().getLastExecutionStatus()
                : null)
        .lastExecutionTs(pipelineEntity.getExecutionSummaryInfo() != null
                ? pipelineEntity.getExecutionSummaryInfo().getLastExecutionTs()
                : null)
        .lastExecutionId(pipelineEntity.getExecutionSummaryInfo() != null
                ? pipelineEntity.getExecutionSummaryInfo().getLastExecutionId()
                : null)
        .build();
  }

  private List<Integer> getNumberOfErrorsLast7Days(PipelineEntity pipeline) {
    if (pipeline.getExecutionSummaryInfo() == null || pipeline.getExecutionSummaryInfo().getNumOfErrors() == null) {
      return new ArrayList<>();
    }
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, -7);
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    List<Integer> errors = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      cal.add(Calendar.DAY_OF_YEAR, 1);
      errors.add(pipeline.getExecutionSummaryInfo().getNumOfErrors().getOrDefault(sdf.format(cal.getTime()), 0));
    }
    return errors;
  }

  private List<Integer> getNumberOfDeployments(PipelineEntity pipeline) {
    if (pipeline.getExecutionSummaryInfo() == null || pipeline.getExecutionSummaryInfo().getDeployments() == null) {
      return new ArrayList<>();
    }
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, -7);
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    List<Integer> numberOfDeployments = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      cal.add(Calendar.DAY_OF_YEAR, 1);
      numberOfDeployments.add(
          pipeline.getExecutionSummaryInfo().getDeployments().getOrDefault(sdf.format(cal.getTime()), 0));
    }
    return numberOfDeployments;
  }

  public EntityDetail toEntityDetail(PipelineEntity entity) {
    return EntityDetail.builder()
        .name(entity.getName())
        .type(EntityType.PIPELINES)
        .entityRef(IdentifierRef.builder()
                       .accountIdentifier(entity.getAccountIdentifier())
                       .orgIdentifier(entity.getOrgIdentifier())
                       .projectIdentifier(entity.getProjectIdentifier())
                       .scope(ScopeHelper.getScope(
                           entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()))
                       .identifier(entity.getIdentifier())
                       .build())
        .build();
  }

  public boolean parseLoadFromCacheHeaderParam(String loadFromCache) {
    if (isEmpty(loadFromCache)) {
      return false;
    } else {
      return BOOLEAN_TRUE_VALUE.equalsIgnoreCase(loadFromCache);
    }
  }

  public PipelineValidationResponseDTO buildPipelineValidationResponseDTO(PipelineValidationEvent event) {
    return PipelineValidationResponseDTO.builder()
        .status(event.getStatus().name())
        .policyEval(event.getResult().getGovernanceMetadata())
        .startTs(event.getStartTs())
        .endTs(event.getEndTs())
        .templateValidationResponse(event.getResult().getTemplateValidationResponse())
        .validateTemplateReconcileResponseDTO(event.getResult().getValidateTemplateReconcileResponseDTO())
        .build();
  }

  public CacheResponseMetadataDTO getCacheResponseFromGitContext() {
    CacheResponse cacheResponse = GitAwareContextHelper.getCacheResponseFromScmGitMetadata();
    if (cacheResponse != null) {
      return CacheResponseMetadataDTO.builder()
          .cacheState(cacheResponse.getCacheState())
          .ttlLeft(cacheResponse.getTtlLeft())
          .lastUpdatedAt(cacheResponse.getLastUpdatedAt())
          .build();
    }
    return null;
  }
}
