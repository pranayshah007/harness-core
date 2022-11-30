/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.StackdriverDataCollectionInfo;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.cvng.core.entities.StackdriverCVConfig.MetricInfo;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.stream.Collectors;

public class StackdriverDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<StackdriverDataCollectionInfo, StackdriverCVConfig> {
  @Override
  protected StackdriverDataCollectionInfo toDataCollectionInfo(StackdriverCVConfig cvConfig) {
    List<StackDriverMetricDefinition> metricDefinitions = cvConfig.getMetricInfos()
                                                              .stream()
                                                              .map(metricInfo -> getMetricCollectionInfo(metricInfo))
                                                              .collect(Collectors.toList());
    return getDataCollectionInfo(metricDefinitions, cvConfig);
  }

  private StackDriverMetricDefinition getMetricCollectionInfo(MetricInfo metricInfo) {
    StackDriverMetricDefinition metricDefinition =
        StackDriverMetricDefinition.extractFromJson(metricInfo.getJsonMetricDefinition());
    metricDefinition.setServiceInstanceField(metricInfo.getServiceInstanceField());
    metricDefinition.setMetricIdentifier(metricInfo.getIdentifier());
    return metricDefinition;
  }

  private StackdriverDataCollectionInfo getDataCollectionInfo(
      List<StackDriverMetricDefinition> metricDefinitions, StackdriverCVConfig cvConfig) {
    StackdriverDataCollectionInfo dataCollectionInfo =
        StackdriverDataCollectionInfo.builder().metricDefinitions(metricDefinitions).build();
    dataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return dataCollectionInfo;
  }
}
