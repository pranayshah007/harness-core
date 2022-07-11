/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATE_PROFILES;
import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE_SCOPE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateDTO;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfileDetails;
import io.harness.delegate.beans.ScopingRules;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateProfileManagerService;
import software.wings.service.intfc.DelegateProfileService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Created by rishi on 7/31/18
 */
@Api("delegate-profiles")
@Path("/delegate-profiles")
@Produces("application/json")
@Scope(DELEGATE_SCOPE)
@AuthRule(permissionType = LOGGED_IN)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
public class DelegateProfileResource {
  private DelegateProfileService delegateProfileService;
  private DelegateProfileManagerService delegateProfileManagerService;

  @Inject
  public DelegateProfileResource(
      DelegateProfileService delegateService, DelegateProfileManagerService delegateProfileManagerService) {
    this.delegateProfileService = delegateService;
    this.delegateProfileManagerService = delegateProfileManagerService;
  }

  @GET
  @ApiImplicitParams(
      { @ApiImplicitParam(name = "accountId", required = true, dataType = "string", paramType = "query") })
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<DelegateProfile>>
  list(@BeanParam PageRequest<DelegateProfile> pageRequest) {
    return new RestResponse<>(delegateProfileService.list(pageRequest));
  }

  @GET
  @Path("{delegateProfileId}")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateProfile> get(@PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(delegateProfileService.get(accountId, delegateProfileId));
  }

  @DELETE
  @Path("{delegateProfileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<Void> delete(@PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    delegateProfileService.delete(accountId, delegateProfileId);
    return new RestResponse<>();
  }

  @PUT
  @Path("{delegateProfileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfile> update(@PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateProfile delegateProfile) {
    delegateProfile.setAccountId(accountId);
    delegateProfile.setUuid(delegateProfileId);
    return new RestResponse<>(delegateProfileService.update(delegateProfile));
  }

  @POST
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfile> add(
      @QueryParam("accountId") @NotEmpty String accountId, DelegateProfile delegateProfile) {
    delegateProfile.setAccountId(accountId);
    return new RestResponse<>(delegateProfileService.add(delegateProfile));
  }

  @PUT
  @Path("{delegateProfileId}/selectors")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfile> updateSelectors(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId, List<String> selectors) {
    return new RestResponse<>(
        delegateProfileService.updateDelegateProfileSelectors(delegateProfileId, accountId, selectors));
  }

  @GET
  @Path("/v2/{delegateProfileId}")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateProfileDetails> getV2(@PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(delegateProfileManagerService.get(accountId, delegateProfileId));
  }

  @POST
  @Path("/v2")
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfileDetails> addV2(
      @QueryParam("accountId") @NotEmpty String accountId, DelegateProfileDetails delegateProfile) {
    delegateProfile.setAccountId(accountId);
    return new RestResponse<>(delegateProfileManagerService.add(delegateProfile));
  }

  @PUT
  @Path("/v2/{delegateProfileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfileDetails> updateV2(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateProfileDetails delegateProfile) {
    delegateProfile.setAccountId(accountId);
    delegateProfile.setUuid(delegateProfileId);
    return new RestResponse<>(delegateProfileManagerService.update(delegateProfile));
  }

  @PUT
  @Path("/v2/{delegateProfileId}/scoping-rules")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfileDetails> updateScopingRulesV2(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId, ScopingRules scopingRules) {
    return new RestResponse<>(delegateProfileManagerService.updateScopingRules(
        accountId, delegateProfileId, scopingRules.getScopingRuleDetails()));
  }

  @DELETE
  @Path("/v2/{delegateProfileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<Void> deleteV2(@PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    delegateProfileManagerService.delete(accountId, delegateProfileId);
    return new RestResponse<>();
  }

  @GET
  @Path("/v2")
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<DelegateProfileDetails>> listV2(
      @BeanParam PageRequest<DelegateProfileDetails> pageRequest, @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(delegateProfileManagerService.list(accountId, pageRequest));
  }

  @PUT
  @Path("/v2/{delegateProfileId}/selectors")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATE_PROFILES)
  public RestResponse<DelegateProfileDetails> updateSelectorsV2(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId, List<String> selectors) {
    return new RestResponse<>(delegateProfileManagerService.updateSelectors(accountId, delegateProfileId, selectors));
  }

  @GET
  @Path("/{delegateProfileId}/delegates")
  @Timed
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  @ExceptionMetered
  public RestResponse<List<DelegateDTO>> listDelegates(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(delegateProfileService.listDelegatesUsingProfile(accountId, delegateProfileId));
  }

  @GET
  @Path("/list")
  @Timed
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  @ExceptionMetered
  public RestResponse<List<DelegateProfileDetails>> listDelegatesProfiles(
      @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(delegateProfileService.listDelegateProfiles(accountId));
  }
}
