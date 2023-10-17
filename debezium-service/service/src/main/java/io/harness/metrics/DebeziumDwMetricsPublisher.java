/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.DEBEZIUM})
@OwnedBy(HarnessTeam.PIPELINE)
public class DebeziumDwMetricsPublisher implements MetricsPublisher {
  @Inject private MetricService metricService;
  @Inject private MetricRegistry metricRegistry;
  private static final Double SNAPSHOT_FACTOR = 1.0D / (double) TimeUnit.SECONDS.toNanos(1L);
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");
  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String CONTAINER_NAME = System.getenv("CONTAINER_NAME");
  private static final String SERVICE_NAME = "debezium-service";

  private static final MetricFilter meterMetricFilter =
      MetricFilter.startsWith("io.dropwizard.jetty.MutableServletContextHandler");
  @Override
  public void recordMetrics() {
    Set<Map.Entry<String, Meter>> meterSet = metricRegistry.getMeters(meterMetricFilter).entrySet();
    meterSet.forEach(entry -> recordMeter(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Timer>> timerSet = metricRegistry.getTimers().entrySet();
    timerSet.forEach(entry -> recordTimer(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Counter>> counterSet = metricRegistry.getCounters().entrySet();
    counterSet.forEach(entry -> recordCounter(sanitizeMetricName(entry.getKey()), entry.getValue()));
  }

  private void recordCounter(String metricName, Counter counter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName, counter.getCount());
    }
  }

  private void recordMeter(String metricName, Meter meter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_count", meter.getCount());
      recordMetric(metricName + "_fifteenMinuteRate", meter.getFifteenMinuteRate());
      recordMetric(metricName + "_fiveMinuteRate", meter.getFiveMinuteRate());
      recordMetric(metricName + "_oneMinuteRate", meter.getOneMinuteRate());
      recordMetric(metricName + "_meanRate", meter.getMeanRate());
    }
  }

  private void recordTimer(String metricName, Timer timer) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_count", timer.getCount());
      recordMetric(metricName + "_fifteenMinuteRate", timer.getFifteenMinuteRate());
      recordMetric(metricName + "_fiveMinuteRate", timer.getFiveMinuteRate());
      recordMetric(metricName + "_oneMinuteRate", timer.getOneMinuteRate());
      recordMetric(metricName + "_meanRate", timer.getMeanRate());
      recordSnapshot(metricName + "_snapshot", timer.getSnapshot());
    }
  }

  private void recordSnapshot(String metricName, Snapshot snapshot) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_mean", snapshot.getMean() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_95thPercentile", snapshot.get95thPercentile() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_99thPercentile", snapshot.get99thPercentile() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_999thPercentile", snapshot.get999thPercentile() * SNAPSHOT_FACTOR);
    }
  }

  private void recordMetric(String name, double value) {
    metricService.recordMetric(name, value);
  }

  private static String sanitizeMetricName(String dropwizardName) {
    String name = METRIC_NAME_RE.matcher(dropwizardName).replaceAll("_");
    if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }
}
