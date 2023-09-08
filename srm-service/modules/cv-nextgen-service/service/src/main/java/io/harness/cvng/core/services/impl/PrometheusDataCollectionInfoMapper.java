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
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricDataCollectionInfoMapper;
import io.harness.cvng.utils.PrometheusQueryUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class PrometheusDataCollectionInfoMapper
    extends MetricDataCollectionInfoMapper<PrometheusDataCollectionInfo, MetricCVConfig> {
  @Inject private FeatureFlagService featureFlagService;
  @Override
  protected PrometheusDataCollectionInfo toDataCollectionInfo(MetricCVConfig cvConfig) {
    PrometheusDataCollectionInfo prometheusDataCollectionInfo;
    if (cvConfig instanceof PrometheusCVConfig) {
      PrometheusCVConfig prometheusCVConfig = (PrometheusCVConfig) cvConfig;
      Preconditions.checkNotNull(prometheusCVConfig);
      List<MetricCollectionInfo> metricCollectionInfoList =
          prometheusCVConfig.getMetricInfos().stream().map(this::getMetricCollectionInfo).collect(Collectors.toList());
      prometheusDataCollectionInfo = PrometheusDataCollectionInfo.builder()
                                         .groupName(prometheusCVConfig.getGroupName())
                                         .metricCollectionInfoList(metricCollectionInfoList)
                                         .build();
      prometheusDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
    } else {
      NextGenMetricCVConfig nextGenMetricCVConfig = (NextGenMetricCVConfig) cvConfig;
      List<MetricCollectionInfo> dataCollectionMetricInfos =
          nextGenMetricCVConfig.getMetricInfos()
              .stream()
              .map(PrometheusDataCollectionInfoMapper::getMetricCollectionInfo)
              .collect(Collectors.toList());
      prometheusDataCollectionInfo = PrometheusDataCollectionInfo.builder()
                                         .metricCollectionInfoList(dataCollectionMetricInfos)
                                         .groupName(nextGenMetricCVConfig.getGroupName())
                                         .build();
      prometheusDataCollectionInfo.setDataCollectionDsl(cvConfig.getDataCollectionDsl());
      if (prometheusDataCollectionInfo.isCollectHostData()) {
        prometheusDataCollectionInfo.setDataCollectionDsl(
            DataCollectionDSLFactory.readMetricDSL(DataSourceType.PROMETHEUS));
        List<MetricCollectionInfo> metricCollectionInfoList =
            nextGenMetricCVConfig.getMetricInfos()
                .stream()
                .map(PrometheusDataCollectionInfoMapper::getMetricCollectionInfo)
                .peek(metricCollectionInfo
                    -> metricCollectionInfo.setQuery(PrometheusQueryUtils.formGroupByQuery(
                        metricCollectionInfo.getQuery(), metricCollectionInfo.getServiceInstanceField())))
                .collect(Collectors.toList());
        prometheusDataCollectionInfo.setMetricCollectionInfoList(metricCollectionInfoList);
      }
    }
    return prometheusDataCollectionInfo;
  }

  @Override
  public PrometheusDataCollectionInfo toDeploymentDataCollectionInfo(
      MetricCVConfig cvConfig, List<String> serviceInstances) {
    PrometheusDataCollectionInfo dataCollectionInfo =
        this.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.DEPLOYMENT);
    if (CollectionUtils.isNotEmpty(serviceInstances)) {
      dataCollectionInfo.getMetricCollectionInfoList().forEach(metricCollectionInfo -> {
        StringBuilder serviceInstanceFilterAdditionBuilder = new StringBuilder();
        if (StringUtils.isNotEmpty(metricCollectionInfo.getFilters())) {
          serviceInstanceFilterAdditionBuilder.append(",");
        }
        serviceInstanceFilterAdditionBuilder.append(metricCollectionInfo.getServiceInstanceField())
            .append("=~\"")
            .append(StringUtils.joinWith("|", serviceInstances.toArray()))
            .append("\"");
        metricCollectionInfo.setFilters(metricCollectionInfo.getFilters() + serviceInstanceFilterAdditionBuilder);
        metricCollectionInfo.setQuery(metricCollectionInfo.getQuery().replaceAll(
            "}", serviceInstanceFilterAdditionBuilder.append("}").toString()));
      });
    }
    dataCollectionInfo.setServiceInstances(serviceInstances);
    return dataCollectionInfo;
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
  private static MetricCollectionInfo getMetricCollectionInfo(NextGenMetricInfo nextGenMetricInfo) {
    String serviceIdentifierTag =
        Optional.ofNullable(nextGenMetricInfo.getQueryParams()).map(QueryParams::getServiceInstanceField).orElse(null);
    return MetricCollectionInfo.builder()
        .metricIdentifier(nextGenMetricInfo.getIdentifier())
        .metricName(nextGenMetricInfo.getMetricName())
        .query(nextGenMetricInfo.getQuery())
        .serviceInstanceField(serviceIdentifierTag)
        .build();
  }

  @Override
  public void postProcessDataCollectionInfo(
      PrometheusDataCollectionInfo dataCollectionInfo, MetricCVConfig cvConfig, VerificationTask.TaskType taskType) {
    if (featureFlagService.isFeatureFlagEnabled(
            cvConfig.getAccountId(), FeatureName.SRM_ENABLE_AGGREGATION_USING_BY_IN_PROMETHEUS.name())) {
      PrometheusCVConfig prometheusCVConfig = (PrometheusCVConfig) cvConfig;
      if (dataCollectionInfo.isCollectHostData()) {
        dataCollectionInfo.setDataCollectionDsl(DataCollectionDSLFactory.readMetricDSL(DataSourceType.PROMETHEUS));
        List<MetricCollectionInfo> metricCollectionInfoList =
            prometheusCVConfig.getMetricInfos()
                .stream()
                .map(this::getMetricCollectionInfo)
                .peek(metricCollectionInfo
                    -> metricCollectionInfo.setQuery(PrometheusQueryUtils.formGroupByQuery(
                        metricCollectionInfo.getQuery(), metricCollectionInfo.getServiceInstanceField())))
                .collect(Collectors.toList());
        dataCollectionInfo.setMetricCollectionInfoList(metricCollectionInfoList);
      }
    }
  }
}
