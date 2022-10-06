/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.UpgradeCheckResult;
import io.harness.delegate.service.intfc.DelegateUpgraderService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/upgrade-check")
@Path("/upgrade-check")
@Produces("application/json")
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateUpgraderResource {
  private final DelegateUpgraderService delegateUpgraderService;

  @Inject
  public DelegateUpgraderResource(DelegateUpgraderService delegateUpgraderService) {
    this.delegateUpgraderService = delegateUpgraderService;
  }

  @DelegateAuth
  @GET
  @Path("/delegate")
  @Timed
  @ExceptionMetered
  public RestResponse<UpgradeCheckResult> getDelegateImageTag(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("currentDelegateImageTag") @NotEmpty String currentDelegateImageTag) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateUpgraderService.getDelegateImageTag(accountId, currentDelegateImageTag));
    }
  }

  @DelegateAuth
  @GET
  @Path("/delegate-v2")
  @Timed
  @ExceptionMetered
  public RestResponse<UpgradeCheckResult> getDelegateImageTag(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("currentDelegateImageTag") @NotEmpty String currentDelegateImageTag,
      @QueryParam("delegateGroupName") String delegateGroupName) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateUpgraderService.getDelegateImageTag(accountId, currentDelegateImageTag, delegateGroupName));
    }
  }

  @DelegateAuth
  @GET
  @Path("/upgrader")
  @Timed
  @ExceptionMetered
  public RestResponse<UpgradeCheckResult> getUpgraderImageTag(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("currentUpgraderImageTag") @NotEmpty String currentUpgraderImageTag) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateUpgraderService.getUpgraderImageTag(accountId, currentUpgraderImageTag));
    }
  }
}
