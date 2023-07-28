/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.AzureMetricsDataCollectionInfo;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AzureMetricsDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<AzureMetricsDataCollectionInfo, NextGenMetricCVConfig> {
  @Override
  protected AzureMetricsDataCollectionInfo toDataCollectionInfo(NextGenMetricCVConfig cvConfig) {
    List<AzureMetricsDataCollectionInfo.MetricCollectionInfo> dataCollectionMetricInfos =
        cvConfig.getMetricInfos()
            .stream()
            .map(AzureMetricsDataCollectionInfoMapper::getMetricCollectionInfo)
            .collect(Collectors.toList());
    AzureMetricsDataCollectionInfo azureMetricsDataCollectionInfo = AzureMetricsDataCollectionInfo.builder()
                                                                        .metricDefinitions(dataCollectionMetricInfos)
                                                                        .groupName(cvConfig.getGroupName())
                                                                        .build();
    azureMetricsDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return azureMetricsDataCollectionInfo;
  }

  private static AzureMetricsDataCollectionInfo.MetricCollectionInfo getMetricCollectionInfo(
      NextGenMetricInfo nextGenMetricInfo) {
    String serviceIdentifierTag =
        Optional.ofNullable(nextGenMetricInfo.getQueryParams()).map(QueryParams::getServiceInstanceField).orElse(null);
    return AzureMetricsDataCollectionInfo.MetricCollectionInfo.builder()
        .metricIdentifier(nextGenMetricInfo.getIdentifier())
        .metricName(nextGenMetricInfo.getMetricName())
        .serviceInstanceIdentifierTag(serviceIdentifierTag)
        .resourceId(nextGenMetricInfo.getQueryParams().getIndex())
        .healthSourceMetricName(nextGenMetricInfo.getQueryParams().getHealthSourceMetricName())
        .healthSourceMetricNamespace(nextGenMetricInfo.getQueryParams().getHealthSourceMetricNamespace())
        .aggregationType(nextGenMetricInfo.getQueryParams().getAggregationType().toString())
        .build();
  }
}
