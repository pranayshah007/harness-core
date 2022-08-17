package io.harness.health;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.NoResultFoundException;

import com.codahale.metrics.health.HealthCheck;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class HealthProbe extends HealthCheck {
  @Override
  public Result check() throws Exception {
    if (getMaintenanceFlag()) {
      log.info("In maintenance mode. Throwing exception to prevent traffic.");
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message("in maintenance mode")
          .reportTargets(USER)
          .build();
    }

    // queue size logic

    return Result.healthy();
  }
}
