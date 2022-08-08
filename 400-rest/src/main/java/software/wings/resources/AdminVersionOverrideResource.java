/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import io.harness.datahandler.services.AdminDelegateVersionService;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AdminPortalAuth;

import com.google.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

@Path("/admin/version-override")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@AdminPortalAuth
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AdminVersionOverrideResource {
  private final AdminDelegateVersionService adminDelegateVersionService;

  @PUT
  @Path("/{accountId}/delegate-tag")
  public RestResponse<Void> setDelegateTagOverride(@NotEmpty @PathParam("accountId") final String accountId,
      @NotEmpty @FormParam("delegateTag") final String delegateTag,
      @FormParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Range(max = 90) @FormParam("validForDays") @DefaultValue("30") final int validForDays) {
    log.info("Setting delegate image tag override for account {} to {}", accountId, delegateTag);
    adminDelegateVersionService.setDelegateImageTag(delegateTag, accountId, validTillNextRelease, validForDays);
    return new RestResponse<>();
  }

  @PUT
  @Path("/{accountId}/upgrader-tag")
  public RestResponse<Void> setUpgraderTagOverride(@NotEmpty @PathParam("accountId") final String accountId,
      @NotEmpty @FormParam("upgraderTag") final String upgraderTag,
      @FormParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Range(max = 90) @FormParam("validForDays") @DefaultValue("30") final int validForDays) {
    log.info("Setting upgrader image tag override for account {} to {}", accountId, upgraderTag);
    adminDelegateVersionService.setUpgraderImageTag(upgraderTag, accountId, validTillNextRelease, validForDays);
    return new RestResponse<>();
  }

  @PUT
  @Path("/{accountId}/delegate-version")
  public RestResponse<Void> setDelegateVersionOverride(@NotEmpty @PathParam("accountId") final String accountId,
      @NotEmpty @FormParam("delegateVersion") final String delegateVersion,
      @FormParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Range(max = 90) @FormParam("validForDays") @DefaultValue("30") final int validForDays) {
    log.info("Setting delegate.jar version override for account {} to {}", accountId, delegateVersion);
    adminDelegateVersionService.setDelegateVersion(delegateVersion, accountId, validTillNextRelease, validForDays);
    return new RestResponse<>();
  }

  @PUT
  @Path("/{accountId}/watcher-version")
  public RestResponse<Void> setWatcherVersionOverride(@NotEmpty @PathParam("accountId") final String accountId,
      @NotEmpty @FormParam("watcherVersion") final String watcherVersion,
      @FormParam("validTillNextRelease") @DefaultValue("false") final Boolean validTillNextRelease,
      @Range(max = 90) @FormParam("validForDays") @DefaultValue("30") final int validForDays) {
    log.info("Setting watcher.jar version override for account {} to {}", accountId, watcherVersion);
    adminDelegateVersionService.setWatcherVersion(watcherVersion, accountId, validTillNextRelease, validForDays);
    return new RestResponse<>();
  }
}
