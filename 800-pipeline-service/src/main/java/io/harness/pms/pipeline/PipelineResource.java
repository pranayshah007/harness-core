/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
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
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.contracts.governance.GovernanceMetadata;
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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
@Api("pipelines")
@Path("pipelines")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Pipeline", description = "This contains APIs related to Setup of Pipelines")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@PipelineServiceAuth
@Slf4j
public class PipelineResource implements YamlSchemaResource {
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final NodeExecutionService nodeExecutionService;
  private final NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final VariableCreatorMergeService variableCreatorMergeService;
  private final PipelineCloneHelper pipelineCloneHelper;

  @POST
  @ApiOperation(value = "Create a Pipeline", nickname = "createPipeline")
  @Operation(operationId = "postPipeline", description = "Creates a Pipeline", summary = "Create a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created pipeline")
      },
      deprecated = true)
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Deprecated
  public ResponseDTO<String>
  createPipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml) {
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    log.info(String.format("Creating pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));

    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.create(pipelineEntity);
    PipelineCRUDErrorResponse.checkForGovernanceErrorAndThrow(pipelineCRUDResult.getGovernanceMetadata());
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(createdEntity.getVersion().toString(), createdEntity.getIdentifier());
  }

  @POST
  @Path("/v2")
  @ApiOperation(value = "Create a Pipeline", nickname = "createPipelineV2")
  @Operation(operationId = "postPipelineV2", description = "Creates a Pipeline", summary = "Create a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created pipeline with metadata")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<PipelineSaveResponse>
  createPipelineV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml) {
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
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

  @POST
  @Path("/clone")
  @ApiOperation(value = "Clone a Pipeline", nickname = "clonePipeline")
  @Operation(operationId = "clonePipeline", summary = "Clone a Pipeline API",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns cloned pipeline with metadata")
      })
  @Hidden
  public ResponseDTO<PipelineSaveResponse>
  clonePipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true,
          description = "Request Body for Cloning a pipeline") @NotNull ClonePipelineDTO clonePipelineDTO) {
    pipelineCloneHelper.checkAccess(clonePipelineDTO, accountId);

    PipelineSaveResponse pipelineSaveResponse = pmsPipelineService.clone(clonePipelineDTO, accountId);

    return ResponseDTO.newResponse(pipelineSaveResponse);
  }

  @POST
  @Path("/variables")
  @Operation(operationId = "createVariables",
      summary = "Get all the Variables which can be used as expression in the Pipeline.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in pipeline.")
      })
  @ApiOperation(value = "Create variables for Pipeline", nickname = "createVariables")
  @Hidden
  public ResponseDTO<VariableMergeServiceResponse>
  createVariables(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                      required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull @ApiParam(hidden = true) String yaml) {
    log.info("Creating variables for pipeline.");

    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    // Apply all the templateRefs(if any) then check for variables.
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity).getMergedPipelineYaml();
    VariableMergeServiceResponse variablesResponse =
        variableCreatorMergeService.createVariablesResponses(resolveTemplateRefsInPipeline, false);

    return ResponseDTO.newResponse(variablesResponse);
  }

  @POST
  @Path("/v2/variables")
  @Operation(operationId = "createVariablesV2",
      summary = "Get all the Variables which can be used as expression in the Pipeline.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in pipeline.")
      })
  @ApiOperation(value = "Create variables for Pipeline", nickname = "createVariablesV2")
  @Hidden
  public ResponseDTO<VariableMergeServiceResponse>
  createVariablesV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                        required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull @ApiParam(hidden = true) String yaml) {
    log.info("Creating variables for pipeline v2 version.");

    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    // Apply all the templateRefs(if any) then check for variables.
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity).getMergedPipelineYaml();
    VariableMergeServiceResponse variablesResponse = variableCreatorMergeService.createVariablesResponsesV2(
        accountId, orgId, projectId, resolveTemplateRefsInPipeline);
    return ResponseDTO.newResponse(variablesResponse);
  }

  @GET
  @Path("/{pipelineIdentifier}")
  @ApiOperation(value = "Gets a pipeline by identifier", nickname = "getPipeline")
  @Operation(operationId = "getPipeline", description = "Returns a Pipeline by Identifier",
      summary = "Fetch a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline YAML")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineResponseDTO>
  getPipelineByIdentifier(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                          @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(
          description =
              "This is a boolean value. If true, returns Templates resolved Pipeline YAML in the response else returns null.")
      @QueryParam("getTemplatesResolvedPipeline") @DefaultValue("false") boolean getTemplatesResolvedPipeline) {
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

  @PUT
  @Path("/{pipelineIdentifier}")
  @ApiOperation(value = "Update a Pipeline", nickname = "putPipeline")
  @Operation(operationId = "updatePipeline", description = "Updates a Pipeline by Identifier",
      summary = "Update a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated pipeline")
      },
      deprecated = true)
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Deprecated
  public ResponseDTO<String>
  updatePipeline(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true, description = "Pipeline YAML to be updated") @NotNull String yaml) {
    log.info(String.format("Updating pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    PipelineEntity withVersion =
        PMSPipelineDtoMapper.toPipelineEntityWithVersion(accountId, orgId, projectId, pipelineId, yaml, ifMatch);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.updatePipelineYaml(withVersion, ChangeType.MODIFY);
    PipelineCRUDErrorResponse.checkForGovernanceErrorAndThrow(pipelineCRUDResult.getGovernanceMetadata());
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(), updatedEntity.getIdentifier());
  }

  @PUT
  @Path("/v2/{pipelineIdentifier}")
  @ApiOperation(value = "Update a Pipeline", nickname = "putPipelineV2")
  @Operation(operationId = "updatePipelineV2", description = "Updates a Pipeline by Identifier",
      summary = "Update a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated pipeline with metadata")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<PipelineSaveResponse>
  updatePipelineV2(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true, description = "Pipeline YAML to be updated") @NotNull String yaml) {
    log.info(String.format("Updating pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    PipelineEntity withVersion =
        PMSPipelineDtoMapper.toPipelineEntityWithVersion(accountId, orgId, projectId, pipelineId, yaml, ifMatch);
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

  @DELETE
  @Path("/{pipelineIdentifier}")
  @ApiOperation(value = "Delete a pipeline", nickname = "softDeletePipeline")
  @Operation(operationId = "deletePipeline", description = "Deletes a Pipeline by Identifier",
      summary = "Delete a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Boolean status whether request was successful or not")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  public ResponseDTO<Boolean>
  deletePipeline(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo) {
    log.info(String.format("Deleting pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));

    return ResponseDTO.newResponse(pmsPipelineService.delete(
        accountId, orgId, projectId, pipelineId, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets Pipeline list", nickname = "getPipelineList")
  @Operation(operationId = "getPipelineList", description = "Returns List of Pipelines in the Given Project",
      summary = "List Pipelines",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of pipelines.")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PMSPipelineSummaryResponseDTO>>
  getListOfPipelines(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("25") int size,
      @QueryParam("sort") @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) List<String> sort,
      @Parameter(description = PipelineResourceConstants.PIPELINE_SEARCH_TERM_PARAM_MESSAGE)
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm, @QueryParam("module") String module,
      @QueryParam("filterIdentifier") String filterIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @RequestBody(description = "This is the body for the filter properties for listing pipelines.")
      PipelineFilterPropertiesDto filterProperties,
      @Parameter(description = "Boolean flag to get distinct pipelines from all branches.") @QueryParam(
          "getDistinctFromBranches") Boolean getDistinctFromBranches) {
    log.info(String.format("Get List of pipelines in project %s, org %s, account %s", projectId, orgId, accountId));

    Criteria criteria = pipelineServiceHelper.formCriteria(
        accountId, orgId, projectId, filterIdentifier, filterProperties, false, module, searchTerm);

    Pageable pageRequest =
        PageUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));

    Page<PMSPipelineSummaryResponseDTO> pipelines =
        pmsPipelineService.list(criteria, pageRequest, accountId, orgId, projectId, getDistinctFromBranches)
            .map(PMSPipelineDtoMapper::preparePipelineSummaryForListView);

    return ResponseDTO.newResponse(pipelines);
  }

  @GET
  @Path("/summary/{pipelineIdentifier}")
  @ApiOperation(value = "Gets Pipeline Summary of a pipeline", nickname = "getPipelineSummary")
  @Operation(operationId = "getPipelineSummary", description = "Returns Pipeline Summary by Identifier",
      summary = "Fetch Pipeline Summary",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns Pipeline Summary having pipelineIdentifier as specified in request")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineSummaryResponseDTO>
  getPipelineSummary(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
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

  @POST
  @Path("/import/{pipelineIdentifier}")
  @Hidden
  @ApiOperation(value = "Get Pipeline YAML from Git Repository", nickname = "importPipeline")
  @Operation(operationId = "importPipeline", summary = "Get Pipeline YAML from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns Pipeline YAML fetched from Git Repository. No Pipeline is saved")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PipelineSaveResponse>
  importPipelineFromGit(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineId,
      @BeanParam GitImportInfoDTO gitImportInfoDTO, PipelineImportRequestDTO pipelineImportRequestDTO) {
    PipelineEntity savedPipelineEntity =
        pmsPipelineService.importPipelineFromRemote(accountId, orgId, projectId, pipelineId, pipelineImportRequestDTO);
    return ResponseDTO.newResponse(
        PipelineSaveResponse.builder().identifier(savedPipelineEntity.getIdentifier()).build());
  }

  @GET
  @Path("/expandedJSON/{pipelineIdentifier}")
  @ApiOperation(value = "Gets Pipeline JSON with extra info for some fields", nickname = "getExpandedPipelineJSON")
  @Operation(operationId = "getExpandedPipelineJSON", summary = "Gets Pipeline JSON with extra info for some fields",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Gets Pipeline JSON with extra info for some fields as required for Pipeline Governance")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  public ResponseDTO<ExpandedPipelineJsonDTO>
  getExpandedPipelineJson(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE)
                          @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    String expandedPipelineJSON = pmsPipelineService.fetchExpandedPipelineJSON(accountId, orgId, projectId, pipelineId);
    return ResponseDTO.newResponse(ExpandedPipelineJsonDTO.builder().expandedJson(expandedPipelineJSON).build());
  }

  @POST
  @Path("/v2/steps")
  @Hidden
  @ApiOperation(value = "Get Steps for given modules Version 2", nickname = "getStepsV2")
  @Operation(operationId = "getStepsV2", summary = "Gets all the Steps for given Category (V2 Version)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns steps for a given Category")
      })
  public ResponseDTO<StepCategory>
  getStepsV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                 required = true) @NotNull @QueryParam("accountId") String accountId,
      @RequestBody(required = true, description = "Step Pallete Filter request body")
      @NotNull StepPalleteFilterWrapper stepPalleteFilterWrapper) {
    return ResponseDTO.newResponse(pmsPipelineService.getStepsV2(accountId, stepPalleteFilterWrapper));
  }

  @GET
  @Path("/notification")
  @ApiOperation(value = "Get Notification Schema", nickname = "getNotificationSchema")
  @Hidden
  public ResponseDTO<NotificationRules> getNotificationSchema() {
    return ResponseDTO.newResponse(NotificationRules.builder().build());
  }

  @GET
  @Path("/getExecutionNode")
  @ApiOperation(value = "get execution node", nickname = "getExecutionNode")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getExecutionNode", summary = "Get the Execution Node by Execution Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Execution Node if it exists, else returns Null.")
      })
  @Hidden
  public ResponseDTO<ExecutionNode>
  getExecutionNode(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Id for the corresponding Node Execution", required = true) @NotNull @QueryParam(
          "nodeExecutionId") String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    return ResponseDTO.newResponse(
        nodeExecutionToExecutioNodeMapper.mapNodeExecutionToExecutionNode(nodeExecutionService.get(nodeExecutionId)));
  }

  @GET
  @Path("/dummy-pmsSteps-api")
  @ApiOperation(value = "This is dummy api to expose pmsSteps", nickname = "dummyPmsStepsApi")
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

  @GET
  @Path("/dummy-templateStep-api")
  @ApiOperation(value = "This is dummy api to expose templateStepNode", nickname = "dummyTemplateStepApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<TemplateStepNode> getTemplateStepNode() {
    return ResponseDTO.newResponse(new TemplateStepNode());
  }

  @GET
  @Path("/dummy-templateStage-api")
  @ApiOperation(value = "This is dummy api to expose templateStageNode", nickname = "dummyTemplateStageApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<TemplateStageNode> getTemplateStageNode() {
    return ResponseDTO.newResponse(new TemplateStageNode());
  }

  @GET
  @Path("/ffCache/refresh")
  @ApiOperation(value = "Refresh the feature flag cache", nickname = "refreshFFCache")
  @Operation(operationId = "refreshFFCache", summary = "Refresh the feature flag cache",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Refresh the feature flag cache")
      })
  @Hidden
  public ResponseDTO<Boolean>
  refreshFFCache(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
      required = true) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId) {
    try {
      return ResponseDTO.newResponse(pmsFeatureFlagHelper.refreshCacheForGivenAccountId(accountId));
    } catch (ExecutionException e) {
      log.error("Execution exception occurred while updating cache: " + e.getMessage());
    }
    return ResponseDTO.newResponse(false);
  }

  @POST
  @Path("/validate-yaml-with-schema")
  @ApiOperation(value = "Validate a Pipeline YAML", nickname = "validatePipelineByYAML")
  @Hidden
  @Operation(operationId = "postPipeline", summary = "Validate a Pipeline YAML with Schema",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return if Pipeline YAML is valid or not")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<String>
  validatePipelineByYAML(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                         @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml) {
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml);
    log.info(String.format("Validating the pipeline YAML with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));
    pipelineServiceHelper.validatePipelineYaml(pipelineEntity, false);
    return ResponseDTO.newResponse(pipelineEntity.getIdentifier());
  }

  @POST
  @Path("/validate-pipeline-with-schema")
  @ApiOperation(value = "Validate a Pipeline", nickname = "validatePipeline")
  @Hidden
  @Operation(operationId = "postPipeline", summary = "Validate a Pipeline with Schema",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return if Pipeline is valid or not")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public ResponseDTO<String>
  validatePipelineByIdentifier(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId) {
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

  @GET
  @Path("resolved-templates-pipeline-yaml/{pipelineIdentifier}")
  @ApiOperation(value = "Gets template resolved pipeline yaml", nickname = "getTemplateResolvedPipeline")
  @Hidden
  @Operation(operationId = "getTemplateResolvedPipeline",
      summary = "Gets template resolved pipeline yaml by pipeline identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns templates resolved pipeline YAML")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<TemplatesResolvedPipelineResponseDTO>
  getTemplateResolvedPipelineYaml(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
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
