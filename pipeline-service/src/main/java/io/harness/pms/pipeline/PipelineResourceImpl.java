/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionNode;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.interceptor.GitImportInfoDTO;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.NodeExecutionToExecutioNodeMapper;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.utils.PageUtils;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.YamlSchemaResource;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.inject.Inject;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineResourceImpl implements YamlSchemaResource, PipelineResource {
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final NodeExecutionService nodeExecutionService;
  private final NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final VariableCreatorMergeService variableCreatorMergeService;
  private final PipelineCloneHelper pipelineCloneHelper;
  private final PipelineMetadataService pipelineMetadataService;

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Deprecated
  public ResponseDTO<String> createPipeline(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId, String pipelineIdentifier,
      String pipelineName, String pipelineDescription, Boolean isDraft, GitEntityCreateInfoDTO gitEntityCreateInfo,
      @NotNull String yaml) {
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml, isDraft);
    log.info(String.format("Creating pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));

    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.create(pipelineEntity);

    PipelineCRUDErrorResponse.checkForGovernanceErrorAndThrow(pipelineCRUDResult.getGovernanceMetadata());
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(createdEntity.getVersion().toString(), createdEntity.getIdentifier());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<PipelineSaveResponse> createPipelineV2(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId, String pipelineIdentifier,
      String pipelineName, String pipelineDescription, Boolean isDraft, GitEntityCreateInfoDTO gitEntityCreateInfo,
      @NotNull String yaml) {
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml, isDraft);
    log.info(String.format("Creating pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));

    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.create(pipelineEntity);

    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      return ResponseDTO.newResponse(PipelineSaveResponse.builder().governanceMetadata(governanceMetadata).build());
    }
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(createdEntity.getVersion().toString(),
        PipelineSaveResponse.builder()
            .governanceMetadata(governanceMetadata)
            .identifier(createdEntity.getIdentifier())
            .build());
  }

  @Hidden
  public ResponseDTO<PipelineSaveResponse> clonePipeline(@NotNull @AccountIdentifier String accountId,
      GitEntityCreateInfoDTO gitEntityCreateInfo, @NotNull ClonePipelineDTO clonePipelineDTO) {
    pipelineCloneHelper.checkAccess(clonePipelineDTO, accountId);

    PipelineSaveResponse pipelineSaveResponse = pmsPipelineService.clone(clonePipelineDTO, accountId);

    return ResponseDTO.newResponse(pipelineSaveResponse);
  }

  @Hidden
  public ResponseDTO<VariableMergeServiceResponse> createVariables(@NotNull String accountId, @NotNull String orgId,
      @NotNull String projectId, GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @ApiParam(hidden = true) String yaml) {
    log.info("Creating variables for pipeline.");

    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    // Apply all the templateRefs(if any) then check for variables.
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity).getMergedPipelineYaml();
    VariableMergeServiceResponse variablesResponse =
        variableCreatorMergeService.createVariablesResponses(resolveTemplateRefsInPipeline, false);

    return ResponseDTO.newResponse(variablesResponse);
  }

  @Hidden
  public ResponseDTO<VariableMergeServiceResponse> createVariablesV2(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
      @NotNull String accountId, @NotNull String orgId, @NotNull String projectId,
      GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull @ApiParam(hidden = true) String yaml) {
    log.info("Creating variables for pipeline v2 version.");

    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    // Apply all the templateRefs(if any) then check for variables.
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity).getMergedPipelineYaml();
    VariableMergeServiceResponse variablesResponse = variableCreatorMergeService.createVariablesResponsesV2(
        accountId, orgId, projectId, resolveTemplateRefsInPipeline);
    return ResponseDTO.newResponse(variablesResponse);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineResponseDTO> getPipelineByIdentifier(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean getTemplatesResolvedPipeline) {
    log.info(String.format("Retrieving pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));

    Optional<PipelineEntity> pipelineEntity;
    try {
      pipelineEntity = pmsPipelineService.get(accountId, orgId, projectId, pipelineId, false);
    } catch (PolicyEvaluationFailureException pe) {
      return ResponseDTO.newResponse(
          PMSPipelineResponseDTO.builder()
              .yamlPipeline(pe.getYaml())
              .governanceMetadata(pe.getGovernanceMetadata())
              .entityValidityDetails(EntityValidityDetails.builder().valid(false).invalidYaml(pe.getYaml()).build())
              .gitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata())
              .build());
    } catch (InvalidYamlException e) {
      return ResponseDTO.newResponse(
          PMSPipelineResponseDTO.builder()
              .yamlPipeline(e.getYaml())
              .entityValidityDetails(EntityValidityDetails.builder().valid(false).invalidYaml(e.getYaml()).build())
              .gitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata())
              .yamlSchemaErrorWrapper((YamlSchemaErrorWrapperDTO) e.getMetadata())
              .build());
    }
    String version = "0";
    if (pipelineEntity.isPresent()) {
      version = pipelineEntity.get().getVersion().toString();
    }

    PMSPipelineResponseDTO pipeline = PMSPipelineDtoMapper.writePipelineDto(pipelineEntity.orElseThrow(
        ()
            -> new EntityNotFoundException(
                String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId))));

    if (getTemplatesResolvedPipeline) {
      try {
        String templateResolvedPipelineYaml = "";
        TemplateMergeResponseDTO templateMergeResponseDTO =
            pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.get());
        templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipeline.setResolvedTemplatesPipelineYaml(templateResolvedPipelineYaml);
      } catch (Exception e) {
        log.info("Cannot get resolved templates pipeline YAML");
      }
    }
    return ResponseDTO.newResponse(version, pipeline);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Deprecated
  public ResponseDTO<String> updatePipeline(String ifMatch, @NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, String pipelineName, String pipelineDescription, Boolean isDraft,
      GitEntityUpdateInfoDTO gitEntityInfo, @NotNull String yaml) {
    log.info(String.format("Updating pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    PipelineEntity withVersion = PMSPipelineDtoMapper.toPipelineEntityWithVersion(
        accountId, orgId, projectId, pipelineId, yaml, ifMatch, isDraft);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.updatePipelineYaml(withVersion, ChangeType.MODIFY);
    PipelineCRUDErrorResponse.checkForGovernanceErrorAndThrow(pipelineCRUDResult.getGovernanceMetadata());
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(), updatedEntity.getIdentifier());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<PipelineSaveResponse> updatePipelineV2(String ifMatch,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, @ResourceIdentifier String pipelineId, String pipelineName,
      String pipelineDescription, Boolean isDraft, GitEntityUpdateInfoDTO gitEntityInfo, @NotNull String yaml) {
    log.info(String.format("Updating pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    PipelineEntity withVersion = PMSPipelineDtoMapper.toPipelineEntityWithVersion(
        accountId, orgId, projectId, pipelineId, yaml, ifMatch, isDraft);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.updatePipelineYaml(withVersion, ChangeType.MODIFY);
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      return ResponseDTO.newResponse(PipelineSaveResponse.builder().governanceMetadata(governanceMetadata).build());
    }
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(),
        PipelineSaveResponse.builder()
            .identifier(updatedEntity.getIdentifier())
            .governanceMetadata(governanceMetadata)
            .build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  public ResponseDTO<Boolean> deletePipeline(String ifMatch, @NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityDeleteInfoDTO entityDeleteInfo) {
    log.info(String.format("Deleting pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));

    return ResponseDTO.newResponse(pmsPipelineService.delete(
        accountId, orgId, projectId, pipelineId, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PMSPipelineSummaryResponseDTO>> getListOfPipelines(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, int page, int size, List<String> sort,

      String searchTerm, String module, String filterIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      PipelineFilterPropertiesDto filterProperties, Boolean getDistinctFromBranches) {
    log.info(String.format("Get List of pipelines in project %s, org %s, account %s", projectId, orgId, accountId));

    // todo: Add handling for sort by lastExecutedAt
    Criteria criteria = pipelineServiceHelper.formCriteria(
        accountId, orgId, projectId, filterIdentifier, filterProperties, false, module, searchTerm);

    Pageable pageRequest =
        PageUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));

    Page<PipelineEntity> pipelineEntities =
        pmsPipelineService.list(criteria, pageRequest, accountId, orgId, projectId, getDistinctFromBranches);

    List<String> pipelineIdentifiers =
        pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());
    Map<String, PipelineMetadataV2> pipelineMetadataMap =
        pipelineMetadataService.getMetadataForGivenPipelineIds(accountId, orgId, projectId, pipelineIdentifiers);

    Page<PMSPipelineSummaryResponseDTO> pipelines =
        pipelineEntities.map(e -> PMSPipelineDtoMapper.preparePipelineSummaryForListView(e, pipelineMetadataMap));

    return ResponseDTO.newResponse(pipelines);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineSummaryResponseDTO> getPipelineSummary(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info(
        String.format("Get pipeline summary for pipeline with with identifier %s in project %s, org %s, account %s",
            pipelineId, projectId, orgId, accountId));

    Optional<PipelineEntity> pipelineEntity;
    pipelineEntity = pmsPipelineService.getWithoutPerformingValidations(accountId, orgId, projectId, pipelineId, false);

    PMSPipelineSummaryResponseDTO pipelineSummary =
        PMSPipelineDtoMapper.preparePipelineSummary(pipelineEntity.orElseThrow(
            ()
                -> new EntityNotFoundException(
                    String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId))));

    return ResponseDTO.newResponse(pipelineSummary);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<PipelineSaveResponse> importPipelineFromGit(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitImportInfoDTO gitImportInfoDTO,
      PipelineImportRequestDTO pipelineImportRequestDTO) {
    PipelineEntity savedPipelineEntity = pmsPipelineService.importPipelineFromRemote(
        accountId, orgId, projectId, pipelineId, pipelineImportRequestDTO, gitImportInfoDTO.isForceImport());
    return ResponseDTO.newResponse(
        PipelineSaveResponse.builder().identifier(savedPipelineEntity.getIdentifier()).build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  public ResponseDTO<ExpandedPipelineJsonDTO> getExpandedPipelineJson(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityFindInfoDTO gitEntityBasicInfo) {
    String expandedPipelineJSON = pmsPipelineService.fetchExpandedPipelineJSON(accountId, orgId, projectId, pipelineId);
    return ResponseDTO.newResponse(ExpandedPipelineJsonDTO.builder().expandedJson(expandedPipelineJSON).build());
  }

  public ResponseDTO<StepCategory> getStepsV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                                                  required = true) @NotNull String accountId,
      @NotNull StepPalleteFilterWrapper stepPalleteFilterWrapper) {
    return ResponseDTO.newResponse(pmsPipelineService.getStepsV2(accountId, stepPalleteFilterWrapper));
  }

  @Hidden
  public ResponseDTO<NotificationRules> getNotificationSchema() {
    return ResponseDTO.newResponse(NotificationRules.builder().build());
  }
  @Hidden
  public ResponseDTO<ExecutionNode> getExecutionNode(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @NotNull String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    return ResponseDTO.newResponse(
        nodeExecutionToExecutioNodeMapper.mapNodeExecutionToExecutionNode(nodeExecutionService.get(nodeExecutionId)));
  }

  @Hidden
  public ResponseDTO<PmsAbstractStepNode> getPmsStepNodes() {
    return ResponseDTO.newResponse(new PmsAbstractStepNode() {
      @Override
      public String getType() {
        return null;
      }

      @Override
      public StepSpecType getStepSpecType() {
        return null;
      }
    });
  }

  @Hidden
  // do not delete this.
  public ResponseDTO<TemplateStepNode> getTemplateStepNode() {
    return ResponseDTO.newResponse(new TemplateStepNode());
  }

  @Hidden
  // do not delete this.
  public ResponseDTO<TemplateStageNode> getTemplateStageNode() {
    return ResponseDTO.newResponse(new TemplateStageNode());
  }

  @Hidden
  public ResponseDTO<Boolean> refreshFFCache(@NotNull @AccountIdentifier String accountId) {
    try {
      return ResponseDTO.newResponse(pmsFeatureFlagHelper.refreshCacheForGivenAccountId(accountId));
    } catch (ExecutionException e) {
      log.error("Execution exception occurred while updating cache: " + e.getMessage());
    }
    return ResponseDTO.newResponse(false);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<String> validatePipelineByYAML(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId, @NotNull String yaml) {
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    log.info(String.format("Validating the pipeline YAML with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));
    pipelineServiceHelper.validatePipelineYaml(pipelineEntity, false);
    return ResponseDTO.newResponse(pipelineEntity.getIdentifier());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<String> validatePipelineByIdentifier(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId) {
    Optional<PipelineEntity> entityOptional = pmsPipelineService.get(accountId, orgId, projectId, pipelineId, false);
    if (entityOptional.isPresent()) {
      PipelineEntity pipelineEntity = entityOptional.get();
      log.info(String.format("Validating the pipeline with identifier %s in project %s, org %s, account %s",
          pipelineEntity.getIdentifier(), projectId, orgId, accountId));
      pipelineServiceHelper.validatePipelineYaml(pipelineEntity, false);
      return ResponseDTO.newResponse(pipelineEntity.getIdentifier());
    } else {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId));
    }
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<TemplatesResolvedPipelineResponseDTO> getTemplateResolvedPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, @ResourceIdentifier String pipelineId,
      GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info(
        String.format("Retrieving templates resolved pipeline with identifier %s in project %s, org %s, account %s",
            pipelineId, projectId, orgId, accountId));

    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.get(accountId, orgId, projectId, pipelineId, false);

    if (!pipelineEntity.isPresent()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId));
    }

    String pipelineYaml = pipelineEntity.get().getYaml();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.get());
    String templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
    TemplatesResolvedPipelineResponseDTO templatesResolvedPipelineResponseDTO =
        TemplatesResolvedPipelineResponseDTO.builder()
            .resolvedTemplatesPipelineYaml(templateResolvedPipelineYaml)
            .yamlPipeline(pipelineYaml)
            .build();
    return ResponseDTO.newResponse(templatesResolvedPipelineResponseDTO);
  }
}
