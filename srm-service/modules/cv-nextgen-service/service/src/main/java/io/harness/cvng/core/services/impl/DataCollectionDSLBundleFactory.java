/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.AZURE_LOGS_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.DATADOG_METRIC_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.ELK_LOG_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.GRAFANA_LOKI_LOG_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.PROMETHEUS_METRIC_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.SIGNALFX_METRIC_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.SUMOLOGIC_LOG_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.ActualDataCollectionPath.SUMOLOGIC_METRIC_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.AZURE_LOGS_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.AZURE_METRICS_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.DATADOG_SAMPLE_V2_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.ELK_LOG_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.GRAFANA_LOKI_LOG_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.PROMETHEUS_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.SIGNALFX_METRIC_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.SUMOLOGIC_LOG_SAMPLE_PATH;
import static io.harness.cvng.core.services.impl.DataCollectionDSLBundleFactory.SampleDataCollectionPath.SUMOLOGIC_METRIC_SAMPLE_PATH;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class DataCollectionDSLBundleFactory {
  static class ActualDataCollectionPath {
    public static final String SUMOLOGIC_LOG_PATH = "sumologic-log.datacollection";
    public static final String ELK_LOG_PATH = "elk-log-fetch-data.datacollection";
    public static final String GRAFANA_LOKI_LOG_PATH = "grafana-loki-log-fetch-data.datacollection";
    public static final String PROMETHEUS_METRIC_PATH = "prometheus-v2-dsl-metric.datacollection";
    public static final String DATADOG_METRIC_PATH = "datadog-v2-dsl-metric.datacollection";
    public static final String AZURE_LOGS_PATH = "azure-logs-fetch-data.datacollection";
    public static final String SUMOLOGIC_METRIC_PATH = "/sumologic/dsl/metric-collection.datacollection";
    public static final String SIGNALFX_METRIC_PATH = "/signalfx/dsl/metric-collection.datacollection";
  }

  public static class SampleDataCollectionPath {
    public static final String DATADOG_SAMPLE_V2_PATH = "/datadog/dsl/datadog-time-series-points-v2.datacollection";
    public static final String GRAFANA_LOKI_LOG_SAMPLE_PATH =
        "/grafanaloki/dsl/grafana-loki-log-sample-data.datacollection";
    public static final String ELK_LOG_SAMPLE_PATH = "/elk/dsl/elk-sample-data.datacollection";
    public static final String PROMETHEUS_SAMPLE_PATH = "/prometheus/dsl/prometheus-sample-data.datacollection";
    public static final String SIGNALFX_METRIC_SAMPLE_PATH = "/signalfx/dsl/signalfx-metric-sample-data.datacollection";
    public static final String SUMOLOGIC_METRIC_SAMPLE_PATH =
        "/sumologic/dsl/sumologic-metric-sample-data.datacollection";
    public static final String SUMOLOGIC_LOG_SAMPLE_PATH = "/sumologic/dsl/sumologic-log-sample-data.datacollection";
    public static final String AZURE_LOGS_SAMPLE_PATH = "/azure/dsl/azure-logs-sample-data.datacollection";
    public static final String AZURE_METRICS_SAMPLE_PATH = "/azure/dsl/azure-metrics-sample-data.datacollection";
  }
  private static final String AZURE_SERVICE_INSTANCE_FIELD_PATH =
      "/azure/dsl/azure-service-instance-field-data.datacollection";

  private static final Map<DataSourceType, DataCollectionDSLBundle> dataSourceTypeToDslScriptMap = new HashMap<>();
  static {
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.SUMOLOGIC_LOG, createDataCollectionDSL(SUMOLOGIC_LOG_PATH, SUMOLOGIC_LOG_SAMPLE_PATH));
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.ELASTICSEARCH, createDataCollectionDSL(ELK_LOG_PATH, ELK_LOG_SAMPLE_PATH));
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.GRAFANA_LOKI_LOGS, createDataCollectionDSL(GRAFANA_LOKI_LOG_PATH, GRAFANA_LOKI_LOG_SAMPLE_PATH));
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.PROMETHEUS, createDataCollectionDSL(PROMETHEUS_METRIC_PATH, PROMETHEUS_SAMPLE_PATH));
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.DATADOG_METRICS, createDataCollectionDSL(DATADOG_METRIC_PATH, DATADOG_SAMPLE_V2_PATH));
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.AZURE_LOGS, createDataCollectionDSL(AZURE_LOGS_PATH, AZURE_LOGS_SAMPLE_PATH));
    dataSourceTypeToDslScriptMap.put(DataSourceType.AZURE_METRICS,
        createDataCollectionDSL(
            AZURE_LOGS_PATH, AZURE_METRICS_SAMPLE_PATH, AZURE_SERVICE_INSTANCE_FIELD_PATH)); // TODO Fix
    dataSourceTypeToDslScriptMap.put(DataSourceType.SPLUNK_SIGNALFX_METRICS,
        createDataCollectionDSL(SIGNALFX_METRIC_PATH, SIGNALFX_METRIC_SAMPLE_PATH));
    dataSourceTypeToDslScriptMap.put(
        DataSourceType.SUMOLOGIC_METRICS, createDataCollectionDSL(SUMOLOGIC_METRIC_PATH, SUMOLOGIC_METRIC_SAMPLE_PATH));
  }

  public static DataCollectionDSLBundle readDSL(DataSourceType dataSourceType) {
    if (dataSourceTypeToDslScriptMap.containsKey(dataSourceType)) {
      return dataSourceTypeToDslScriptMap.get(dataSourceType);
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented for DataSourceType " + dataSourceType.name());
    }
  }

  private static DataCollectionDSLBundle createDataCollectionDSL(
      String actualDataCollectionDSLPath, String sampleDataCollectionDSLPath) {
    return DataCollectionDSLBundle.builder()
        .actualDataCollectionDSL(readFile(actualDataCollectionDSLPath))
        .sampleDataCollectionDSL(readFile(sampleDataCollectionDSLPath))
        .build();
  }

  private static DataCollectionDSLBundle createDataCollectionDSL(
      String actualDataCollectionDSLPath, String sampleDataCollectionDSLPath, String serviceInstanceDSL) {
    return DataCollectionDSLBundle.builder()
        .actualDataCollectionDSL(readFile(actualDataCollectionDSLPath))
        .sampleDataCollectionDSL(readFile(sampleDataCollectionDSLPath))
        .serviceInstanceIdentifierDSL(readFile(serviceInstanceDSL))
        .build();
  }

  private static String readFile(String fileName) {
    try {
      return Resources.toString(
          Objects.requireNonNull(NextGenLogCVConfig.class.getResource(fileName)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Cannot read DSL {}", fileName);
      throw new RuntimeException(e);
    }
  }
}
