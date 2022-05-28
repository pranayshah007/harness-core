/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.removeRuntimeInputFromYaml;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.INPUT_SET;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.OVERLAY_INPUT_SET;
import static io.harness.utils.PageUtils.getNGPageResponse;

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
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetSchemaConstants;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.inputset.OverlayInputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSanitiseResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.utils.PageUtils;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
@Api("/inputSets")
@Path("/inputSets")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })

@Tag(name = "InputSets", description = "Contain APIs corresponding to the Input Sets, including Overlay Input Sets.")
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

@PipelineServiceAuth
@Slf4j
public class InputSetResourcePMS {
  private final PMSInputSetService pmsInputSetService;
  private final ValidateAndMergeHelper validateAndMergeHelper;

  @GET
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Gets an InputSet by identifier", nickname = "getInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getInputSet",
      summary = "Gets Input Set for a given identifier. Throws error if no Input Set exists for the given identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Input Set if exists for the given Identifier.")
      })
  public ResponseDTO<InputSetResponseDTOPMS>
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
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info(String.format("Retrieving input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    Optional<InputSetEntity> inputSetEntity = pmsInputSetService.get(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, false);
    String version = "0";
    if (inputSetEntity.isPresent()) {
      version = inputSetEntity.get().getVersion().toString();
    }

    InputSetResponseDTOPMS inputSet = PMSInputSetElementMapper.toInputSetResponseDTOPMS(inputSetEntity.orElseThrow(
        ()
            -> new InvalidRequestException(String.format(
                "InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier))));

    return ResponseDTO.newResponse(version, inputSet);
  }

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
  public ResponseDTO<OverlayInputSetResponseDTOPMS>
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
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info(String.format(
        "Retrieving overlay input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    Optional<InputSetEntity> inputSetEntity = pmsInputSetService.get(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, false);
    String version = "0";
    if (inputSetEntity.isPresent()) {
      version = inputSetEntity.get().getVersion().toString();
    }

    OverlayInputSetResponseDTOPMS overlayInputSet =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(inputSetEntity.orElseThrow(
            ()
                -> new InvalidRequestException(String.format(
                    "InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier))));

    return ResponseDTO.newResponse(version, overlayInputSet);
  }

  @POST
  @ApiOperation(value = "Create an InputSet For Pipeline", nickname = "createInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "postInputSet", summary = "Create an Input Set for a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns created Input Set. If not, it sends what is wrong with the YAML")
      })
  public ResponseDTO<InputSetResponseDTOPMS>
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
              "Input set YAML to be created. The Account, Org, Project, and Pipeline identifiers inside the YAML should match the query parameters.")
      @NotNull String yaml) {
    yaml = removeRuntimeInputFromYaml(yaml);
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    log.info(String.format("Create input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        entity.getIdentifier(), pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));

    InputSetErrorWrapperDTOPMS errorWrapperDTO = validateAndMergeHelper.validateInputSet(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, pipelineBranch, pipelineRepoID);
    if (errorWrapperDTO != null) {
      throw new InvalidInputSetException("Exception in creating the Input Set", errorWrapperDTO);
    }

    InputSetEntity createdEntity = pmsInputSetService.create(entity);
    return ResponseDTO.newResponse(
        createdEntity.getVersion().toString(), PMSInputSetElementMapper.toInputSetResponseDTOPMS(createdEntity));
  }

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
  public ResponseDTO<OverlayInputSetResponseDTOPMS>
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
      @NotNull String yaml) {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    log.info(
        String.format("Create overlay input set with identifier %s for pipeline %s in project %s, org %s, account %s",
            entity.getIdentifier(), pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));

    Map<String, String> invalidReferences = validateAndMergeHelper.validateOverlayInputSet(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    if (!invalidReferences.isEmpty()) {
      OverlayInputSetErrorWrapperDTOPMS overlayInputSetErrorWrapperDTOPMS =
          OverlayInputSetErrorWrapperDTOPMS.builder().invalidReferences(invalidReferences).build();

      throw new InvalidOverlayInputSetException(
          "Exception in creating the Overlay Input Set", overlayInputSetErrorWrapperDTOPMS);
    }

    InputSetEntity createdEntity = pmsInputSetService.create(entity);
    return ResponseDTO.newResponse(
        createdEntity.getVersion().toString(), PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(createdEntity));
  }

  @PUT
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Update an InputSet by identifier", nickname = "updateInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Operation(operationId = "putInputSet", summary = "Update Input Set for Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns the updated Input Set. If not, it sends what is wrong with the YAML")
      })
  public ResponseDTO<InputSetResponseDTOPMS>
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
              "Input set YAML to be updated. The query parameters should match the Account, Org, Project, and Pipeline Ids in the YAML.")
      @NotNull String yaml) {
    log.info(String.format("Updating input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    yaml = removeRuntimeInputFromYaml(yaml);

    InputSetErrorWrapperDTOPMS errorWrapperDTO = validateAndMergeHelper.validateInputSet(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, pipelineBranch, pipelineRepoID);
    if (errorWrapperDTO != null) {
      throw new InvalidInputSetException("Exception in updating the Input Set", errorWrapperDTO);
    }

    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    InputSetEntity entityWithVersion = entity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    InputSetEntity updatedEntity = pmsInputSetService.update(entityWithVersion, ChangeType.MODIFY);
    return ResponseDTO.newResponse(
        updatedEntity.getVersion().toString(), PMSInputSetElementMapper.toInputSetResponseDTOPMS(updatedEntity));
  }

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
  public ResponseDTO<OverlayInputSetResponseDTOPMS>
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
      @NotNull @ApiParam(hidden = true) String yaml) {
    log.info(
        String.format("Updating overlay input set with identifier %s for pipeline %s in project %s, org %s, account %s",
            inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    InputSetEntity entityWithVersion = entity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);

    Map<String, String> invalidReferences = validateAndMergeHelper.validateOverlayInputSet(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    if (!invalidReferences.isEmpty()) {
      OverlayInputSetErrorWrapperDTOPMS overlayInputSetErrorWrapperDTOPMS =
          OverlayInputSetErrorWrapperDTOPMS.builder().invalidReferences(invalidReferences).build();

      throw new InvalidOverlayInputSetException(
          "Exception in updating the Overlay Input Set", overlayInputSetErrorWrapperDTOPMS);
    }

    InputSetEntity updatedEntity = pmsInputSetService.update(entityWithVersion, ChangeType.MODIFY);
    return ResponseDTO.newResponse(
        updatedEntity.getVersion().toString(), PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(updatedEntity));
  }

  @DELETE
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Delete an InputSet by identifier", nickname = "deleteInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  @Operation(operationId = "deleteInputSet", summary = "Delete the Input Set by Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Deleted Input Set")
      })
  public ResponseDTO<Boolean>
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
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo) {
    log.info(String.format("Deleting input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    return ResponseDTO.newResponse(pmsInputSetService.delete(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, inputSetIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @GET
  @ApiOperation(value = "Gets InputSets list for a pipeline", nickname = "getInputSetsListForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "listInputSet", summary = "List all Input Sets for a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch all the Input Sets for a Pipeline, including Overlay Input Sets.")
      })
  public ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>>
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
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    log.info(String.format("Get List of input sets for pipeline %s in project %s, org %s, account %s",
        pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetListType, searchTerm, false);

    Pageable pageRequest =
        PageUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.createdAt));

    Page<InputSetSummaryResponseDTOPMS> inputSetList =
        pmsInputSetService.list(criteria, pageRequest, accountId, orgIdentifier, projectIdentifier)
            .map(inputSetEntity -> {
              InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS = null;
              if (inputSetEntity.getIsInvalid() && inputSetEntity.getInputSetEntityType() == INPUT_SET) {
                inputSetErrorWrapperDTOPMS = validateAndMergeHelper.validateInputSet(accountId, orgIdentifier,
                    projectIdentifier, pipelineIdentifier, inputSetEntity.getYaml(), gitEntityBasicInfo.getBranch(),
                    gitEntityBasicInfo.getYamlGitConfigId());
              }
              Map<String, String> overlaySetErrorDetails = null;
              if (inputSetEntity.getIsInvalid() && inputSetEntity.getInputSetEntityType() == OVERLAY_INPUT_SET) {
                overlaySetErrorDetails = validateAndMergeHelper.validateOverlayInputSet(
                    accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetEntity.getYaml());
              }
              return PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(
                  inputSetEntity, inputSetErrorWrapperDTOPMS, overlaySetErrorDetails);
            });
    return ResponseDTO.newResponse(getNGPageResponse(inputSetList));
  }

  @POST
  @Path("template")
  @ApiOperation(value = "Get template from a pipeline YAML", nickname = "getTemplateFromPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "runtimeInputTemplate", summary = "Fetch Runtime Input Template for a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Fetch Runtime Input Template for a Pipeline, along with any expressions whose value is needed for running specific Stages")
      })
  public ResponseDTO<InputSetTemplateResponseDTOPMS>
  getTemplateFromPipeline(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                              description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = "Pipeline identifier for which we need the Runtime Input Template.") String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, InputSetTemplateRequestDTO inputSetTemplateRequestDTO) {
    log.info(String.format("Get template for pipeline %s in project %s, org %s, account %s", pipelineIdentifier,
        projectIdentifier, orgIdentifier, accountId));
    List<String> stageIdentifiers =
        inputSetTemplateRequestDTO == null ? Collections.emptyList() : inputSetTemplateRequestDTO.getStageIdentifiers();
    InputSetTemplateResponseDTOPMS response = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, stageIdentifiers);
    return ResponseDTO.newResponse(response);
  }

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
  public ResponseDTO<MergeInputSetResponseDTOPMS>
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
      @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO) {
    List<String> inputSetReferences = mergeInputSetRequestDTO.getInputSetReferences();
    String mergedYaml = validateAndMergeHelper.getMergeInputSetFromPipelineTemplate(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID,
        mergeInputSetRequestDTO.getStageIdentifiers());
    String fullYaml = "";
    if (mergeInputSetRequestDTO.isWithMergedPipelineYaml()) {
      fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, mergedYaml, pipelineBranch, pipelineRepoID,
          mergeInputSetRequestDTO.getStageIdentifiers());
    }
    return ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                       .isErrorResponse(false)
                                       .pipelineYaml(mergedYaml)
                                       .completePipelineYaml(fullYaml)
                                       .build());
  }

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
  // TODO(Naman): Correct PipelineServiceClient when modifying this api
  public ResponseDTO<MergeInputSetResponseDTOPMS>
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
      @NotNull @Valid MergeInputSetTemplateRequestDTO mergeInputSetTemplateRequestDTO) {
    String fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, mergeInputSetTemplateRequestDTO.getRuntimeInputYaml(), pipelineBranch, pipelineRepoID,
        null);
    return ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                       .isErrorResponse(false)
                                       .pipelineYaml(mergeInputSetTemplateRequestDTO.getRuntimeInputYaml())
                                       .completePipelineYaml(fullYaml)
                                       .build());
  }

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
  public ResponseDTO<InputSetSanitiseResponseDTO>
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
          description = "The invalid Input Set Yaml to be sanitized") @NotNull String invalidInputSetYaml) {
    String pipelineYaml = validateAndMergeHelper.getPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineBranch, pipelineRepoID);
    String sanitizedRuntimeInputYaml = InputSetSanitizer.sanitizeInputSet(pipelineYaml, invalidInputSetYaml);
    if (EmptyPredicate.isEmpty(sanitizedRuntimeInputYaml)) {
      return ResponseDTO.newResponse(InputSetSanitiseResponseDTO.builder().shouldDeleteInputSet(true).build());
    }
    String newInputSetYaml = InputSetYamlHelper.setPipelineComponent(invalidInputSetYaml, sanitizedRuntimeInputYaml);

    log.info(String.format("Updating input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    newInputSetYaml = removeRuntimeInputFromYaml(newInputSetYaml);

    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, newInputSetYaml);
    InputSetEntity updatedEntity = pmsInputSetService.update(entity, ChangeType.MODIFY);
    return ResponseDTO.newResponse(
        InputSetSanitiseResponseDTO.builder()
            .shouldDeleteInputSet(false)
            .inputSetUpdateResponse(PMSInputSetElementMapper.toInputSetResponseDTOPMS(updatedEntity))
            .build());
  }
}
