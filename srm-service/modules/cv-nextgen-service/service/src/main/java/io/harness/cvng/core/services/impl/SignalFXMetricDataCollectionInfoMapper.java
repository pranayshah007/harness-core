/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.SignalFXMetricDataCollectionInfo;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SignalFXMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<SignalFXMetricDataCollectionInfo, NextGenMetricCVConfig> {
  @Override
  protected SignalFXMetricDataCollectionInfo toDataCollectionInfo(NextGenMetricCVConfig cvConfig) {
    List<SignalFXMetricDataCollectionInfo.MetricCollectionInfo> dataCollectionMetricInfos =
        cvConfig.getMetricInfos()
            .stream()
            .map(SignalFXMetricDataCollectionInfoMapper::getMetricCollectionInfo)
            .collect(Collectors.toList());
    SignalFXMetricDataCollectionInfo signalFXMetricDataCollectionInfo =
        SignalFXMetricDataCollectionInfo.builder()
            .metricDefinitions(dataCollectionMetricInfos)
            .groupName(cvConfig.getGroupName())
            .build();
    signalFXMetricDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return signalFXMetricDataCollectionInfo;
  }
  private static SignalFXMetricDataCollectionInfo.MetricCollectionInfo getMetricCollectionInfo(
      NextGenMetricInfo nextGenMetricInfo) {
    String serviceIdentifierTag =
        Optional.ofNullable(nextGenMetricInfo.getQueryParams()).map(QueryParams::getServiceInstanceField).orElse(null);
    return SignalFXMetricDataCollectionInfo.MetricCollectionInfo.builder()
        .metricIdentifier(nextGenMetricInfo.getIdentifier())
        .metricName(nextGenMetricInfo.getMetricName())
        .query(nextGenMetricInfo.getQuery())
        .serviceInstanceIdentifierTag(serviceIdentifierTag)
        .build();
  }
}
