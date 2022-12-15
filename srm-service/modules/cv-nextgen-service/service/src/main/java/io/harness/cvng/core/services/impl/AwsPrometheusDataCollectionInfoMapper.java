/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo;
import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;

public class AwsPrometheusDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<AwsPrometheusDataCollectionInfo, AwsPrometheusCVConfig> {
  @Override
  protected AwsPrometheusDataCollectionInfo toDataCollectionInfo(AwsPrometheusCVConfig cvConfig) {
    Preconditions.checkNotNull(cvConfig);
    List<MetricCollectionInfo> metricCollectionInfoList =
        cvConfig.getMetricInfos().stream().map(this::getMetricCollectionInfo).collect(Collectors.toList());
    return getDataCollectionInfo(metricCollectionInfoList, cvConfig);
  }

  private MetricCollectionInfo getMetricCollectionInfo(MetricInfo metricInfo) {
    return MetricCollectionInfo.builder()
        .metricName(metricInfo.getMetricName())
        .metricIdentifier(metricInfo.getIdentifier())
        .query(metricInfo.getQuery())
        .filters(metricInfo.getFilters())
        .serviceInstanceField(metricInfo.getServiceInstanceFieldName())
        .build();
  }

  private AwsPrometheusDataCollectionInfo getDataCollectionInfo(
      List<MetricCollectionInfo> metricDefinitions, AwsPrometheusCVConfig cvConfig) {
    AwsPrometheusDataCollectionInfo dataCollectionInfo = AwsPrometheusDataCollectionInfo.builder()
                                                             .groupName(cvConfig.getGroupName())
                                                             .metricCollectionInfoList(metricDefinitions)
                                                             .build();
    dataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    dataCollectionInfo.setRegion(cvConfig.getRegion());
    dataCollectionInfo.setWorkspaceId(cvConfig.getWorkspaceId());
    return dataCollectionInfo;
  }
}
