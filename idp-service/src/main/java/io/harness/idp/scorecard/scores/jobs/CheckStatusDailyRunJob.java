/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.jobs;

import static io.harness.idp.common.DateUtils.ZONE_ID_IST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.service.CheckService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CheckStatusDailyRunJob implements Managed {
  private ScheduledExecutorService executorService;
  private final CheckService checkService;

  @Inject
  public CheckStatusDailyRunJob(
      @Named("checkStatusDailyRunJob") ScheduledExecutorService executorService, CheckService checkService) {
    this.executorService = executorService;
    this.checkService = checkService;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("check-status-daily-run-job").build());
    long midnight = LocalDateTime.now(ZoneId.of(ZONE_ID_IST))
                        .until(LocalDate.now(ZoneId.of(ZONE_ID_IST)).plusDays(1).atStartOfDay(), ChronoUnit.MINUTES);
    log.info("Scheduling CheckStatusDailyRunJob with initial delay of {} minutes from current time", midnight);
    executorService.scheduleAtFixedRate(this::run, midnight + 10, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
      log.error("CheckStatusDailyRunJob executorService terminated after the timeout of 30 seconds");
    }
  }

  public void run() {
    log.info("Check status daily run job started");
    try {
      checkService.computeCheckStatus();
    } catch (Exception ex) {
      log.error("Error in CheckStatusDailyRunJob. Error = {}", ex.getMessage(), ex);
      throw ex;
    }
    log.info("Check status daily run job completed");
  }
}
