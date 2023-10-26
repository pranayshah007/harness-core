/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.jobs;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.reflection.HarnessReflections;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class RecordMetricsJob {
  public static final int METRICS_RECORD_PERIOD_SECONDS = 60;

  @Inject private Injector injector;
  @Inject @Named("metricsPublisherExecutor") protected ScheduledExecutorService executorService;

  public void scheduleMetricsTasks() {
    long initialDelay = new SecureRandom().nextInt(60);
    Set<Class<? extends MetricsPublisher>> classes = HarnessReflections.get().getSubTypesOf(MetricsPublisher.class);

    try {
      classes.forEach(subClass -> {
        try {
          MetricsPublisher publisher = injector.getInstance(subClass);
          executorService.scheduleAtFixedRate(
              () -> publisher.recordMetrics(), initialDelay, METRICS_RECORD_PERIOD_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
          log.error("Exception while creating a scheduled metrics recorder", e);
        }
      });
    } catch (Exception ex) {
      log.error("Exception while instantiating an instance of MetricsPublisher", ex);
    }
  }

  public void scheduleMetricsTasks(int recordPeriodIntervalInSeconds) {
    long initialDelay = new SecureRandom().nextInt(60);
    Set<Class<? extends MetricsPublisher>> classes = HarnessReflections.get().getSubTypesOf(MetricsPublisher.class);

    try {
      classes.forEach(subClass -> {
        try {
          MetricsPublisher publisher = injector.getInstance(subClass);
          executorService.scheduleAtFixedRate(
              () -> publisher.recordMetrics(), initialDelay, recordPeriodIntervalInSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
          log.error("Exception while creating a scheduled metrics recorder", e);
        }
      });
    } catch (Exception ex) {
      log.error("Exception while instantiating an instance of MetricsPublisher", ex);
    }
  }
}
