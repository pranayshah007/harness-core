/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.AppDynamicsDataCollectionInfo;
import io.harness.cvng.beans.AppDynamicsDataCollectionInfo.AppMetricInfoDTO;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class AppDynamicsDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<AppDynamicsDataCollectionInfo, AppDynamicsCVConfig> {
  @Override
  protected AppDynamicsDataCollectionInfo toDataCollectionInfo(AppDynamicsCVConfig baseCVConfig) {
    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName(baseCVConfig.getApplicationName())
            .tierName(baseCVConfig.getTierName())
            .metricPack(baseCVConfig.getMetricPack().toDTO())
            .groupName(baseCVConfig.getGroupName())
            .customMetrics(
                CollectionUtils.emptyIfNull(baseCVConfig.getMetricInfos())
                    .stream()
                    .map(metricInfo
                        -> AppMetricInfoDTO.builder()
                               .metricName(metricInfo.getMetricName())
                               .metricIdentifier(metricInfo.getIdentifier())
                               .completeMetricPath(metricInfo.getCompleteMetricPath())
                               .completeServiceInstanceMetricPath(metricInfo.getCompleteServiceInstanceMetricPath())
                               .build())
                    .collect(Collectors.toList()))
            .build();
    appDynamicsDataCollectionInfo.setDataCollectionDsl(baseCVConfig.getDataCollectionDsl());
    return appDynamicsDataCollectionInfo;
  }
}
