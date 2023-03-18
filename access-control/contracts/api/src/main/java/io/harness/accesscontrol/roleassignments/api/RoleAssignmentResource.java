/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

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
import java.util.Set;
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
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("roleassignments")
@Path("roleassignments")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = AccessDeniedErrorDTO.class, message = "Unauthorized")
    })
@Tag(name = "Role Assignments", description = "This contains APIs for CRUD on role assignments")
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
public interface RoleAssignmentResource {
  @GET
  @ApiOperation(value = "Get Role Assignments", nickname = "getRoleAssignmentList")
  @Operation(operationId = "getRoleAssignmentList", summary = "List Role Assignments",
      description = "List role assignments in the given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Paginated list of role assignments in the given scope")
      })
  ResponseDTO<PageResponse<RoleAssignmentResponseDTO>>
  get(@BeanParam PageRequest pageRequest, @BeanParam HarnessScopeParams harnessScopeParams);

  @POST
  @Path("filter")
  @ApiOperation(value = "Get Filtered Role Assignments", nickname = "getFilteredRoleAssignmentList")
  @Operation(operationId = "getFilteredRoleAssignmentList", summary = "List Role Assignments by filter",
      description = "List role assignments in the scope according to the given filter",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Paginated list of role assignments in the scope according to the given filter")
      })
  ResponseDTO<PageResponse<RoleAssignmentResponseDTO>>
  get(@BeanParam PageRequest pageRequest, @BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "Filter role assignments based on multiple parameters.",
          required = true) @Body RoleAssignmentFilterDTO roleAssignmentFilter);

  @POST
  @Path("filter/internal")
  @Hidden
  @ApiOperation(value = "Get Filtered Role Assignments", nickname = "getFilteredRoleAssignmentsWithInternalRoles")
  @Operation(operationId = "getFilteredRoleAssignmentsWithInternalRoles", summary = "List Role Assignments by filter",
      description = "List role assignments in the scope according to the given filter along with internal roles",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Paginated list of role assignments in the scope according to the given filter")
      })
  ResponseDTO<PageResponse<RoleAssignmentResponseDTO>>
  getFilteredRoleAssignmentsWithInternalRoles(@BeanParam PageRequest pageRequest,
      @BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "Filter role assignments based on multiple parameters.",
          required = true) @Body RoleAssignmentFilterDTO roleAssignmentFilter);

  @POST
  @Path("v2/filter")
  @InternalApi
  @ApiOperation(value = "Get Filtered Role Assignments By Scopes", nickname = "getFilteredRoleAssignmentByScopeList")
  @Operation(operationId = "getFilteredRoleAssignmentByScopeList", summary = "List Role Assignments by scope filter",
      description = "List role assignments in the scope according to the given filter",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Paginated list of role assignments in the scope according to the given filter")
      })
  ResponseDTO<PageResponse<RoleAssignmentAggregate>>
  getList(@BeanParam PageRequest pageRequest, @BeanParam HarnessScopeParams harnessScopeParams,
      @Body RoleAssignmentFilterV2 roleAssignmentFilterV2);

  @POST
  @Path("filter/internal/childscopes")
  @Hidden
  @ApiOperation(value = "Get Filtered Role Assignments including child scopes",
      nickname = "getFilteredRoleAssignmentListIncludingChildScopes", hidden = true)
  @Operation(operationId = "getFilteredRoleAssignmentListIncludingChildScopes",
      summary = "List role assignments at provided scope and its child scopes according to the given filter",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description =
                        "List of role assignments at provided scope and its child scopes according to the given filter")
      },
      hidden = true)
  ResponseDTO<List<RoleAssignmentResponseDTO>>
  getAllIncludingChildScopes(@BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "Filter role assignments based on multiple parameters.",
          required = true) @Body RoleAssignmentFilterDTO roleAssignmentFilterDTO);

  @POST
  @Path("aggregate")
  @ApiOperation(value = "Get Role Assignments Aggregate", nickname = "getRoleAssignmentsAggregate")
  @Operation(operationId = "getRoleAssignmentAggregateList", summary = "List Aggregated Role Assignments by filter",
      description = "List role assignments in the scope according to the given filter with added metadata",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description =
                "Paginated list of role assignments in the scope according to the given filter with added metadata.")
      })
  ResponseDTO<RoleAssignmentAggregateResponseDTO>
  getAggregated(@BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "Filter role assignments based on multiple parameters.",
          required = true) @Body RoleAssignmentFilterDTO roleAssignmentFilter);

  @POST
  @ApiOperation(value = "Create Role Assignment", nickname = "postRoleAssignment")
  @Operation(operationId = "postRoleAssignment", summary = "Create Role Assignment",
      description = "Creates role assignment within the specified scope.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "These are details of the created role assignment.")
      })
  ResponseDTO<RoleAssignmentResponseDTO>
  create(@BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "These are details for the role assignment to create.",
          required = true) @Body RoleAssignmentDTO roleAssignmentDTO);

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update Role Assignment", nickname = "putRoleAssignment")
  @Hidden
  @Operation(operationId = "putRoleAssignment",
      summary =
          "Update existing role assignment by identifier and scope. Only changing the disabled/enabled state is allowed.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This has the details of the updated Role Assignment.")
      },
      hidden = true)
  ResponseDTO<RoleAssignmentResponseDTO>
  update(@Parameter(description = "Identifier of the role assignment to update") @NotNull @PathParam(IDENTIFIER_KEY)
         String identifier, @BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "This has the details of the updated role assignment.",
          required = true) @Body RoleAssignmentDTO roleAssignmentDTO);

  @POST
  @Path("/multi")
  @ApiOperation(value = "Create Multiple Role Assignments", nickname = "postRoleAssignments")
  @Operation(operationId = "postRoleAssignments", summary = "Create Role Assignments",
      description =
          "Create multiple role assignments in a scope. Returns all successfully created role assignments. Ignores failures and duplicates.",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully created role assignments") })
  ResponseDTO<List<RoleAssignmentResponseDTO>>
  create(@BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "List of role assignments to create",
          required = true) @Body RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO);

  @POST
  @Path("/multi/internal")
  @InternalApi
  @ApiOperation(value = "Create Multiple Role Assignments", nickname = "createRoleAssignmentsInternal", hidden = true)
  @Operation(operationId = "createRoleAssignmentsInternal",
      summary =
          "Create multiple role assignments in a scope. Returns all successfully created role assignments. Ignores failures and duplicates.",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully created role assignments") },
      hidden = true)
  ResponseDTO<List<RoleAssignmentResponseDTO>>
  create(@BeanParam HarnessScopeParams harnessScopeParams,
      @Body RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO,
      @QueryParam("managed") @DefaultValue("false") Boolean managed);

  @POST
  @Path("/validate")
  @ApiOperation(value = "Validate Role Assignment", nickname = "validateRoleAssignment")
  @Operation(operationId = "validateRoleAssignment", summary = "Validate Role Assignment",
      description = "Check whether a proposed role assignment is valid.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "This is the result of the role assignment validation request.")
      })
  ResponseDTO<RoleAssignmentValidationResponseDTO>
  validate(@BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "This is the details of the role assignment for validation.",
          required = true) @Body RoleAssignmentValidationRequestDTO validationRequest);

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete Role Assignment", nickname = "deleteRoleAssignment")
  @Operation(operationId = "deleteRoleAssignment", summary = "Delete Role Assignment",
      description = "Delete an existing role assignment by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Deleted role assignment") })
  ResponseDTO<RoleAssignmentResponseDTO>
  delete(@BeanParam HarnessScopeParams harnessScopeParams,
      @Parameter(description = "Identifier for role assignment") @NotEmpty @PathParam(
          IDENTIFIER_KEY) String identifier);

  @POST
  @Path("/delete/batch")
  @ApiOperation(value = "Delete Role Assignment", nickname = "bulkDeleteRoleAssignment")
  @Operation(operationId = "bulkDeleteRoleAssignment", summary = "Bulk Delete Role Assignment",
      description = "Bulk delete role assignments by identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns successfully/failed deleted roleassignment.")
      })
  ResponseDTO<RoleAssignmentDeleteResponseDTO>
  bulkDelete(@BeanParam HarnessScopeParams harnessScopeParams,
      @RequestBody(description = "List of role assigment identifiers to be deleted",
          required = true) @Body Set<String> roleAssignmentIdentifiers);

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get Role Assignment", nickname = "getRoleAssignment")
  @Operation(operationId = "getRoleAssignment", summary = "Get Role Assignment",
      description = "Get an existing role assignment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Get an existing role assignment by identifier in the given scope")
      })
  ResponseDTO<RoleAssignmentResponseDTO>
  get(@BeanParam HarnessScopeParams harnessScopeParams,
      @Parameter(description = "Identifier for role assignment") @NotEmpty @PathParam(
          IDENTIFIER_KEY) String identifier);
}
