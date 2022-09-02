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
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;
import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE_SCOPE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateScope;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateScopeService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by brett on 8/4/17
 */
@Api("delegate-scopes")
@Path("/delegate-scopes")
@Produces("application/json")
@Scope(DELEGATE_SCOPE)
@AuthRule(permissionType = LOGGED_IN)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
public class DelegateScopeResource {
  private DelegateScopeService delegateScopeService;

  @Inject
  public DelegateScopeResource(DelegateScopeService delegateService) {
    this.delegateScopeService = delegateService;
  }

  @GET
  @ApiImplicitParams(
      { @ApiImplicitParam(name = "accountId", required = true, dataType = "string", paramType = "query") })
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<DelegateScope>>
  list(@BeanParam PageRequest<DelegateScope> pageRequest) {
    return new RestResponse<>(delegateScopeService.list(pageRequest));
  }

  @GET
  @Path("{delegateScopeId}")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateScope> get(@PathParam("delegateScopeId") @NotEmpty String delegateScopeId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(delegateScopeService.get(accountId, delegateScopeId));
  }

  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @DELETE
  @Path("{delegateScopeId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Void> delete(@PathParam("delegateScopeId") @NotEmpty String delegateScopeId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    delegateScopeService.delete(accountId, delegateScopeId);
    return new RestResponse<>();
  }

  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @PUT
  @Path("{delegateScopeId}")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateScope> update(@PathParam("delegateScopeId") @NotEmpty String delegateScopeId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateScope delegateScope) {
    delegateScope.setAccountId(accountId);
    delegateScope.setUuid(delegateScopeId);
    return new RestResponse<>(delegateScopeService.update(delegateScope));
  }

  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @POST
  public RestResponse<DelegateScope> add(
      @QueryParam("accountId") @NotEmpty String accountId, DelegateScope delegateScope) {
    delegateScope.setAccountId(accountId);
    return new RestResponse<>(delegateScopeService.add(delegateScope));
  }
}
