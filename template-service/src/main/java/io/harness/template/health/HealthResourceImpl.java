/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.health;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.NoResultFoundException;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.ng.core.dto.ResponseDTO;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
public class HealthResourceImpl implements HealthResource {
  private HealthService healthService;

  @Inject
  public HealthResourceImpl(HealthService healthService) {
    this.healthService = healthService;
  }

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
}
