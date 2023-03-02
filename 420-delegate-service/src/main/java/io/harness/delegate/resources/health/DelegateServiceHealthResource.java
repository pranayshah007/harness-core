/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources.health;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("health")
@Path("/health")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OwnedBy(DEL)
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@PublicApi
public class DelegateServiceHealthResource {
  private final HealthService healthService;

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get health for delegate service", nickname = "getDelegateServiceHealthStatus")
  public ResponseDTO<String> get() throws Exception {
    final HealthCheck.Result check = healthService.check();
    if (check.isHealthy()) {
      return ResponseDTO.newResponse(HealthService.HEALTHY);
    }
    throw new HealthException(check.getMessage(), check.getError());
  }
}
