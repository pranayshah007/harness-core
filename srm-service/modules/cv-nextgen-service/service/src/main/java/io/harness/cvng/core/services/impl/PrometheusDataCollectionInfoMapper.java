/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.beans.FeatureName;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.PrometheusDataCollectionInfo;
import io.harness.cvng.beans.PrometheusDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;
import io.harness.cvng.utils.PrometheusQueryUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class PrometheusDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<PrometheusDataCollectionInfo, PrometheusCVConfig> {
  @Inject private FeatureFlagService featureFlagService;

  @Override
  protected PrometheusDataCollectionInfo toDataCollectionInfo(PrometheusCVConfig cvConfig) {
    Preconditions.checkNotNull(cvConfig);
    List<MetricCollectionInfo> metricCollectionInfoList =
        cvConfig.getMetricInfos().stream().map(this::getMetricCollectionInfo).collect(Collectors.toList());
    return getDataCollectionInfo(metricCollectionInfoList, cvConfig);
  }

  private MetricCollectionInfo getMetricCollectionInfo(MetricInfo metricInfo) {
    return PrometheusDataCollectionInfo.MetricCollectionInfo.builder()
        .metricName(metricInfo.getMetricName())
        .metricIdentifier(metricInfo.getIdentifier())
        .query(metricInfo.getQuery())
        .filters(metricInfo.getFilters())
        .serviceInstanceField(metricInfo.getServiceInstanceFieldName())
        .build();
  }

  private PrometheusDataCollectionInfo getDataCollectionInfo(
      List<MetricCollectionInfo> metricDefinitions, PrometheusCVConfig cvConfig) {
    PrometheusDataCollectionInfo dataCollectionInfo = PrometheusDataCollectionInfo.builder()
                                                          .groupName(cvConfig.getGroupName())
                                                          .metricCollectionInfoList(metricDefinitions)
                                                          .build();
    dataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    return dataCollectionInfo;
  }

  @Override
  public void postProcessDataCollectionInfo(PrometheusDataCollectionInfo dataCollectionInfo,
      PrometheusCVConfig cvConfig, VerificationTask.TaskType taskType) {
    if (featureFlagService.isFeatureFlagEnabled(
            cvConfig.getAccountId(), FeatureName.SRM_ENABLE_AGGREGATION_USING_BY_IN_PROMETHEUS.name())) {
      if (dataCollectionInfo.isCollectHostData()) {
        dataCollectionInfo.setDataCollectionDsl(DataCollectionDSLFactory.readMetricDSL(DataSourceType.PROMETHEUS));
        // wrong as filters are already added. TODO
        for (MetricCollectionInfo metricCollectionInfo : dataCollectionInfo.getMetricCollectionInfoList()) {
          metricCollectionInfo.setQuery(PrometheusQueryUtils.formGroupByQuery(
              metricCollectionInfo.getQuery(), metricCollectionInfo.getServiceInstanceField()));
        }
      }
    }
  }
}
