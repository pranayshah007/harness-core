/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataCollectionDSLFactory {
  private static final String SUMOLOGIC_LOG_DATACOLLECTION_FILE = "sumologic-log.datacollection";
  private static final String ELK_LOG_DATACOLLECTION_FILE = "elk-log-fetch-data.datacollection";
  private static final String GRAFANA_LOKI_LOG_DATACOLLECTION_FILE = "grafana-loki-log-fetch-data.datacollection";

  private static final String PROMETHEUS_METRIC_DATACOLLECTION_FILE = "prometheus-v2-dsl-metric.datacollection";
  private static final Map<DataSourceType, String> dataSourceTypeToDslScriptMap = new HashMap<>();
  private static final Map<DataSourceType, String> dataSourceTypeToDslScriptPathMap = new HashMap<>();

  static {
    dataSourceTypeToDslScriptPathMap.put(DataSourceType.SUMOLOGIC_LOG, SUMOLOGIC_LOG_DATACOLLECTION_FILE);
    dataSourceTypeToDslScriptPathMap.put(DataSourceType.ELASTICSEARCH, ELK_LOG_DATACOLLECTION_FILE);
    dataSourceTypeToDslScriptPathMap.put(DataSourceType.GRAFANA_LOKI_LOGS, GRAFANA_LOKI_LOG_DATACOLLECTION_FILE);
    dataSourceTypeToDslScriptPathMap.put(DataSourceType.PROMETHEUS, PROMETHEUS_METRIC_DATACOLLECTION_FILE);
  }

  public static String readLogDSL(DataSourceType dataSourceType) {
    if (dataSourceTypeToDslScriptMap.containsKey(dataSourceType)) {
      return dataSourceTypeToDslScriptMap.get(dataSourceType);
    } else if (dataSourceTypeToDslScriptPathMap.containsKey(dataSourceType)) {
      String dslScript = readFile(dataSourceTypeToDslScriptPathMap.get(dataSourceType));
      dataSourceTypeToDslScriptMap.put(dataSourceType, dslScript);
      return dslScript;
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented for DataSourceType " + dataSourceType.name());
    }
  }

  public static String readMetricDSL(DataSourceType dataSourceType) {
    // TODO check if we need a log and metric specific functions.
    return readLogDSL(dataSourceType);
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
  private DataCollectionDSLFactory() {}
}
