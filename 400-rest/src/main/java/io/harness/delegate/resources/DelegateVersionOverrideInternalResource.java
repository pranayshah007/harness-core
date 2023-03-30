/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;
import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.datahandler.services.AdminDelegateVersionService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;

@Api("/version-override/internal")
@Path("/version-override/internal")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Hidden
@InternalApi
public class DelegateVersionOverrideInternalResource {
  private final AdminDelegateVersionService adminDelegateVersionService;

  @Inject
  public DelegateVersionOverrideInternalResource(AdminDelegateVersionService adminDelegateVersionService) {
    this.adminDelegateVersionService = adminDelegateVersionService;
  }

  @PUT
  @Path("/delegate-tag")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @InternalApi
  public RestResponse<String> overrideDelegateImageTag(@Context HttpServletRequest request,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @QueryParam("delegateTag") @NotNull String delegateTag,
      @QueryParam("validTillNextRelease") @NotNull final Boolean validTillNextRelease,
      @QueryParam("validForDays") @NotNull final int validForDays) {
    return new RestResponse<>(adminDelegateVersionService.setDelegateImageTag(
        delegateTag, accountIdentifier, validTillNextRelease, validForDays));
  }
}
