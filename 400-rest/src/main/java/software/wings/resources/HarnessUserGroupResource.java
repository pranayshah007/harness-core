/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;

import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_RESTRICTED_ACCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.security.HarnessSupportUserDTO;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.beans.security.HarnessUserGroupDTO;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.HarnessUserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

@Api("harnessUserGroup")
@Path("/harnessUserGroup")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public class HarnessUserGroupResource {
  private final HarnessUserGroupService harnessUserGroupService;

  @Inject
  public HarnessUserGroupResource(HarnessUserGroupService harnessUserGroupService) {
    this.harnessUserGroupService = harnessUserGroupService;
  }

  @POST
  @Path("{accountId}")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<HarnessUserGroup> create(
      @PathParam("accountId") String accountId, @RequestBody HarnessUserGroupDTO harnessUserGroupDTO) {
    return new RestResponse<>(harnessUserGroupService.createHarnessUserGroup(accountId, harnessUserGroupDTO));
  }

  @DELETE
  @Path("{accountId}/{harnessUserGroupId}")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Boolean> deleteHarnessUserGroup(
      @PathParam("accountId") String accountId, @PathParam("harnessUserGroupId") String harnessUserGroupId) {
    return new RestResponse<>(harnessUserGroupService.delete(accountId, harnessUserGroupId));
  }

  // TODO: EndPoint to be deleted once UI is created for AccessRequest
  @DELETE
  @Path("{accountId}/deleteHarnessUserGroupWorkflow/{harnessGroupAccountId}/{harnessUserGroupId}")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Boolean> deleteHarnessUserGroupWorkflow(@PathParam("accountId") String accountId,
      @PathParam("harnessGroupAccountId") String harnessGroupAccountId,
      @PathParam("harnessUserGroupId") String harnessUserGroupId) {
    return new RestResponse<>(harnessUserGroupService.delete(harnessGroupAccountId, harnessUserGroupId));
  }

  @PUT
  @Path("{accountId}/{harnessUserGroupId}")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<HarnessUserGroup> updateHarnessUserGroupMembers(@PathParam("accountId") String accountId,
      @PathParam("harnessUserGroupId") String harnessUserGroupId,
      @RequestBody HarnessUserGroupDTO harnessUserGroupDTO) {
    return new RestResponse<>(
        harnessUserGroupService.updateMembers(harnessUserGroupId, accountId, harnessUserGroupDTO));
  }

  @GET
  @Path("listHarnessUserGroupForAccount")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<List<HarnessUserGroup>> listHarnessUserGroup(@QueryParam("accountId") String accountId) {
    List<HarnessUserGroup> harnessUserGroups = harnessUserGroupService.listHarnessUserGroupForAccount(accountId);
    return new RestResponse<>(harnessUserGroups);
  }

  @GET
  @Path("checkIfHarnessSupportEnabledForAccount")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Boolean> checkIfHarnessSupportEnabledForAccount(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(harnessUserGroupService.isHarnessSupportEnabledForAccount(accountId));
  }

  @GET
  @Path("supportEnabledStatus")
  @NextGenManagerAuth
  public RestResponse<Boolean> checkIfHarnessSupportEnabledForAccountInternal(
      @QueryParam("accountId") String accountId) {
    return new RestResponse<>(harnessUserGroupService.isHarnessSupportEnabledForAccount(accountId));
  }

  @GET
  @Path("listAllHarnessSupportUsers")
  @AuthRule(permissionType = MANAGE_RESTRICTED_ACCESS)
  public RestResponse<List<HarnessSupportUserDTO>> listAllHarnessSupportUsers(
      @QueryParam("accountId") String accountId) {
    return new RestResponse<>(
        harnessUserGroupService.toHarnessSupportUser(harnessUserGroupService.listAllHarnessSupportUsers()));
  }

  @GET
  @Path("supportUsers")
  @NextGenManagerAuth
  public RestResponse<List<HarnessSupportUserDTO>> getSupportUsersInternal() {
    List<HarnessSupportUserDTO> supportUsers =
        harnessUserGroupService.toHarnessSupportUser(harnessUserGroupService.listAllHarnessSupportUserInternal());
    return new RestResponse<>(supportUsers);
  }

  @GET
  @Path("is-harness-support-user-id")
  @NextGenManagerAuth
  public RestResponse<Boolean> isHarnessSupportUserId(@QueryParam("userId") String userId) {
    boolean harnessSupportUser = harnessUserGroupService.isHarnessSupportUser(userId);
    return new RestResponse<>(harnessSupportUser);
  }
}
