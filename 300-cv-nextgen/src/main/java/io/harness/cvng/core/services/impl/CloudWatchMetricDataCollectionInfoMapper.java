/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo;
import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig.CloudWatchMetricInfo;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CloudWatchMetricDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<CloudWatchMetricDataCollectionInfo, CloudWatchMetricCVConfig> {
  @Override
  protected CloudWatchMetricDataCollectionInfo toDataCollectionInfo(CloudWatchMetricCVConfig cvConfig) {
    List<CloudWatchMetricInfoDTO> metricInfoDTOs =
        cvConfig.getMetricInfos().stream().map(this::generateInfoDTO).collect(Collectors.toList());
    CloudWatchMetricDataCollectionInfo cloudWatchMetricDataCollectionInfo =
        CloudWatchMetricDataCollectionInfo.builder()
            .region(cvConfig.getRegion())
            .groupName(cvConfig.getGroupName())
            .metricInfos(metricInfoDTOs)
            .metricPack(cvConfig.getMetricPack().toDTO())
            .build();
    cloudWatchMetricDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return cloudWatchMetricDataCollectionInfo;
  }

  private CloudWatchMetricInfoDTO generateInfoDTO(CloudWatchMetricInfo metricInfo) {
    return CloudWatchMetricInfoDTO.builder()
        .metricName(metricInfo.getMetricName())
        .metricIdentifier(metricInfo.getIdentifier())
        .expression(metricInfo.getExpression())
        .finalExpression(metricInfo.getExpression())
        .responseMapping(
            Objects.nonNull(metricInfo.getResponseMapping()) ? metricInfo.getResponseMapping().toDto() : null)
        .build();
  }
}
