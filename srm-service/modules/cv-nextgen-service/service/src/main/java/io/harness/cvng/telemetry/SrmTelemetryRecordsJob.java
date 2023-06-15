/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.telemetry;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SrmTelemetryRecordsJob {
  public static final int THIRTY_MINS_IN_SECS = 1800;
  @Inject @Named("SrmTelemetryPublisherExecutor") private ScheduledExecutorService executorService;
  @Inject private SrmTelemetryPublisher publisher;

  public void scheduleTasks() {
    long initialDelay = 180;

    try {
      log.info("SrmTelemetryRecordsJob scheduler starting");
      executorService.scheduleAtFixedRate(
          () -> publisher.recordTelemetry(), initialDelay, THIRTY_MINS_IN_SECS, TimeUnit.SECONDS);
      log.info("SrmTelemetryRecordsJob scheduler started");
    } catch (Exception e) {
      log.error("Exception while creating the scheduled job to track Srm license services", e);
    }
  }
}
