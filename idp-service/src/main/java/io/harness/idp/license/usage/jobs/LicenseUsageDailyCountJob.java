/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class LicenseUsageDailyCountJob implements Managed {
  private ScheduledExecutorService executorService;
  private final IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Inject
  public LicenseUsageDailyCountJob(@Named("licenseUsageDailyCountJob") ScheduledExecutorService executorService,
      IDPModuleLicenseUsage idpModuleLicenseUsage) {
    this.executorService = executorService;
    this.idpModuleLicenseUsage = idpModuleLicenseUsage;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("license-usage-daily-count-job").build());
    long midnight = LocalDateTime.now().until(LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.MINUTES);
    executorService.scheduleAtFixedRate(this::run, midnight, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
      log.error("LicenseUsageDailyCountJob executorService terminated after the timeout of 30 seconds");
    }
  }

  public void run() {
    log.info("License usage daily count job started");
    try {
      idpModuleLicenseUsage.licenseUsageDailyCountAggregationPerAccount();
    } catch (Exception ex) {
      log.error("Error in LicenseUsageDailyCountJob. Error = {}", ex.getMessage(), ex);
      throw ex;
    }
    log.info("License usage daily count job completed");
  }
}
