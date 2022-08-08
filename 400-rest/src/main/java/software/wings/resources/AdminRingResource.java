/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.datahandler.services.AdminRingService;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(DEL)
@Path("/admin/rings")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalApi
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminRingResource {
  private final AdminRingService adminRingService;

  @PUT
  @Path("/{ringName}/delegate-tag")
  public RestResponse<Boolean> updateDelegateTag(
      @PathParam("ringName") final String ringName, @Body final String delegateTag) {
    if (isBlank(delegateTag) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty delegate tag or ring name");
    }
    log.info("Updating delegate image tag for ring {} to {}", ringName, delegateTag);
    return new RestResponse<>(adminRingService.updateDelegateImageTag(delegateTag, ringName));
  }

  @PUT
  @Path("/{ringName}/upgrader-tag")
  public RestResponse<Boolean> updateUpgraderTag(
      @PathParam("ringName") final String ringName, @Body final String upgraderTag) {
    if (isBlank(upgraderTag) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty upgrader tag or ring name");
    }
    log.info("Updating upgrader image tag for ring {} to {}", ringName, upgraderTag);
    return new RestResponse<>(adminRingService.updateUpgraderImageTag(upgraderTag, ringName));
  }

  @PUT
  @Path("/{ringName}/delegate-version")
  public RestResponse<Boolean> updateDelegateVersion(
      @PathParam("ringName") final String ringName, @Body final List<String> delegateVersion) {
    if (isEmpty(delegateVersion) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty delegate version or ring name");
    }
    log.info("Updating delegate.jar version for ring {} to {}", ringName, delegateVersion);
    return new RestResponse<>(adminRingService.updateDelegateVersion(delegateVersion, ringName));
  }

  @PUT
  @Path("/{ringName}/watcher-version")
  public RestResponse<Boolean> updateWatcherVersion(
      @PathParam("ringName") final String ringName, @Body final List<String> watcherVersion) {
    if (isEmpty(watcherVersion) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty delegate version or ring name");
    }
    log.info("Updating watcher.jar version for ring {} to {}", ringName, watcherVersion);
    return new RestResponse<>(adminRingService.updateWatcherVersion(watcherVersion, ringName));
  }
}
