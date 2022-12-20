/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.SumologicMetricDataCollectionInfo;
import io.harness.cvng.core.beans.healthsource.QueryParams;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SumologicMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<SumologicMetricDataCollectionInfo, NextGenMetricCVConfig> {
  @Override
  protected SumologicMetricDataCollectionInfo toDataCollectionInfo(NextGenMetricCVConfig cvConfig) {
    List<SumologicMetricDataCollectionInfo.MetricCollectionInfo> dataCollectionMetricInfos =
        cvConfig.getMetricInfos()
            .stream()
            .map(SumologicMetricDataCollectionInfoMapper::getMetricCollectionInfo)
            .collect(Collectors.toList());
    SumologicMetricDataCollectionInfo sumologicMetricDataCollectionInfo =
        SumologicMetricDataCollectionInfo.builder()
            .metricDefinitions(dataCollectionMetricInfos)
            .groupName(cvConfig.getGroupName())
            .build();
    sumologicMetricDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return sumologicMetricDataCollectionInfo;
  }

  private static SumologicMetricDataCollectionInfo.MetricCollectionInfo getMetricCollectionInfo(
      NextGenMetricInfo nextGenMetricInfo) {
    String serviceIdentifierTag =
        Optional.ofNullable(nextGenMetricInfo.getQueryParams()).map(QueryParams::getServiceInstanceField).orElse(null);
    return SumologicMetricDataCollectionInfo.MetricCollectionInfo.builder()
        .metricIdentifier(nextGenMetricInfo.getIdentifier())
        .metricName(nextGenMetricInfo.getMetricName())
        .query(nextGenMetricInfo.getQuery())
        .serviceInstanceIdentifierTag(serviceIdentifierTag)
        .build();
  }
}