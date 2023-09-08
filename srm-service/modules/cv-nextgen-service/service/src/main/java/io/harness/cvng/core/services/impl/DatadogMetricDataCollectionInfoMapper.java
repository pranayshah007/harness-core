/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.MetricInfo;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;
import io.harness.cvng.utils.DatadogQueryUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class DatadogMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<DatadogMetricsDataCollectionInfo, MetricCVConfig> {
  @Override
  protected DatadogMetricsDataCollectionInfo toDataCollectionInfo(MetricCVConfig baseCVConfig) {
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfo;
    if (baseCVConfig instanceof DatadogMetricCVConfig) {
      DatadogMetricCVConfig datadogMetricCVConfig = (DatadogMetricCVConfig) baseCVConfig;
      List<MetricCollectionInfo> metricDefinitions = datadogMetricCVConfig.getMetricInfos()
                                                         .stream()
                                                         .map(this::getMetricCollectionInfo)
                                                         .collect(Collectors.toList());
      datadogMetricsDataCollectionInfo = DatadogMetricsDataCollectionInfo.builder()
                                             .metricDefinitions(metricDefinitions)
                                             .groupName(datadogMetricCVConfig.getDashboardName())
                                             .build();
    } else {
      NextGenMetricCVConfig nextGenMetricCVConfig = (NextGenMetricCVConfig) baseCVConfig;
      List<MetricCollectionInfo> dataCollectionMetricInfos =
          nextGenMetricCVConfig.getMetricInfos()
              .stream()
              .map(DatadogMetricDataCollectionInfoMapper::getMetricCollectionInfo)
              .collect(Collectors.toList());
      datadogMetricsDataCollectionInfo = DatadogMetricsDataCollectionInfo.builder()
                                             .metricDefinitions(dataCollectionMetricInfos)
                                             .groupName(nextGenMetricCVConfig.getGroupName())
                                             .build();
    }
    datadogMetricsDataCollectionInfo.setDataCollectionDsl(
        DataCollectionDSLFactory.readMetricDSL(DataSourceType.DATADOG_METRICS));
    for (MetricCollectionInfo metricCollectionInfo : datadogMetricsDataCollectionInfo.getMetricDefinitions()) {
      // composite query the base query is considered and not the grouping query formed by the UI
      String serviceInstanceIdentifierTag = metricCollectionInfo.getServiceInstanceIdentifierTag();
      boolean isCollectHostData = StringUtils.isNotEmpty(serviceInstanceIdentifierTag);
      Pair<String, List<String>> formulaQueriesPair = DatadogQueryUtils.processCompositeQuery(
          metricCollectionInfo.getQuery(), serviceInstanceIdentifierTag, isCollectHostData);
      metricCollectionInfo.setFormula(formulaQueriesPair.getLeft());
      metricCollectionInfo.setFormulaQueries(formulaQueriesPair.getRight());
    }
    return datadogMetricsDataCollectionInfo;
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

  private static MetricCollectionInfo getMetricCollectionInfo(NextGenMetricInfo nextGenMetricInfo) {
    String serviceIdentifierTag =
        Optional.ofNullable(nextGenMetricInfo.getQueryParams()).map(QueryParams::getServiceInstanceField).orElse(null);
    return MetricCollectionInfo.builder()
        .metricIdentifier(nextGenMetricInfo.getIdentifier())
        .metricName(nextGenMetricInfo.getMetricName())
        .query(nextGenMetricInfo.getQuery())
        .serviceInstanceIdentifierTag(serviceIdentifierTag)
        .build();
  }
}
