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
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineServiceDwMetricsPublisher implements MetricsPublisher {
  private final MetricRegistry metricRegistry;
  private final MetricService metricService;
  private static final Double SNAPSHOT_FACTOR = 1.0D / (double) TimeUnit.SECONDS.toNanos(1L);
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");
  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String CONTAINER_NAME = System.getenv("CONTAINER_NAME");
  private static final String SERVICE_NAME = "pipeline-service";
  public static final String MUTABLE_SERVLET_CONTEXT_HANDLER_PATH = "io.dropwizard.jetty.MutableServletContextHandler";
  public static final String MUTABLE_SERVLET_CONTEXT_HANDLER = "MutableServletContextHandler";

  @Override
  public void recordMetrics() {
    Set<Map.Entry<String, Meter>> meterSet = metricRegistry.getMeters().entrySet();
    meterSet.forEach(entry -> recordMeter(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Gauge>> gaugeSet = metricRegistry.getGauges().entrySet();
    gaugeSet.forEach(entry -> recordGauge(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Timer>> timerSet = metricRegistry.getTimers().entrySet();
    timerSet.forEach(entry -> recordTimer(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Counter>> counterSet = metricRegistry.getCounters().entrySet();
    counterSet.forEach(entry -> recordCounter(sanitizeMetricName(entry.getKey()), entry.getValue()));
  }

  private void recordMeter(String metricName, Meter meter) {
    if (metricName.contains(MUTABLE_SERVLET_CONTEXT_HANDLER)) {
      recordMeterForMutableServletContextHandler(metricName, meter);
    } else if (checkIfResourceMetrics(metricName, "responses")) {
      recordMeterForResponsesOfResourceMethods(metricName, meter);
    }
  }

  private void recordMeterForMutableServletContextHandler(String metricName, Meter meter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_count", meter.getCount());
    }
  }
  private void recordMeterForResponsesOfResourceMethods(String metricName, Meter meter) {
    String[] s = metricName.split("_");
    String methodName = "";
    String resourceName = "";
    String statusCode = "";
    if (s.length >= 4) {
      statusCode = s[s.length - 2];
      methodName = s[s.length - 3];
      resourceName = s[s.length - 4];
    }
    try (DwMetricContext ignore =
             new DwMetricContext(methodName, resourceName, statusCode, NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      String modifiedMetricName = "io_harness_pipeline_resources_responses";
      recordMetric(modifiedMetricName + "_count", meter.getCount());
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
    // Getting timers for only total metric as total metrics contains complete time for request, response and method
    // time spent
    if (checkIfResourceMetrics(metricName, "total")) {
      addTimerMetricsForResources(metricName, timer);
      return;
    } else if (checkIfAggregateHttpMethodMetric(metricName)) {
      addTimerMetricsForAggregateHttpMethods(metricName, timer);
      return;
    }
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_count", timer.getCount());
      recordSnapshot(metricName + "_snapshot", timer.getSnapshot());
    }
  }

  private void recordSnapshot(String metricName, Snapshot snapshot) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_95thPercentile", snapshot.get95thPercentile() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_99thPercentile", snapshot.get99thPercentile() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_999thPercentile", snapshot.get999thPercentile() * SNAPSHOT_FACTOR);
    }
  }

  private void addTimerMetricsForAggregateHttpMethods(String metricName, Timer timer) {
    String[] s = metricName.split("_");
    String methodName = "";
    if (s.length >= 3) {
      methodName = s[s.length - 2];
      Set<String> methods = Sets.newHashSet("put", "get", "post", "delete");
      if (!methods.contains(methodName)) {
        return;
      }
    }

    try (DwMetricContext ignore = new DwMetricContext(methodName, NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      String modifiedMetricName = sanitizeMetricName(MUTABLE_SERVLET_CONTEXT_HANDLER_PATH + "_http_method_requests");
      recordMetric(modifiedMetricName + "_count", timer.getCount());
      recordSnapshot(modifiedMetricName + "_snapshot", timer.getSnapshot());
    }
  }

  private boolean checkIfAggregateHttpMethodMetric(String metricName) {
    List<String> methodList = new ArrayList<>(List.of("put", "get", "post", "delete"));
    for (String method : methodList) {
      if (metricName.contains("_" + method + "_")) {
        return true;
      }
    }
    return false;
  }

  private void addTimerMetricsForResources(String metricName, Timer timer) {
    String[] s = metricName.split("_");
    String methodName = "";
    String resourceName = "";
    if (s.length >= 3) {
      methodName = s[s.length - 2];
      resourceName = s[s.length - 3];
    }
    try (DwMetricContext ignore =
             new DwMetricContext(methodName, resourceName, NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      String modifiedMetricName = "io_harness_pipeline_resources_total";
      recordMetric(modifiedMetricName + "_count", timer.getCount());
      recordSnapshot(modifiedMetricName + "_snapshot", timer.getSnapshot());
    }
  }

  private boolean checkIfResourceMetrics(String metricName, String additionalMetricNameCheck) {
    List<String> resourcesList =
        List.of("PipelineResource", "InputSetResourcePMS", "PlanExecutionResource", "ExecutionDetailsResource");
    for (String resourceName : resourcesList) {
      // Logging only total metrics as we want to find total time spent for each api
      if (metricName.contains(resourceName) && metricName.contains(additionalMetricNameCheck)) {
        return true;
      }
    }
    return false;
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
