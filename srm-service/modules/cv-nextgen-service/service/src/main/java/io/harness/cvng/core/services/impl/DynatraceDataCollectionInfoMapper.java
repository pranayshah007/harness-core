/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DynatraceDataCollectionInfo;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.stream.Collectors;

public class DynatraceDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<DynatraceDataCollectionInfo, DynatraceCVConfig> {
  @Override
  protected DynatraceDataCollectionInfo toDataCollectionInfo(DynatraceCVConfig cvConfig) {
    DynatraceDataCollectionInfo dynatraceDataCollectionInfo =
        DynatraceDataCollectionInfo.builder()
            .metricPack(cvConfig.getMetricPack().toDTO())
            .groupName(cvConfig.getGroupName())
            .serviceId(cvConfig.getDynatraceServiceId())
            .serviceMethodIds(cvConfig.getServiceMethodIds())
            .customMetrics(cvConfig.getMetricInfos()
                               .stream()
                               .map(metricInfo
                                   -> DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
                                          .metricName(metricInfo.getMetricName())
                                          .identifier(metricInfo.getIdentifier())
                                          .metricSelector(metricInfo.getMetricSelector())
                                          .build())
                               .collect(Collectors.toList()))
            .build();
    dynatraceDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return dynatraceDataCollectionInfo;
  }
}
