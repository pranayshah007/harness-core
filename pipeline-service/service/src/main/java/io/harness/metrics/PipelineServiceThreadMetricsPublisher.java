/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineServiceThreadMetricsPublisher implements MetricsPublisher {
  private final HarnessMetricRegistry metricRegistry;
  private final MetricService metricService;
  private static final Double SNAPSHOT_FACTOR = 1.0D / (double) TimeUnit.SECONDS.toNanos(1L);
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");
  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String CONTAINER_NAME = System.getenv("CONTAINER_NAME");
  private static final String SERVICE_NAME = "pipeline-service";

  private static final Set<String> metricFilters = Set.of("Pipeline", "EngineExecutor");

  @Override
  public void recordMetrics() {
    Set<Map.Entry<String, Meter>> meterSet = metricRegistry.getThreadPoolMetricRegistry().getMeters().entrySet();
    meterSet.stream()
        .filter(e -> metricFilters.stream().anyMatch(e.getKey()::startsWith))
        .forEach(entry -> recordMeter(sanitizeMetricName(entry.getKey()), entry.getValue()));

    Set<Map.Entry<String, Gauge>> gaugeSet = metricRegistry.getThreadPoolMetricRegistry().getGauges().entrySet();
    gaugeSet.stream()
        .filter(e -> metricFilters.stream().anyMatch(e.getKey()::startsWith))
        .forEach(entry -> recordGauge(sanitizeMetricName(entry.getKey()), entry.getValue()));

    Set<Map.Entry<String, Timer>> timerSet = metricRegistry.getThreadPoolMetricRegistry().getTimers().entrySet();
    timerSet.stream()
        .filter(e -> metricFilters.stream().anyMatch(e.getKey()::startsWith))
        .forEach(entry -> recordTimer(sanitizeMetricName(entry.getKey()), entry.getValue()));

    Set<Map.Entry<String, Counter>> counterSet = metricRegistry.getThreadPoolMetricRegistry().getCounters().entrySet();
    counterSet.stream()
        .filter(e -> metricFilters.stream().anyMatch(e.getKey()::startsWith))
        .forEach(entry -> recordCounter(sanitizeMetricName(entry.getKey()), entry.getValue()));
  }

  private void recordMeter(String metricName, Meter meter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_fifteenMinuteRate", meter.getFifteenMinuteRate());
      recordMetric(metricName + "_meanRate", meter.getMeanRate());
    }
  }

  private void recordCounter(String metricName, Counter counter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName, counter.getCount());
    }
  }

  private void recordGauge(String metricName, Gauge gauge) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      Object obj = gauge.getValue();
      double value;
      if (obj instanceof Number) {
        value = ((Number) obj).doubleValue();
      } else {
        if (!(obj instanceof Boolean)) {
          log.debug(String.format(
              "Invalid type for Gauge %s: %s", metricName, obj == null ? "null" : obj.getClass().getName()));
          return;
        }
        value = (Boolean) obj ? 1.0D : 0.0D;
      }
      recordMetric(metricName, value);
    }
  }

  private void recordTimer(String metricName, Timer timer) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordSnapshot(metricName, timer.getSnapshot());
    }
  }

  private void recordSnapshot(String metricName, Snapshot snapshot) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_mean", snapshot.getMean() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_99thPercentile", snapshot.get99thPercentile() * SNAPSHOT_FACTOR);
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
