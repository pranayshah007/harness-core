/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.CustomHealthDataCollectionInfo;
import io.harness.cvng.beans.MetricResponseMappingDTO;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.stream.Collectors;

public class CustomHealthMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<CustomHealthDataCollectionInfo, CustomHealthMetricCVConfig> {
  @Override
  protected CustomHealthDataCollectionInfo toDataCollectionInfo(CustomHealthMetricCVConfig baseCVConfig) {
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        CustomHealthDataCollectionInfo.builder()
            .groupName(baseCVConfig.getGroupName())
            .metricInfoList(baseCVConfig.getMetricInfos()
                                .stream()
                                .map(metricDefinition -> mapMetricDefinitionToMetricInfo(metricDefinition))
                                .collect(Collectors.toList()))
            .build();
    customHealthDataCollectionInfo.setDataCollectionDsl(baseCVConfig.getDataCollectionDsl());
    return customHealthDataCollectionInfo;
  }

  private CustomHealthDataCollectionInfo.CustomHealthMetricInfo mapMetricDefinitionToMetricInfo(
      CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition) {
    MetricResponseMapping metricResponseMapping = metricDefinition.getMetricResponseMapping();
    CustomHealthRequestDefinition healthDefinition = metricDefinition.getRequestDefinition();
    return CustomHealthDataCollectionInfo.CustomHealthMetricInfo.builder()
        .metricName(metricDefinition.getMetricName())
        .metricIdentifier(metricDefinition.getIdentifier())
        .endTime(healthDefinition.getEndTimeInfo())
        .responseMapping(
            MetricResponseMappingDTO.builder()
                .metricValueJsonPath(metricResponseMapping.getMetricValueJsonPath())
                .serviceInstanceJsonPath(metricResponseMapping.getServiceInstanceJsonPath())
                .serviceInstanceListJsonPath(metricResponseMapping.getServiceInstanceListJsonPath())
                .relativeServiceInstanceValueJsonPath(metricResponseMapping.getRelativeServiceInstanceValueJsonPath())
                .relativeMetricListJsonPath(metricResponseMapping.getRelativeMetricListJsonPath())
                .relativeMetricValueJsonPath(metricResponseMapping.getRelativeMetricValueJsonPath())
                .relativeTimestampJsonPath(metricResponseMapping.getRelativeTimestampJsonPath())
                .timestampFormat(metricResponseMapping.getTimestampFormat())
                .timestampJsonPath(metricResponseMapping.getTimestampJsonPath())
                .build())
        .body(healthDefinition.getRequestBody())
        .method(healthDefinition.getMethod())
        .startTime(healthDefinition.getStartTimeInfo())
        .urlPath(healthDefinition.getUrlPath())
        .build();
  }
}
