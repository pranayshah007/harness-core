/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.beans.FeatureName;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.MetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;
import io.harness.cvng.utils.DatadogQueryUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class DatadogMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<DatadogMetricsDataCollectionInfo, DatadogMetricCVConfig> {
  @Inject private FeatureFlagService featureFlagService;
  @Override
  protected DatadogMetricsDataCollectionInfo toDataCollectionInfo(DatadogMetricCVConfig baseCVConfig) {
    List<MetricCollectionInfo> metricDefinitions =
        baseCVConfig.getMetricInfos().stream().map(this::getMetricCollectionInfo).collect(Collectors.toList());
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

  @Override
  public void postProcessDataCollectionInfo(DatadogMetricsDataCollectionInfo dataCollectionInfo,
      DatadogMetricCVConfig cvConfig, VerificationTask.TaskType taskType) {
    if (featureFlagService.isFeatureFlagEnabled(
            cvConfig.getAccountId(), FeatureName.SRM_DATADOG_METRICS_FORMULA_SUPPORT.name())) {
      dataCollectionInfo.setDataCollectionDsl(DataCollectionDSLFactory.readMetricDSL(DataSourceType.DATADOG_METRICS));
      for (MetricCollectionInfo metricCollectionInfo : dataCollectionInfo.getMetricDefinitions()) {
        // composite query the base query is considered and not the grouping query formed by the UI
        Pair<String, List<String>> formulaQueriesPair =
            DatadogQueryUtils.processCompositeQuery(metricCollectionInfo.getQuery(),
                metricCollectionInfo.getServiceInstanceIdentifierTag(), dataCollectionInfo.isCollectHostData());
        metricCollectionInfo.setFormula(formulaQueriesPair.getLeft());
        metricCollectionInfo.setFormulaQueries(formulaQueriesPair.getRight());
      }
    }
  }
}
