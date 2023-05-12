/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;

import static java.util.Collections.emptyList;

import io.harness.NGCommonEntityConstants;
import io.harness.delegate.beans.SupportedDelegateVersion;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth2;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.service.intfc.DelegateRingService;

import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AccountService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Api("version")
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateVersionInfoResource {
  private final AccountService accountService;
  private final DelegateRingService delegateRingService;

  @GET
  @Path("/delegate/{ring}")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<List<String>> getDelegateVersionFromRing(@PathParam("ring") String ringName) {
    final List<String> ringVersion = delegateRingService.getDelegateVersionsForRing(ringName, false);
    if (!CollectionUtils.isEmpty(ringVersion)) {
      return new RestResponse<>(ringVersion);
    }

    return new RestResponse<>(emptyList());
  }

  @GET
  @Path("/delegate/rings")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<Map<String, List<String>>> getDelegateVersionFromRing() {
    return new RestResponse<>(delegateRingService.getDelegateVersionsForAllRings(false));
  }

  @GET
  @Path("/delegate")
  @Timed
  @ExceptionMetered
  @DelegateAuth2
  public RestResponse<List<String>> getDelegateVersion(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.getDelegateConfiguration(accountId).getDelegateVersions());
  }

  @GET
  @Path("/supportedDelegate")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @InternalApi
  public RestResponse<SupportedDelegateVersion> getSupportedDelegateVersion(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountId) {
    String latestSupportedDelegateImage = delegateRingService.getDelegateImageTag(accountId);
    String[] split = latestSupportedDelegateImage.split(":");
    String latestVersion = split[1];
    SupportedDelegateVersion supportedDelegateVersion =
        SupportedDelegateVersion.builder()
            .latestSupportedVersion(latestVersion)
            .latestSupportedMinimalVersion(latestVersion.concat(".minimal"))
            .build();
    return new RestResponse<>(supportedDelegateVersion);
  }

  @GET
  @Path("/watcher/{ring}")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<String> getWatcherVersionFromRing(@PathParam("ring") String ringName) {
    final String ringVersion = delegateRingService.getWatcherVersionsForRing(ringName, false);
    return new RestResponse<>(ringVersion);
  }

  @GET
  @Path("/watcher/rings")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<Map<String, String>> getWatcherVersionFromRing() {
    return new RestResponse<>(delegateRingService.getWatcherVersionsAllRings(false));
  }

  @GET
  @Path("/watcher")
  @Timed
  @ExceptionMetered
  @DelegateAuth2
  public RestResponse<String> getWatcherVersion(@QueryParam("accountId") @NotEmpty String accountId) {
    final String watcherVersion = accountService.getWatcherVersion(accountId);
    if (isNotEmpty(watcherVersion)) {
      return new RestResponse<>(watcherVersion);
    }
    throw new InvalidRequestException("Unable to get watcher version from ring");
  }
}
