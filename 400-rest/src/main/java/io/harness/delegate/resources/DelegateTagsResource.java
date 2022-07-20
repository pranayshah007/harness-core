/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;
import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateDTO;
import io.harness.delegate.beans.DelegateTags;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@Api("/delegate")
@Path("/delegate")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
public class DelegateTagsResource {
  private final DelegateService delegateService;

  @Inject
  public DelegateTagsResource(DelegateService delegateService) {
    this.delegateService = delegateService;
  }

  @GET
  @Path("{delegateId}/delegate-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateDTO> listTags(
      @PathParam("delegateId") @NotEmpty String delegateId, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.listDelegateTags(accountId, delegateId));
    }
  }

  @POST
  @Path("{delegateId}/delegate-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateDTO> addTags(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, @Body @NotNull DelegateTags delegateTags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.addDelegateTags(accountId, delegateId, delegateTags));
    }
  }

  @PUT
  @Path("{delegateId}/delegate-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateDTO> updateTags(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, @Body @NotNull DelegateTags delegateTags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.updateDelegateTags(accountId, delegateId, delegateTags));
    }
  }

  @DELETE
  @Path("{delegateId}/delegate-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateDTO> deleteTags(
      @PathParam("delegateId") @NotEmpty String delegateId, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.deleteDelegateTags(accountId, delegateId));
    }
  }

  @POST
  @Path("/filter-with-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<List<DelegateDTO>> listDelegatesHavingTags(
      @QueryParam("accountId") @NotEmpty String accountId, @Body @NotNull DelegateTags delegateTags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.listDelegatesHavingTags(accountId, delegateTags));
    }
  }
}
