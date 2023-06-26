/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.NoResultFoundException;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Api("health")
@Path("/health")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OwnedBy(PL)
@ExposeInternalException
@Slf4j
@PublicApi
public class NGDashboardAggregatorHealthResource {
  private final HealthService healthService;
  private final ThreadDeadlockHealthCheck threadDeadlockHealthCheck;

  @Inject
  public NGDashboardAggregatorHealthResource(HealthService healthService) {
    this.healthService = healthService;
    this.threadDeadlockHealthCheck = new ThreadDeadlockHealthCheck();
  }

  @GET
  @Timed
  @ExceptionMetered
  @Operation(hidden = true)
  public ResponseDTO<String> get() throws Exception {
    if (getMaintenanceFlag()) {
      log.info("In maintenance mode. Throwing exception to prevent traffic.");
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message("in maintenance mode")
          .reportTargets(USER)
          .build();
    }

    final HealthCheck.Result check = healthService.check();
    if (check.isHealthy()) {
      return ResponseDTO.newResponse("healthy");
    }
    throw new HealthException(check.getMessage(), check.getError());
  }

  @GET
  @Path("liveness")
  @Timed
  @ExceptionMetered
  @Operation(hidden = true)
  public RestResponse<String> doLivenessCheck() {
    HealthCheck.Result check = threadDeadlockHealthCheck.execute();
    if (check.isHealthy()) {
      return new RestResponse<>("live");
    }
    log.info(check.getMessage());
    throw new HealthException(check.getMessage(), check.getError());
  }
}
