/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.encryption.ScopeHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.pipeline.ExecutionSummaryInfoDTO;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PMSPipelineDtoMapper {
  public PMSPipelineResponseDTO writePipelineDto(PipelineEntity pipelineEntity) {
    return PMSPipelineResponseDTO.builder()
        .yamlPipeline(pipelineEntity.getYaml())
        .version(pipelineEntity.getVersion())
        .modules(pipelineEntity.getFilters().keySet())
        .gitDetails(getEntityGitDetails(pipelineEntity))
        .entityValidityDetails(getEntityValidityDetails(pipelineEntity))
        .build();
  }

  public EntityGitDetails getEntityGitDetails(PipelineEntity pipelineEntity) {
    return pipelineEntity.getStoreType() == null ? EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity)
        : pipelineEntity.getStoreType() == StoreType.REMOTE
        ? GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()
        : null;
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

  public PMSPipelineSummaryResponseDTO preparePipelineSummary(PipelineEntity pipelineEntity) {
    return preparePipelineSummary(pipelineEntity, getEntityGitDetails(pipelineEntity));
  }

  public PMSPipelineSummaryResponseDTO preparePipelineSummaryForListView(PipelineEntity pipelineEntity) {
    // For List View, getEntityGitDetails(...) method cant be used because for REMOTE pipelines. That is because
    // GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata() cannot be used, because there won't be any
    // SCM Context set in the List call.
    EntityGitDetails entityGitDetails = pipelineEntity.getStoreType() == null
        ? EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity)
        : pipelineEntity.getStoreType() == StoreType.REMOTE ? GitAwareContextHelper.getEntityGitDetails(pipelineEntity)
                                                            : null;
    return preparePipelineSummary(pipelineEntity, entityGitDetails);
  }

  private PMSPipelineSummaryResponseDTO preparePipelineSummary(
      PipelineEntity pipelineEntity, EntityGitDetails entityGitDetails) {
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
    if (pipeline.getExecutionSummaryInfo() == null) {
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
    if (pipeline.getExecutionSummaryInfo() == null) {
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

  public PermissionCheckDTO toPermissionCheckDTO(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String permission) {
    return PermissionCheckDTO.builder()
        .resourceScope(ResourceScope.builder()
                           .accountIdentifier(accountIdentifier)
                           .orgIdentifier(orgIdentifier)
                           .projectIdentifier(projectIdentifier)
                           .build())
        .resourceType("PIPELINE")
        .resourceIdentifier(pipelineIdentifier)
        .permission(permission)
        .build();
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
}
