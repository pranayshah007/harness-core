/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;

@Path("/version-override")
@Api("/version-override")
@Slf4j
@Produces("application/json")
@NextGenManagerAuth
public class VersionOverrideResource {
  private final DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient;

  @Inject
  public VersionOverrideResource(DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient) {
    this.delegateNgManagerCgManagerClient = delegateNgManagerCgManagerClient;
  }

  @PUT
  @Path("/delegate-tag")
  @ApiOperation(value = "Overrides delegate image tag for account", nickname = "overrideDelegateImageTag")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "overrideDelegateImageTag", summary = "Overrides delegate image tag for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Image Tag")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public RestResponse<String>
  setDelegateTagOverride(@NotEmpty @QueryParam("accountIdentifier") final String accountId,
      @NotEmpty @QueryParam("delegateTag") final String delegateTag,
      @QueryParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Range(max = 90) @QueryParam("validForDays") @DefaultValue("30") final int validForDays) {
    String delegateImage = CGRestUtils.getResponse(delegateNgManagerCgManagerClient.overrideDelegateImage(
        accountId, delegateTag, validTillNextRelease, validForDays));
    return new RestResponse<>(String.format("Updated Delegate image tag to %s", delegateImage));
  }
}
