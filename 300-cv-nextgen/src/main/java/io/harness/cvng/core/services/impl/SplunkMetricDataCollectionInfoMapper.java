/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.SplunkMetricDataCollectionInfo;
import io.harness.cvng.core.entities.SplunkMetricCVConfig;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.stream.Collectors;

public class SplunkMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<SplunkMetricDataCollectionInfo, SplunkMetricCVConfig> {
  @Override
  protected SplunkMetricDataCollectionInfo toDataCollectionInfo(SplunkMetricCVConfig cvConfig) {
    List<SplunkMetricDataCollectionInfo.MetricInfo> dataCollectionMetricInfos =
        cvConfig.getMetricInfos()
            .stream()
            .map(splunkMetricInfo
                -> SplunkMetricDataCollectionInfo.MetricInfo.builder()
                       .identifier(splunkMetricInfo.getIdentifier())
                       .metricName(splunkMetricInfo.getMetricName())
                       .query(splunkMetricInfo.getQuery())
                       .build())
            .collect(Collectors.toList());
    SplunkMetricDataCollectionInfo splunkDataCollectionInfo = SplunkMetricDataCollectionInfo.builder()
                                                                  .metricInfos(dataCollectionMetricInfos)
                                                                  .groupName(cvConfig.getGroupName())
                                                                  .build();
    splunkDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return splunkDataCollectionInfo;
  }
}
