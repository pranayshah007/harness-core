/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.template.resources.NGTemplateResource.INCLUDE_ALL_TEMPLATES_ACCESSIBLE;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE_PARAM_MESSAGE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.resources.beans.NGTemplateConstants;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;

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
import java.io.IOException;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.springframework.data.domain.Page;
import retrofit2.http.Body;

@OwnedBy(CDC)
@Api("globalTemplates")
@Path("globalTemplates")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Templates", description = "This contains a list of APIs specific to the Templates")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@NextGenManagerAuth
public interface NGGlobalTemplateResource {
  @POST
  @ApiOperation(value = "Creates a Global Template", nickname = "createGlobalTemplate")
  @Operation(operationId = "createGlobalTemplate", summary = "Creates a Global Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Fetches Template YAML from Harness DB and creates a remote entity")
      })
  ResponseDTO<List<TemplateWrapperResponseDTO>>
  crud(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) @ProjectIdentifier String connectorRef,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.TARGET_BRANCH) @ProjectIdentifier String targetBranch,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created")
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true, description = "Template YAML",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Create Template YAML",
                         value = NGTemplateConstants.API_SAMPLE_TEMPLATE_YAML, description = "Sample Template YAML"))
          }) @NotNull String webhookEvent,
      @Parameter(description = "Specify true if Default Template is to be set") @QueryParam(
          "setDefaultTemplate") @DefaultValue("false") boolean setDefaultTemplate,
      @Parameter(description = "Comments") @QueryParam("comments") String comments,
      @Parameter(
          description =
              "When isNewTemplate flag is set user will not be able to create a new version for an existing template")
      @QueryParam("isNewTemplate") @DefaultValue("false") @ApiParam(hidden = true) boolean isNewTemplate)
      throws IOException;

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets all template list", nickname = "getTemplateList")
  @Operation(operationId = "getTemplateList", summary = "Get Templates",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the Templates")
      })
  @Hidden
  // will return non deleted templates only
  ResponseDTO<Page<TemplateSummaryResponseDTO>>
  listGlobalTemplates(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("25") int size,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Filter Identifier") @QueryParam("filterIdentifier") String filterIdentifier,
      @Parameter(description = "Template List Type") @NotNull @QueryParam(
          "templateListType") TemplateListType templateListType,
      @Parameter(description = "Specify true if all accessible Templates are to be included") @QueryParam(
          INCLUDE_ALL_TEMPLATES_ACCESSIBLE) Boolean includeAllTemplatesAccessibleAtScope,
      @Parameter(description = "This contains details of Git Entity like Git Branch info")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "This contains details of Template filters based on Template Types and Template Names ")
      @Body TemplateFilterPropertiesDTO filterProperties,
      @QueryParam("getDistinctFromBranches") Boolean getDistinctFromBranches);

  @GET
  @Path("{templateIdentifier}")
  @ApiOperation(value = "Gets Template", nickname = "getTemplate")
  @Operation(operationId = "getTemplate", summary = "Get Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the saved Template")
      })
  ResponseDTO<TemplateResponseDTO>
  get(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "Specifies whether Template is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "This contains details of Git Entity like Git Branch info")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch);
}
