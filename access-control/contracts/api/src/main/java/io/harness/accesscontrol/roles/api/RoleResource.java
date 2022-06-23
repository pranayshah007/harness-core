/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ACCOUNT_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ORG_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.PROJECT_LEVEL_PARAM_NAME;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

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
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("/roles")
@Path("/roles")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = AccessDeniedErrorDTO.class, message = "Unauthorized")
    })
@Tag(name = "Roles", description = "This contains APIs for CRUD on roles")
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
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unauthorized",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = AccessDeniedErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AccessDeniedErrorDTO.class))
    })
public interface RoleResource {
  @GET
  @ApiOperation(value = "Get Roles", nickname = "getRoleList")
  @Operation(operationId = "getRoleList", summary = "List Roles", description = "List roles in the given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Paginated list of roles in the given scope")
      })
  ResponseDTO<PageResponse<RoleResponseDTO>>
  get(@BeanParam PageRequest pageRequest, @BeanParam HarnessScopeParams harnessScopeParams,
      @Parameter(description = "Search roles by name/identifier") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm);

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get Role", nickname = "getRole")
  @Operation(operationId = "getRole", summary = "Get Role", description = "Get a Role by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Queried Role") })
  ResponseDTO<RoleResponseDTO>
  get(@Parameter(description = "Identifier of the Role") @NotEmpty @PathParam(IDENTIFIER_KEY) String identifier,
      @BeanParam HarnessScopeParams harnessScopeParams);

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update Role", nickname = "putRole")
  @Operation(operationId = "putRole", summary = "Update Role", description = "Update a Custom Role by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Updated Role") })
  ResponseDTO<RoleResponseDTO>
  update(@Parameter(description = "Identifier of the Role") @NotNull @PathParam(IDENTIFIER_KEY) String identifier,
      @BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "Updated Role entity", required = true) @Body RoleDTO roleDTO);

  @POST
  @ApiOperation(value = "Create Role", nickname = "postRole")
  @Operation(operationId = "postRole", summary = "Create Role", description = "Create a Custom Role in a scope",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Created Role") })
  ResponseDTO<RoleResponseDTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotEmpty @QueryParam(
             ACCOUNT_LEVEL_PARAM_NAME) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_LEVEL_PARAM_NAME) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_LEVEL_PARAM_NAME) String projectIdentifier,
      @RequestBody(description = "Role entity", required = true) @Body RoleDTO roleDTO);

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete Role", nickname = "deleteRole")
  @Operation(operationId = "deleteRole", summary = "Delete Role", description = "Delete a Custom Role in a scope",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Deleted Role") })
  ResponseDTO<RoleResponseDTO>
  delete(@Parameter(description = "Identifier of the Role") @NotNull @PathParam(IDENTIFIER_KEY) String identifier,
      @BeanParam HarnessScopeParams harnessScopeParams);
}
