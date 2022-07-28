/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import io.harness.scim.PatchRequest;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimResource;
import io.harness.scim.service.ScimGroupService;
import io.harness.security.annotations.ScimAPI;

import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import javax.ws.rs.core.Response.Status;
import lombok.extern.slf4j.Slf4j;

@Api("scim")
@Path("/scim/account/{accountId}/")
@Consumes({"application/scim+json", "application/json"})
@Produces("application/scim+json")
@Slf4j
@ScimAPI
public class ScimGroupResource extends ScimResource {
  @Inject private ScimGroupService scimGroupService;

  @POST
  @Path("Groups")
  @ApiOperation(value = "Create a new group and return uuid in response")
  public Response createGroup(ScimGroup groupQuery, @PathParam("accountId") String accountId) {
    try {
      return Response.status(Status.CREATED).entity(scimGroupService.createGroup(groupQuery, accountId)).build();
    } catch (Exception ex) {
      log.error("SCIM: Failed to create the group {}, for accountId {}", groupQuery.getDisplayName(), accountId, ex);
      return getExceptionResponse(ex, Status.NOT_FOUND.getStatusCode(), Status.CONFLICT);
    }
  }

  @GET
  @Path("Groups/{groupId}")
  @ApiOperation(value = "Fetch an existing user by uuid")
  public Response getGroup(@PathParam("accountId") String accountId, @PathParam("groupId") String groupId) {
    try {
      return Response.status(Response.Status.OK).entity(scimGroupService.getGroup(groupId, accountId)).build();
    } catch (Exception ex) {
      log.error("SCIM: Failed to fetch the groups with id: {}, for accountId {}", groupId, accountId, ex);
      return getExceptionResponse(ex, Status.NOT_FOUND.getStatusCode(), Status.NOT_FOUND);
    }
  }

  @DELETE
  @Path("Groups/{groupId}")
  @ApiOperation(value = "Delete an existing user by uuid")
  public Response deleteGroup(@PathParam("accountId") String accountId, @PathParam("groupId") String groupId) {
    scimGroupService.deleteGroup(groupId, accountId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @GET
  @Path("Groups")
  @ApiOperation(
      value =
          "Search groups by their name. Supports pagination. If nothing is passed in filter, all results will be returned.")
  public Response
  searchGroup(@PathParam("accountId") String accountId, @QueryParam("filter") String filter,
      @QueryParam("count") Integer count, @QueryParam("startIndex") Integer startIndex) {
    // there could be fields related to exclude fields.
    try {
      ScimListResponse<ScimGroup> groupResources = scimGroupService.searchGroup(filter, accountId, count, startIndex);
      return Response.status(Response.Status.OK).entity(groupResources).build();
    } catch (Exception ex) {
      log.error("SCIM: Search group call failed. AccountId: {}, filter: {}, count: {}, startIndex{}", accountId, filter,
          count, startIndex, ex);
      return getExceptionResponse(ex, Status.NOT_FOUND.getStatusCode(), Status.NOT_FOUND);
    }
  }

  @PATCH
  @Path("Groups/{groupId}")
  @ApiOperation(value = "Update some fields of a groups by uuid. Can update members/name")
  public Response updateGroup(
      @PathParam("accountId") String accountId, @PathParam("groupId") String groupId, PatchRequest patchRequest) {
    // there could be fields related to exclude fields.
    return scimGroupService.updateGroup(groupId, accountId, patchRequest);
  }

  @PUT
  @Path("Groups/{groupId}")
  @ApiOperation(value = "Update a group")
  public Response updateGroup(
      @PathParam("accountId") String accountId, @PathParam("groupId") String groupId, ScimGroup groupQuery) {
    // there could be fields related to exclude fields.
    try {
      return scimGroupService.updateGroup(groupId, accountId, groupQuery);
    } catch (Exception ex) {
      log.error("SCIM: Failed to update the group with id: {}, accountId {} ", groupId, accountId, ex);
      return getExceptionResponse(ex, Status.NOT_FOUND.getStatusCode(), Status.PRECONDITION_FAILED);
    }
  }
}
