/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import static io.harness.batch.processing.ApplicationReadyListener.createLivenessMarker;

import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.timescaledb.metrics.QueryStatsPrinter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
public class StartUpJobScheduler {
  private static final long oneHour = 60L * 60 * 1000;

  @Autowired private QueryStatsPrinter queryStatsPrinter;

  @Autowired private MetricsPublisher metricsPublisher;
  @Autowired private MetricService metricService;

  /**
   * Created this job because while running functional test
   * ApplicationReadyListener#createLivenessMarkerOnReadyEvent() is not executed.
   */
  @Scheduled(fixedDelay = Long.MAX_VALUE)
  public void onAppStart() {
    log.info("Inside onAppStart");
    log.info("Initializing metrics");
    metricService.initializeMetrics();
    try {
      createLivenessMarker();
    } catch (Exception ex) {
      log.error("StartUpJobs: Failed to create liveness marker");
    }
  }

  @Scheduled(fixedRate = oneHour * 24, initialDelay = oneHour)
  public void printTimescaleDBMetrics() {
    StringBuilder allQueries = new StringBuilder("\n");
    queryStatsPrinter.get().forEach(
        (k, v) -> allQueries.append(String.format("Query: [%s], Stats: [%s]", k, v)).append("\n"));

    log.info("PSQL query stats: {}", allQueries);
  }

  @Scheduled(fixedRate = 60L * 1000, initialDelay = 60L * 1000)
  public void recordMetrics() {
    metricsPublisher.recordMetrics();
  }
}
