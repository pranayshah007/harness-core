/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE_PARAM_MESSAGE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.resources.beans.NGTemplateConstants;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import java.util.Map;
import javax.validation.constraints.NotNull;
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
@Tag(name = "Global Templates", description = "This contains a list of APIs specific to the Global Templates")
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
  @Hidden
  @ApiOperation(value = "Creates a Global Template", nickname = "createGlobalTemplate")
  @Operation(operationId = "createGlobalTemplate", summary = "Creates a Global Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Template YAML from git and create global template to Harness DB")
      })
  ResponseDTO<List<TemplateWrapperResponseDTO>>
  createAndUpdate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) @ProjectIdentifier String connectorRef,
      @RequestBody(required = true, description = "Template YAML",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Create Template YAML",
                         value = NGTemplateConstants.API_SAMPLE_TEMPLATE_YAML, description = "Sample Template YAML"))
          }) @NotNull Map<String, Object> webhookEvent,
      @Parameter(description = "Comments") @QueryParam("comments") String comments);

  @GET
  @Path("/inputs/{globalTemplateIdentifier}")
  @ApiOperation(value = "Gets global template input set yaml", nickname = "getGlobalTemplateInputSetYaml")
  @Operation(operationId = "getGlobalTemplateInputSetYaml", summary = "Gets Global Template Input Set YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Template Input Set YAML")
      })
  ResponseDTO<String>
  getTemplateInputsYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "globalTemplateIdentifier") @ResourceIdentifier String globalTemplateIdentifier,
      @Parameter(description = "Template Label") @NotNull @QueryParam(NGCommonEntityConstants.VERSION_LABEL_KEY)
      String templateLabel, @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache);

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets all Global template list", nickname = "getGlobalTemplateList")
  @Operation(operationId = "getGlobalTemplateList", summary = "Gets all Global template list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the Global Templates")
      })
  @Hidden
  // will return non deleted templates only
  ResponseDTO<Page<TemplateSummaryResponseDTO>>
  listGlobalTemplates(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
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
      @Parameter(description = "This contains details of Template filters based on Template Types and Template Names ")
      @Body TemplateFilterPropertiesDTO filterProperties);
}