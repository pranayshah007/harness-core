/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.pms.pipeline.service.PipelineGetResult;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.PipelinesApi;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineImportRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineSaveResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelinesApiImpl implements PipelinesApi {
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineMetadataService pipelineMetadataService;
  private final PipelineAsyncValidationService pipelineAsyncValidationService;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public Response createPipeline(PipelineCreateRequestBody requestBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline Create request body must not be null.");
    }
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitCreateDetails(requestBody.getGitDetails()));
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(
        PipelinesApiUtils.mapCreateToRequestInfoDTO(requestBody), account, org, project, null);
    log.info(String.format("Creating a Pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), project, org, account));
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.validateAndCreatePipeline(pipelineEntity, false);
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      throw new PolicyEvaluationFailureException(
          "Policy Evaluation Failure", governanceMetadata, pipelineEntity.getYaml());
    }
    PipelineCreateResponseBody responseBody = new PipelineCreateResponseBody();
    responseBody.setIdentifier(createdEntity.getIdentifier());
    return Response.status(201).entity(responseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  public Response deletePipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account) {
    log.info(String.format(
        "Deleting Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    pmsPipelineService.delete(account, org, project, pipeline, null);
    return Response.status(204).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getPipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, String branch, Boolean templatesApplied,
      String connectorRef, String repoName, String loadFromCache, Boolean loadFromFallbackBranch,
      Boolean validateAsync) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branch).connectorRef(connectorRef).repoName(repoName).build());
    log.info(String.format(
        "Retrieving Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    Optional<PipelineEntity> pipelineEntity;
    PipelineGetResponseBody pipelineGetResponseBody = new PipelineGetResponseBody();
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID;
    try {
      PipelineGetResult pipelineGetResult = pmsPipelineService.getAndValidatePipeline(account, org, project, pipeline,
          false, false, Boolean.TRUE.equals(loadFromFallbackBranch),
          GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), validateAsync);
      pipelineEntity = pipelineGetResult.getPipelineEntity();
      validationUUID = pipelineGetResult.getAsyncValidationUUID();
    } catch (PolicyEvaluationFailureException pe) {
      pipelineGetResponseBody.setPipelineYaml(pe.getYaml());
      pipelineGetResponseBody.setGitDetails(
          PipelinesApiUtils.getGitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()));
      pipelineGetResponseBody.setValid(false);
      // GovMetaData needed here after redoing structure
      return Response.status(200).entity(pipelineGetResponseBody).build();
    } catch (InvalidYamlException e) {
      pipelineGetResponseBody.setPipelineYaml(e.getYaml());
      pipelineGetResponseBody.setGitDetails(
          PipelinesApiUtils.getGitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()));
      pipelineGetResponseBody.setYamlErrorWrapper(
          PipelinesApiUtils.getListYAMLErrorWrapper((YamlSchemaErrorWrapperDTO) e.getMetadata()));
      pipelineGetResponseBody.setValid(false);
      return Response.status(200).entity(pipelineGetResponseBody).build();
    }

    pipelineGetResponseBody = PipelinesApiUtils.getGetResponseBody(pipelineEntity.orElseThrow(
        ()
            -> new EntityNotFoundException(
                String.format("Pipeline with the given ID: %s does not exist or has been deleted.", pipeline))));
    if (Boolean.TRUE.equals(templatesApplied)) {
      try {
        String templateResolvedPipelineYaml = "";
        TemplateMergeResponseDTO templateMergeResponseDTO =
            pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.get(), BOOLEAN_FALSE_VALUE);
        templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipelineGetResponseBody.setTemplateAppliedPipelineYaml(templateResolvedPipelineYaml);
      } catch (Exception e) {
        log.info("Cannot get resolved templates pipeline YAML");
      }
    }
    if (validateAsync) {
      pipelineGetResponseBody.setValidationUuid(validationUUID);
    }
    return Response.ok().entity(pipelineGetResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response startPipelineValidationEvent(@OrgIdentifier String org, @ProjectIdentifier String project,
      String pipeline, @AccountIdentifier String account, String branch, String connectorRef, String repoName,
      Boolean loadFromCache, Boolean loadFromFallbackBranch) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branch).connectorRef(connectorRef).repoName(repoName).build());
    Optional<PipelineEntity> pipelineEntity =
        pmsPipelineService.getPipeline(account, org, project, pipeline, false, false);
    if (pipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted.", pipeline));
    }
    PipelineValidationEvent pipelineValidationEvent =
        pipelineAsyncValidationService.startEvent(pipelineEntity.get(), branch, Action.CRUD, loadFromCache);
    PipelineValidationUUIDResponseBody pipelineValidationUUIDResponseBody =
        PipelinesApiUtils.buildPipelineValidationUUIDResponseBody(pipelineValidationEvent);
    return Response.ok().entity(pipelineValidationUUIDResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public Response updatePipelineGitMetadata(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @Valid GitMetadataUpdateRequestBody body,
      @AccountIdentifier String harnessAccount) {
    String pipelineAfterUpdate = pmsPipelineService.updateGitMetadata(harnessAccount, org, project, pipeline,
        PMSUpdateGitDetailsParams.builder()
            .connectorRef(body.getConnectorRef())
            .filePath(body.getFilePath())
            .repoName(body.getRepoName())
            .build());

    GitMetadataUpdateResponseBody gitMetadataUpdateResponseBody = new GitMetadataUpdateResponseBody();
    gitMetadataUpdateResponseBody.setEntityIdentifier(pipelineAfterUpdate);
    return Response.ok().entity(gitMetadataUpdateResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getPipelineValidateResult(
      @OrgIdentifier String org, @ProjectIdentifier String project, String uuid, @AccountIdentifier String account) {
    Optional<PipelineValidationEvent> eventByUuid = pipelineAsyncValidationService.getEventByUuid(uuid);
    PipelineValidationEvent pipelineValidationEvent = eventByUuid.get();
    PipelineValidationResponseBody pipelineValidationResponseBody =
        PipelinesApiUtils.buildPipelineValidationResponseBody(pipelineValidationEvent);
    return Response.ok().entity(pipelineValidationResponseBody).build();
  }

  @Override
  public Response importPipelineFromGit(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @Valid PipelineImportRequestBody body,
      @AccountIdentifier String harnessAccount) {
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitImportDetails(body.getGitImportInfo()));
    PipelineImportRequestDTO pipelineImportRequestDTO =
        PipelineImportRequestDTO.builder()
            .pipelineName(body.getPipelineImportRequest().getPipelineName())
            .pipelineDescription(body.getPipelineImportRequest().getPipelineDescription())
            .build();
    log.info(String.format("Importing Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project,
        org, harnessAccount));
    PipelineEntity savedPipelineEntity = pmsPipelineService.importPipelineFromRemote(harnessAccount, org, project,
        pipeline, pipelineImportRequestDTO, Boolean.TRUE.equals(body.getGitImportInfo().isIsForceImport()));
    PipelineSaveResponseBody responseBody = new PipelineSaveResponseBody();
    responseBody.setIdentifier(savedPipelineEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response listPipelines(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String account, Integer page, Integer limit, String searchTerm, String sort, String order,
      String module, String filterId, List<String> pipelineIds, String name, String description, List<String> tags,
      List<String> services, List<String> envs, String deploymentType, String repoName) {
    log.info(String.format("Get List of Pipelines in project %s, org %s, account %s", project, org, account));
    Criteria criteria = pipelineServiceHelper.formCriteria(account, org, project, filterId,
        PipelinesApiUtils.getFilterProperties(
            pipelineIds, name, description, tags, services, envs, deploymentType, repoName),
        false, module, searchTerm);
    List<String> sortingList = PipelinesApiUtils.getSorting(sort, order);
    Pageable pageRequest = PageUtils.getPageRequest(
        page, limit, sortingList, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelineEntities =
        pmsPipelineService.list(criteria, pageRequest, account, org, project, false);

    List<String> pipelineIdentifiers =
        pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());
    Map<String, PipelineMetadataV2> pipelineMetadataMap =
        pipelineMetadataService.getMetadataForGivenPipelineIds(account, org, project, pipelineIdentifiers);

    Page<PMSPipelineSummaryResponseDTO> pipelines =
        pipelineEntities.map(e -> PMSPipelineDtoMapper.preparePipelineSummaryForListView(e, pipelineMetadataMap));

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pipelines.getTotalElements(), page, limit);
    return responseBuilderWithLinks
        .entity(pipelines.getContent()
                    .stream()
                    .map(pipeline -> PipelinesApiUtils.getPipelines(pipeline))
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public Response updatePipeline(PipelineUpdateRequestBody requestBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String pipeline, @AccountIdentifier String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline Update request body must not be null.");
    }
    if (!Objects.equals(pipeline, requestBody.getIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Pipeline identifier in Request Body to be [%s], but was [%s]", pipeline,
              requestBody.getIdentifier()));
    }
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitUpdateDetails(requestBody.getGitDetails()));
    log.info(String.format(
        "Updating Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(
        PipelinesApiUtils.mapUpdateToRequestInfoDTO(requestBody), account, org, project, null);
    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.MODIFY, false);
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      throw new PolicyEvaluationFailureException(
          "Policy Evaluation Failure", governanceMetadata, updatedEntity.getYaml());
    }
    PipelineCreateResponseBody responseBody = new PipelineCreateResponseBody();
    responseBody.setIdentifier(updatedEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public Response moveConfig(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, PipelineMoveConfigRequestBody requestBody,
      @AccountIdentifier String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline move config request body must not be null.");
    }
    if (!Objects.equals(pipeline, requestBody.getPipelineIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Pipeline identifier in Request Body to be [%s], but was [%s]", pipeline,
              requestBody.getPipelineIdentifier()));
    }
    log.info(
        String.format("Move Config for Pipeline of move type %s with identifier %s in project %s, org %s, account %s",
            requestBody.getMoveConfigOperationType().toString(), pipeline, project, org, account));
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitMoveDetails(requestBody.getGitDetails()));
    MoveConfigOperationDTO moveConfigOperation = PipelinesApiUtils.buildMoveConfigOperationDTO(
        requestBody.getGitDetails(), requestBody.getMoveConfigOperationType());
    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.moveConfig(account, org, project, pipeline, moveConfigOperation);
    PipelineEntity movedPipelineEntity = pipelineCRUDResult.getPipelineEntity();
    PipelineMoveConfigResponseBody responseBody = new PipelineMoveConfigResponseBody();
    responseBody.setPipelineIdentifier(movedPipelineEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }
}
