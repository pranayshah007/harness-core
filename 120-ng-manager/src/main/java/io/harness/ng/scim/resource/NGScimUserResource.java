/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim.resource;

import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimResource;
import io.harness.scim.ScimUser;
import io.harness.scim.service.ScimUserService;
import io.harness.security.annotations.ScimAPI;

import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("scim")
@Path("/scim/account/{accountIdentifier}")
@Hidden
@Consumes({"application/scim+json", "application/json"})
@Produces("application/scim+json")
@Slf4j
@ScimAPI
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "SCIM", description = "This contains APIs related to SCIM provisioning")
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
public class NGScimUserResource extends ScimResource {
  @Inject private ScimUserService scimUserService;

  @POST
  @Path("Users")
  @ApiOperation(value = "Create a new user", nickname = "createScimUser")
  @Operation(operationId = "createScimUser", summary = "Creates a new user via SCIM",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created user")
      })
  public Response
  createUser(ScimUser userQuery, @PathParam("accountIdentifier") String accountIdentifier) {
    try {
      return scimUserService.createUser(userQuery, accountIdentifier);
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to create the user", ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.CONFLICT);
    }
  }

  @PUT
  @Path("Users/{userIdentifier}")
  @Hidden
  @ApiOperation(value = "Update an existing user by uuid", nickname = "updateScimUser")
  @Operation(operationId = "updateScimUser", summary = "Updates a SCIM user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated user")
      })
  public Response
  updateUser(@PathParam("userIdentifier") String userIdentifier,
      @PathParam("accountIdentifier") String accountIdentifier, ScimUser userQuery) {
    try {
      return scimUserService.updateUser(userIdentifier, accountIdentifier, userQuery);
    } catch (Exception ex) {
      log.info("NGSCIM: Failed to update the user with id: {} for account: {}", userIdentifier, accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @GET
  @Path("Users/{userIdentifier}")
  @Hidden
  @ApiOperation(value = "Get an existing user by uuid", nickname = "getScimUser")
  @Operation(operationId = "getScimUser", summary = "Get user details",
      description = "Gets the user details for the given Account ID and User ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the user with the requested accountIdentifier and connectorIdentifier")
      })
  public Response
  getUser(
      @PathParam("userIdentifier") String userIdentifier, @PathParam("accountIdentifier") String accountIdentifier) {
    try {
      return Response.status(Response.Status.OK)
          .entity(scimUserService.getUser(userIdentifier, accountIdentifier))
          .build();
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to fetch the user with id: {} for account: {}", userIdentifier, accountIdentifier);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @GET
  @Path("Users")
  @Hidden
  @ApiOperation(
      value =
          "Search users by their email address. Supports pagination. If nothing is passed in filter, all results will be returned.",
      nickname = "searchScimUser")
  @Operation(operationId = "searchScimUser", summary = "Search for users by email addresses",
      description =
          "Search for users by email addresses. If the filter parameter is left blank, all the users will be returned.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns the user with the requested accountIdentifier and filter")
      })
  public Response
  searchUser(@PathParam("accountIdentifier") String accountIdentifier, @QueryParam("filter") String filter,
      @QueryParam("count") Integer count, @QueryParam("startIndex") Integer startIndex) {
    try {
      ScimListResponse<ScimUser> searchUserResponse =
          scimUserService.searchUser(accountIdentifier, filter, count, startIndex);
      return Response.status(Response.Status.OK).entity(searchUserResponse).build();
    } catch (Exception ex) {
      log.error("NGSCIM: Search user call failed. AccountId: {}, filter: {}, count: {}, startIndex: {}",
          accountIdentifier, filter, count, startIndex, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @DELETE
  @Path("Users/{userIdentifier}")
  @Hidden
  @ApiOperation(value = "Delete an user by uuid", nickname = "deleteScimUser")
  @Operation(operationId = "deleteScimUser", summary = "Deletes user for the given ID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "It does not return any content")
      })
  public Response
  deleteUser(
      @PathParam("userIdentifier") String userIdentifier, @PathParam("accountIdentifier") String accountIdentifier) {
    scimUserService.deleteUser(userIdentifier, accountIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @PATCH
  @Path("Users/{userIdentifier}")
  @Hidden
  @ApiOperation(value = "Update some fields of a user by uuid", nickname = "patchScimUser")
  @Operation(operationId = "patchScimUser", summary = "Updates user for the given UUID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated User")
      })
  public ScimUser
  updateUser(@PathParam("accountIdentifier") String accountIdentifier,
      @PathParam("userIdentifier") String userIdentifier, PatchRequest patchRequest) {
    return scimUserService.updateUser(accountIdentifier, userIdentifier, patchRequest);
  }
}
