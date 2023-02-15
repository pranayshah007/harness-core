/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.apiexamples.PipelineAPIConstants;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.inputset.InputSetSchemaConstants;
import io.harness.pms.inputset.MergeInputSetForRerunRequestDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSanitiseResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.rbac.PipelineRbacPermissions;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
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

@OwnedBy(PIPELINE)
@Api("/inputSets")
@Path("/inputSets")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })

@Tag(name = "Pipeline Input Set", description = "This contains APIs related to Input Sets")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public interface InputSetResourcePMS {
  @GET
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Gets an InputSet by identifier", nickname = "getInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getInputSet",
      description = "Returns Input Set for a Given Identifier (Throws an Error if no Input Set Exists)",
      summary = "Fetch an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Input Set if exists for the given Identifier.")
      })
  ResponseDTO<InputSetResponseDTOPMS>
  getInputSet(@PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
                  description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be fetched") String pipelineBranch,
      @QueryParam("pipelineRepoID") @Parameter(
          description = "Github Repo identifier of the Pipeline for which the Input Set is to be fetched")
      String pipelineRepoID,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache);

  @GET
  @Path("overlay/{inputSetIdentifier}")
  @ApiOperation(value = "Gets an Overlay InputSet by identifier", nickname = "getOverlayInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getOverlayInputSet", summary = "Gets an Overlay Input Set by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "The Overlay Input Set that corresponds to the given Overlay Input Set Identifier")
      })
  @Hidden
  ResponseDTO<OverlayInputSetResponseDTOPMS>
  getOverlayInputSet(
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.OVERLAY_INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be fetched") String pipelineBranch,
      @QueryParam("pipelineRepoID") @Parameter(
          description = "Github Repo identifier of the Pipeline for which the Input Set is to be fetched")
      String pipelineRepoID,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache);

  @POST
  @ApiOperation(value = "Create an InputSet For Pipeline", nickname = "createInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "postInputSet", description = "Creates an Input Set for a Pipeline",
      summary = "Create an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns created Input Set. If not, it sends what is wrong with the YAML")
      })
  ResponseDTO<InputSetResponseDTOPMS>
  createInputSet(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                     description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be created") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo identifier of the Pipeline for which the Input Set is to be created")
      String pipelineRepoID, @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true,
          description =
              "Input set YAML to be created. The Account, Org, Project, and Pipeline identifiers inside the YAML should match the query parameters.",
          content = {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Create", summary = "Sample Input Set YAML",
                    value = PipelineAPIConstants.CREATE_INPUTSET_API, description = "Sample Input Set YAML"))
          }) @NotNull String yaml);

  @POST
  @Path("overlay")
  @ApiOperation(value = "Create an Overlay InputSet For Pipeline", nickname = "createOverlayInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "postOverlayInputSet", summary = "Create an Overlay Input Set for a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns created Overlay Input Set. If not, it sends what is wrong with the YAML")
      })
  @Hidden
  ResponseDTO<OverlayInputSetResponseDTOPMS>
  createOverlayInputSet(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true,
          description =
              "Overlay Input Set YAML to be created. The Account, Org, Project, and Pipeline identifiers inside the YAML should match the query parameters")
      @NotNull String yaml);
  @PUT
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Update an InputSet by identifier", nickname = "updateInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "putInputSet", description = "Updates the Input Set for a Pipeline",
      summary = "Update an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns the updated Input Set. If not, it sends what is wrong with the YAML")
      })
  ResponseDTO<InputSetResponseDTOPMS>
  updateInputSet(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(
          description =
              "Identifier for the Input Set that needs to be updated. An Input Set corresponding to this identifier should already exist.")
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be updated") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo Id of the Pipeline for which the Input Set is to be updated")
      String pipelineRepoID, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true,
          description =
              "Input set YAML to be updated. The query parameters should match the Account, Org, Project, and Pipeline Ids in the YAML.",
          content = {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Update", summary = "Sample Input Set YAML",
                    value = PipelineAPIConstants.CREATE_INPUTSET_API, description = "Sample Input Set YAML"))
          }) @NotNull String yaml);

  @PUT
  @Path("overlay/{inputSetIdentifier}")
  @ApiOperation(value = "Update an Overlay InputSet by identifier", nickname = "updateOverlayInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "putOverlayInputSet", summary = "Update an Overlay Input Set for a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns the updated Overlay Input Set. If not, it sends what is wrong with the YAML")
      })
  @Hidden
  ResponseDTO<OverlayInputSetResponseDTOPMS>
  updateOverlayInputSet(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = "Identifier for the Overlay Input Set that needs to be updated.") @PathParam(
          NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true,
          description =
              "Overlay Input Set YAML to be updated. The Account, Org, Project, and Pipeline identifiers inside the YAML should match the query parameters, and the Overlay Input Set identifier cannot be changed.")
      @NotNull @ApiParam(hidden = true) String yaml);

  @DELETE
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Delete an InputSet by identifier", nickname = "deleteInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  @Operation(operationId = "deleteInputSet", description = "Deletes the Input Set by Identifier",
      summary = "Delete an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Deleted Input Set")
      })
  ResponseDTO<Boolean>
  delete(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = "Identifier of the Input Set that should be deleted.") @PathParam(
          NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo);

  @GET
  @ApiOperation(value = "Gets InputSets list for a pipeline", nickname = "getInputSetsListForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "listInputSet", description = "Lists all Input Sets for a Pipeline",
      summary = "List Input Sets",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch all the Input Sets for a Pipeline, including Overlay Input Sets.")
      })
  ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>>
  listInputSetsForPipeline(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") @Parameter(
                               description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Parameter(
          description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) int size,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Pipeline identifier for which we need the Input Sets list.") @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = InputSetSchemaConstants.INPUT_SET_TYPE_MESSAGE) @QueryParam(
          "inputSetType") @DefaultValue("ALL") InputSetListTypePMS inputSetListType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_SEARCH_TERM_PARAM_MESSAGE) String searchTerm,
      @QueryParam(NGResourceFilterConstants.SORT_KEY) @Parameter(
          description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) List<String> sort,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo);

  @POST
  @Path("template")
  @ApiOperation(value = "Get template from a pipeline YAML", nickname = "getTemplateFromPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "runtimeInputTemplate", description = "Returns Runtime Input Template for a Pipeline",
      summary = "Fetch Runtime Input Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Fetch Runtime Input Template for a Pipeline, along with any expressions whose value is needed for running specific Stages")
      })
  ResponseDTO<InputSetTemplateResponseDTOPMS>
  getTemplateFromPipeline(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                              description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = "Pipeline identifier for which we need the Runtime Input Template.") String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, InputSetTemplateRequestDTO inputSetTemplateRequestDTO);

  @POST
  @Path("merge")
  @ApiOperation(
      value = "Merges given Input Sets list on pipeline and return Input Set template format of applied pipeline",
      nickname = "getMergeInputSetFromPipelineTemplateWithListInput")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "mergeInputSets", summary = "Merge given Input Sets into a single Runtime Input YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Merge given Input Sets into A single Runtime Input YAML")
      })
  @Hidden
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputSetFromPipelineTemplate(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier,
      @Parameter(description = "Github branch of the Pipeline to which the Input Sets belong") @QueryParam(
          "pipelineBranch") String pipelineBranch,
      @Parameter(description = "Github Repo identifier of the Pipeline to which the Input Sets belong")
      @QueryParam("pipelineRepoID") String pipelineRepoID, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO);

  @POST
  @Path("merge-for-rerun")
  @ApiOperation(
      value =
          "Merges runtime input YAML from the given planExecutionId and return Input Set template format of applied pipeline",
      nickname = "getMergeInputSetForRun")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "mergeInputSetsForRerun",
      summary = "Merge runtime input YAML from the given planExecutionId into the pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Merge runtime input YAML from the given planExecutionId into the pipeline")
      })
  @Hidden
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputSetForRerun(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                               description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier,
      @Parameter(description = "Github branch of the Pipeline to which the Input Sets belong") @QueryParam(
          "pipelineBranch") String pipelineBranch,
      @Parameter(description = "Github Repo identifier of the Pipeline to which the Input Sets belong")
      @QueryParam("pipelineRepoID") String pipelineRepoID, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetForRerunRequestDTO mergeInputSetForRerunRequestDTO);

  @POST
  @Path("mergeWithTemplateYaml")
  @ApiOperation(
      value = "Merges given runtime input YAML on pipeline and return Input Set template format of applied pipeline",
      nickname = "getMergeInputSetFromPipelineTemplate")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "mergeRuntimeInputIntoPipeline",
      summary = "Merge given Runtime Input YAML into the Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Merge given Runtime Input YAML into the Pipeline")
      })
  @Hidden
  // TODO(Naman): Correct PipelineServiceClient when modifying this api
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputSetFromPipelineTemplate(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline to which the Input Sets belong") String pipelineBranch,
      @QueryParam("pipelineRepoID") @Parameter(
          description = "Github Repo identifier of the Pipeline to which the Input Sets belong") String pipelineRepoID,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetTemplateRequestDTO mergeInputSetTemplateRequestDTO);

  @POST
  @Path("{inputSetIdentifier}/sanitise")
  @ApiOperation(value = "Sanitise an InputSet", nickname = "sanitiseInputSet")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "sanitiseInputSet", summary = "Sanitise an Input Set by removing invalid fields",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Sanitise an Input Set by removing invalid fields from the Input Set YAML and save it")
      })
  @Hidden
  ResponseDTO<InputSetSanitiseResponseDTO>
  sanitiseInputSet(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                       description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(
          description =
              "Identifier for the Input Set that needs to be updated. An Input Set corresponding to this identifier should already exist.")
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be updated") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo Id of the Pipeline for which the Input Set is to be updated")
      String pipelineRepoID, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true,
          description = "The invalid Input Set Yaml to be sanitized") @NotNull String invalidInputSetYaml);

  @GET
  @Path("{inputSetIdentifier}/yaml-diff")
  @ApiOperation(value = "Get sanitised YAML for an InputSet", nickname = "yamlDiffForInputSet")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "yamlDiffForInputSet",
      summary = "Get sanitised YAML for an InputSet by removing invalid fields",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Sanitise an Input Set by removing invalid fields from the Input Set YAML")
      })
  @Hidden
  ResponseDTO<InputSetYamlDiffDTO>
  getInputSetYAMLDiff(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(
          description =
              "Identifier for the Input Set that needs to be updated. An Input Set corresponding to this identifier should already exist.")
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be updated") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo Id of the Pipeline for which the Input Set is to be updated")
      String pipelineRepoID, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo);

  @POST
  @Path("/import/{inputSetIdentifier}")
  @Hidden
  @ApiOperation(value = "Get Input Set YAML from Git Repository", nickname = "importInputSet")
  @Operation(operationId = "importInputSet", summary = "Get Input Set YAML from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Input Set YAML from Git Repository and saves a record for it in Harness")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  ResponseDTO<InputSetImportResponseDTO>
  importInputSetFromGit(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                            description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @BeanParam GitImportInfoDTO gitImportInfoDTO, InputSetImportRequestDTO inputSetImportRequestDTO);

  @POST
  @Path("/move-config/{inputSetIdentifier}")
  @Hidden
  @ApiOperation(
      value = "Move Input Set YAML from inline to remote or remote to inline", nickname = "inputSetMoveConfig")
  @Operation(operationId = "inputSetMoveConfig",
      summary = "Move Input Set YAML from inline to remote or remote to inline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Fetches Input Set YAML from Harness DB and creates a remote entity or Fetches Pipeline YAML from remote repository and creates a inline entity")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  ResponseDTO<InputSetMoveConfigResponseDTO>
  moveConfig(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @BeanParam InputSetMoveConfigRequestDTO inputSetMoveConfigRequestDTO);

  @GET
  @Path("/list-repos")
  @ApiOperation(value = "Gets InputSet Repository list", nickname = "getInputSetRepositoryList")
  @Operation(operationId = "getInputSetRepositoryList", description = "Gets the list of all repositories",
      summary = "List InputSet Repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the repositories of all InputSets")
      })
  @Hidden
  ResponseDTO<PMSInputSetListRepoResponse>
  getListRepos(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier);
}
