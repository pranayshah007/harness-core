/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.NewRelicDataCollectionInfo;
import io.harness.cvng.beans.NewRelicDataCollectionInfo.NewRelicMetricInfoDTO;
import io.harness.cvng.core.entities.NewRelicCVConfig;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicMetricInfo;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class NewRelicDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<NewRelicDataCollectionInfo, NewRelicCVConfig> {
  @Override
  protected NewRelicDataCollectionInfo toDataCollectionInfo(NewRelicCVConfig cvConfig) {
    NewRelicDataCollectionInfo newRelicDataCollectionInfo = NewRelicDataCollectionInfo.builder()
                                                                .applicationId(cvConfig.getApplicationId())
                                                                .applicationName(cvConfig.getApplicationName())
                                                                .metricPack(cvConfig.getMetricPack().toDTO())
                                                                .build();
    newRelicDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());

    if (cvConfig.isCustomQuery()) {
      newRelicDataCollectionInfo.setGroupName(cvConfig.getGroupName());
      newRelicDataCollectionInfo.setCustomQuery(true);
      List<NewRelicMetricInfoDTO> metricInfoDTOS = CollectionUtils.emptyIfNull(cvConfig.getMetricInfos())
                                                       .stream()
                                                       .map(newRelicMetricInfo -> generateInfoDTO(newRelicMetricInfo))
                                                       .collect(Collectors.toList());
      newRelicDataCollectionInfo.setMetricInfoList(metricInfoDTOS);
    }
    return newRelicDataCollectionInfo;
  }

  private NewRelicMetricInfoDTO generateInfoDTO(NewRelicMetricInfo newRelicMetricInfo) {
    return NewRelicMetricInfoDTO.builder()
        .metricName(newRelicMetricInfo.getMetricName())
        .metricIdentifier(newRelicMetricInfo.getIdentifier())
        .nrql(newRelicMetricInfo.getNrql())
        .responseMapping(newRelicMetricInfo.getResponseMapping().toDto())
        .build();
  }
}
