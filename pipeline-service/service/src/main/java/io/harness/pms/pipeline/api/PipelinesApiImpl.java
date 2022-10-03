/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.PipelinesApi;
import io.harness.spec.server.pipeline.model.PipelineCreateRequestBody;
import io.harness.spec.server.pipeline.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.model.PipelineUpdateRequestBody;
import io.harness.utils.PageUtils;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelinesApiImpl implements PipelinesApi {
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineMetadataService pipelineMetadataService;

  @Override
  public Response createPipeline(PipelineCreateRequestBody requestBody, String org, String project, String account) {
    PipelineEntity pipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(account, org, project, requestBody.getPipelineYaml(), null);
    log.info(String.format("Creating a Pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), project, org, account));
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.create(pipelineEntity);
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      throw new PolicyEvaluationFailureException(
          "Policy Evaluation Failure", governanceMetadata, createdEntity.getYaml());
    }
    return Response.status(201).entity(createdEntity.getIdentifier()).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  public Response deletePipeline(String org, String project, String pipeline, String account) {
    log.info(String.format(
        "Deleting Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    boolean deleted = pmsPipelineService.delete(account, org, project, pipeline, null);
    if (!deleted) {
      throw new InvalidRequestException(String.format("Pipeline with identifier %s cannot be deleted.", pipeline));
    }
    return Response.status(204).build();
  }

  @Override
  public Response getPipeline(
      String org, String project, String pipeline, String account, String branch, Boolean templatesApplied) {
    log.info(String.format(
        "Retrieving Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    Optional<PipelineEntity> pipelineEntity;
    PipelineGetResponseBody pipelineGetResponseBody = new PipelineGetResponseBody();
    try {
      pipelineEntity = pmsPipelineService.get(account, org, project, pipeline, false);
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
            pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.get());
        templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipelineGetResponseBody.setTemplateAppliedPipelineYaml(templateResolvedPipelineYaml);
      } catch (Exception e) {
        log.info("Cannot get resolved templates pipeline YAML");
      }
    }
    return Response.ok().entity(pipelineGetResponseBody).build();
  }

  @Override
  public Response listPipelines(String org, String project, String account, Integer page, Integer limit,
      String searchTerm, String sort, String order, String module, String filterId, List<String> pipelineIds,
      String name, String description, List<String> tags, List<String> services, List<String> envs,
      String deploymentType, String repoName) {
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
    ResponseBuilder responseBuilderWithLinks = PipelinesApiUtils.addLinksHeader(responseBuilder,
        String.format("/v1/orgs/%s/projects/%s/pipelines", org, project), pipelines.getContent().size(), page, limit);
    return responseBuilderWithLinks
        .entity(pipelines.getContent()
                    .stream()
                    .map(pipeline -> PipelinesApiUtils.getPipelines(pipeline))
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Response updatePipeline(
      PipelineUpdateRequestBody requestBody, String org, String project, String pipeline, String account) {
    log.info(String.format(
        "Updating Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    PipelineEntity pipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(account, org, project, requestBody.getPipelineYaml(), null);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.updatePipelineYaml(pipelineEntity, ChangeType.MODIFY);
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      throw new PolicyEvaluationFailureException(
          "Policy Evaluation Failure", governanceMetadata, updatedEntity.getYaml());
    }
    return Response.ok().entity(updatedEntity.getIdentifier()).build();
  }
}
