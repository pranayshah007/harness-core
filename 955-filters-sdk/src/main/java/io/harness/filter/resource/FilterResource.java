/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;

@Api("/filters")
@Path("/filters")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Filter", description = "This contains APIs related to Filter as defined in Harness")
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
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(DX)
public class FilterResource {
  private FilterService filterService;

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get Filter", nickname = "getFilter")
  @Operation(operationId = "getFilter", summary = "Return filter details",
      description = "Returns the settings of a filter for the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns Filter having filterIdentifier as specified in request")
      })
  public ResponseDTO<FilterDTO>
  get(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Filter Identifier") @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Type of Filter") @NotNull @QueryParam(
          NGCommonEntityConstants.TYPE_KEY) FilterType type) {
    return ResponseDTO.newResponse(
        filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, type));
  }

  @GET
  @ApiOperation(value = "Get Filter", nickname = "getFilterList")
  @Operation(operationId = "getConnectorListV2", summary = "Lists filters",
      description = "Lists Filters for the given criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Filters")
      })
  public ResponseDTO<PageResponse<FilterDTO>>
  list(@Parameter(description = "Page number of navigation. If left empty, default value of 0 is assumed") @QueryParam(
           NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Number of entries per page. If left empty, default value of 100 is assumed")
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") int size,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Type of Filter") @NotNull @QueryParam(
          NGCommonEntityConstants.TYPE_KEY) FilterType type) {
    return ResponseDTO.newResponse(getNGPageResponse(
        filterService.list(page, size, accountIdentifier, orgIdentifier, projectIdentifier, null, type)));
  }

  @POST
  @ApiOperation(value = "Create a Filter", nickname = "postFilter")
  @Operation(operationId = "postFilter", summary = "Create a filter", description = "Creates a Filter",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Filter")
      })
  public ResponseDTO<FilterDTO>
  create(@RequestBody(
             required = true, description = "Details of the Connector to create") @Valid @NotNull FilterDTO filterDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(filterService.create(accountIdentifier, filterDTO));
  }

  @PUT
  @ApiOperation(value = "Update a Filter", nickname = "updateFilter")
  @Operation(operationId = "updateFilter", summary = "Update a filter",
      description = "Updates the filter for the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Filter")
      })
  public ResponseDTO<FilterDTO>
  update(@RequestBody(required = true,
             description = "This is the updated Filter. This should have all the fields not just the updated ones")
         @NotNull @Valid FilterDTO filterDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(filterService.update(accountIdentifier, filterDTO));
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a filter", nickname = "deleteFilter")
  @Operation(operationId = "deleteFilter", summary = "Delete a filter",
      description = "Deletes a filter for the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Boolean status whether request was successful or not")
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Filter Identifier") @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Type of Filter") @NotNull @QueryParam(
          NGCommonEntityConstants.TYPE_KEY) FilterType type) {
    return ResponseDTO.newResponse(
        filterService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, type));
  }
}
