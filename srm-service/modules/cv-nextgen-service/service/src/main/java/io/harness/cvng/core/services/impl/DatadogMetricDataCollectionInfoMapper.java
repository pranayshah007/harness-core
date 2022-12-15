/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.MetricInfo;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.stream.Collectors;

public class DatadogMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<DatadogMetricsDataCollectionInfo, DatadogMetricCVConfig> {
  @Override
  protected DatadogMetricsDataCollectionInfo toDataCollectionInfo(DatadogMetricCVConfig baseCVConfig) {
    List<MetricCollectionInfo> metricDefinitions = baseCVConfig.getMetricInfos()
                                                       .stream()
                                                       .map(metricInfo -> getMetricCollectionInfo(metricInfo))
                                                       .collect(Collectors.toList());
    return getDataCollectionInfo(metricDefinitions, baseCVConfig);
  }

  private MetricCollectionInfo getMetricCollectionInfo(MetricInfo metricInfo) {
    return DatadogMetricsDataCollectionInfo.MetricCollectionInfo.builder()
        .metricName(metricInfo.getMetricName())
        .metricIdentifier(metricInfo.getIdentifier())
        .metric(metricInfo.getMetric())
        .query(metricInfo.getQuery())
        .groupingQuery(metricInfo.getGroupingQuery())
        .serviceInstanceIdentifierTag(metricInfo.getServiceInstanceIdentifierTag())
        .build();
  }

  private DatadogMetricsDataCollectionInfo getDataCollectionInfo(
      List<MetricCollectionInfo> metricDefinitions, DatadogMetricCVConfig cvConfig) {
    DatadogMetricsDataCollectionInfo dataCollectionInfo = DatadogMetricsDataCollectionInfo.builder()
                                                              .metricDefinitions(metricDefinitions)
                                                              .groupName(cvConfig.getDashboardName())
                                                              .build();
    dataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return dataCollectionInfo;
  }
}
