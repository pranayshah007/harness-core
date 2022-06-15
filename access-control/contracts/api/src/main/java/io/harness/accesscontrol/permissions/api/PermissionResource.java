/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.OwnedBy;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/permissions")
@Path("/permissions")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Permissions", description = "This contains the APIs related to permissions")
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
@OwnedBy(PL)
public interface PermissionResource {
  @GET
  @ApiOperation(value = "Get All Permissions in a Scope", nickname = "getPermissionList")
  @Operation(operationId = "getPermissionList", summary = "List Permissions",
      description = "Get all permissions in a scope or all permissions in the system",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of all permissions") })
  ResponseDTO<List<PermissionResponseDTO>>
  get(@BeanParam HarnessScopeParams scopeParams,
      @Parameter(
          description =
              "This is to enable or disable filtering by scope. The default value is false. If the value is true, all the permissions in the system are fetched.")
      @QueryParam("scopeFilterDisabled") boolean scopeFilterDisabled);

  @GET
  @Path("/resourcetypes")
  @ApiOperation(
      value = "Get All Resource Types for Permissions in a Scope", nickname = "getPermissionResourceTypesList")
  @Operation(operationId = "getPermissionResourceTypesList", summary = "List Resource Types",
      description = "Get all resource types for permissions in a scope or in the system.",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of resource types") })
  ResponseDTO<Set<String>>
  getResourceTypes(@BeanParam HarnessScopeParams scopeParams,
      @Parameter(
          description =
              "This is to enable or disable filtering by scope. The default value is false. If the value is true, all the permissions in the system are fetched.")
      @QueryParam("scopeFilterDisabled") boolean scopeFilterDisabled);
}
