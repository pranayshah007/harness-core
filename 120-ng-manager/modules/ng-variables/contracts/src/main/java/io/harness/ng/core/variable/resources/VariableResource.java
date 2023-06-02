/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.SEARCH_TERM_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
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
import javax.ws.rs.core.MediaType;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(HarnessTeam.PL)
@Api("/variables")
@Path("/variables")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
@Tag(name = "Variables", description = "This contains APIs related to Variables as defined in Harness.")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface VariableResource {
  String INCLUDE_VARIABLES_FROM_EVERY_SUB_SCOPE = "includeVariablesFromEverySubScope";

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get a Variable", nickname = "getVariable")
  @Operation(operationId = "getVariable", summary = "Get the Variable by scope identifiers and variable identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the variable with the requested scope identifiers and variable identifier.")
      })
  ResponseDTO<VariableResponseDTO>
  get(@Parameter(description = "Variable ID") @PathParam(IDENTIFIER_KEY) @NotNull String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier);

  @POST
  @ApiOperation(value = "Create a Variable", nickname = "createVariable")
  @Operation(operationId = "createVariable", summary = "Creates a Variable.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Variable.")
      })
  ResponseDTO<VariableResponseDTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
             ACCOUNT_KEY) @NotNull String accountIdentifier,
      @RequestBody(required = true,
          description = "Details of the Variable to create.") @NotNull @Valid VariableRequestDTO variableRequestDTO);

  @GET
  @ApiOperation(value = "Gets Variable list", nickname = "getVariablesList")
  @Operation(operationId = "getVariableList", summary = "Fetches the list of Variables.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Variable.")
      })
  ResponseDTO<PageResponse<VariableResponseDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(ACCOUNT_KEY)
       String accountIdentifier, @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description =
              "This would be used to filter Variables. Any Variable having the specified string in its Name or ID would be filtered.")
      @QueryParam(SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Specify whether or not to include all the Variables"
              + " accessible at the scope. For eg if set as true, at the Project scope we will get"
              + " org and account Variable also in the response.") @QueryParam(INCLUDE_VARIABLES_FROM_EVERY_SUB_SCOPE)
      @DefaultValue("false") boolean includeVariablesFromEverySubScope,
      @BeanParam PageRequest pageRequest);

  @PUT
  @ApiOperation(value = "Update a Variable", nickname = "updateVariable")
  @Operation(operationId = "updateVariable", summary = "Updates the Variable.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Variable.")
      })
  ResponseDTO<VariableResponseDTO>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
             ACCOUNT_KEY) @NotNull String accountIdentifier,
      @RequestBody(required = true,
          description = "Details of the variable to update.") @NotNull @Valid VariableRequestDTO variableRequestDTO);

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a Variable", nickname = "deleteVariable")
  @Operation(operationId = "deleteVariable", summary = "Deletes Variable by ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "It returns true if the Variable is deleted successfully and false if the Variable is not deleted.")
      })
  ResponseDTO<Boolean>
  delete(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
             ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Variable ID") @PathParam(IDENTIFIER_KEY) @NotBlank String variableIdentifier);

  @GET
  @Path("expressions")
  @ApiOperation(value = "Gets a map of variable expressions", nickname = "listVariablesExpression")
  @Operation(operationId = "listVariableExpressions", summary = "Returns a map of variable expressions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Variable expressions.")
      })
  @Hidden
  ResponseDTO<List<String>>
  expressions(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                  ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier);
}
