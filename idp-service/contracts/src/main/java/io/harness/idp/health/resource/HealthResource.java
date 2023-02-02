/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.health.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Api("health")
@Path("/health")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class HealthResource {
  @GET
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<String> getSecretIdByEnvName() {
    return new RestResponse<>("healthy");
  }
}
