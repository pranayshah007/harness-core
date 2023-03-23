/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.resource;

import static java.util.Collections.emptyList;

import io.harness.beans.FeatureName;
import io.harness.dms.client.DelegateSecretManagerClient;
import io.harness.ff.FeatureFlagService;
import io.harness.remote.client.CGRestUtils;
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

  private final FeatureFlagService featureFlagService;

  private final DelegateSecretManagerClient delegateSecretManagerClient;

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
    boolean ffValue = featureFlagService.isEnabled(FeatureName.USE_IMMUTABLE_DELEGATE, "kmpySmUISimoRrJL6NL73w");
    System.out.print("Value of FF is" + ffValue);
    System.out.println("checking value of string"
        + CGRestUtils.getResponse(
            delegateSecretManagerClient.fetchSecretValue("kmpySmUISimoRrJL6NL73w", "ogQyM9_kRguLaVmUERhioA")));
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
