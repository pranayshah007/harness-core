/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static java.util.Collections.emptyList;

import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;
import io.harness.service.intfc.DelegateRingService;

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
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;

/*
Pending: Move apis using api-key auth
 */
@Api("dms/version")
@Path("/dms/version")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateServiceVersionInfoResource {
  private final DelegateRingService delegateRingService;

  @GET
  @Path("/delegate/{ring}")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<List<String>> getDelegateVersionsFromAllRings(@PathParam("ring") String ringName) {
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
  public RestResponse<Map<String, List<String>>> getDelegateVersionsFromAllRings() {
    return new RestResponse<>(delegateRingService.getDelegateVersionsForAllRings(false));
  }

  @GET
  @Path("/watcher/{ring}")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<String> getWatcherVersionsFromAllRings(@PathParam("ring") String ringName) {
    final String ringVersion = delegateRingService.getWatcherVersionsForRing(ringName, false);
    return new RestResponse<>(ringVersion);
  }

  @GET
  @Path("/watcher/rings")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<Map<String, String>> getWatcherVersionsFromAllRings() {
    return new RestResponse<>(delegateRingService.getWatcherVersionsAllRings(false));
  }
}
