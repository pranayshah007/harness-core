/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.license.usage.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.reporting.IDPTelemetryPublisher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPTelemetryRecordsJob {
  public static final int METRICS_RECORD_PERIOD_SECONDS = 86400;

  @Inject @Named("idpTelemetryPublisherExecutor") protected ScheduledExecutorService executorService;
  @Inject IDPTelemetryPublisher publisher;

  public void scheduleTasks() {
    long initialDelay = 300;

    try {
      log.info("IDPTelemetryRecordsJob scheduler starting");
      executorService.scheduleAtFixedRate(
          () -> publisher.recordTelemetry(), initialDelay, METRICS_RECORD_PERIOD_SECONDS, TimeUnit.SECONDS);
      log.info("IDPTelemetryRecordsJob scheduler started");
    } catch (Exception e) {
      log.error("Exception while creating IDPTelemetryRecordsJob. Error = {}", e.getMessage(), e);
      throw e;
    }
  }
}
