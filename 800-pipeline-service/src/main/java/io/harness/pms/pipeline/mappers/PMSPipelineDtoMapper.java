package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.clients.PermissionCheckDTO;
import io.harness.accesscontrol.clients.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
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
        .gitDetails(EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity))
        .entityValidityDetails(pipelineEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(pipelineEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .build();
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
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  public PMSPipelineSummaryResponseDTO preparePipelineSummary(PipelineEntity pipelineEntity) {
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
        .filters(pipelineEntity.getFilters())
        .stageNames(pipelineEntity.getStageNames())
        .gitDetails(EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity))
        .entityValidityDetails(pipelineEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(pipelineEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
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
}
