/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.DatadogMetricHealthDefinition;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.MetricInfo;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@JsonTypeName("DATADOG")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "DatadogCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DatadogMetricCVConfig extends MetricCVConfig<MetricInfo> {
  private List<MetricInfo> metricInfoList;
  private String dashboardId;
  private String dashboardName;

  public void populateFromMetricDefinitions(
      List<DatadogMetricHealthDefinition> datadogMetricDefinitions, CVMonitoringCategory category) {
    Preconditions.checkNotNull(datadogMetricDefinitions);
    if (metricInfoList == null) {
      metricInfoList = new ArrayList<>();
    }
    dashboardName = datadogMetricDefinitions.get(0).getDashboardName();
    dashboardId = datadogMetricDefinitions.get(0).getDashboardId();
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.DATADOG_METRICS)
                                .projectIdentifier(getProjectIdentifier())
                                .identifier(category.getDisplayName())
                                .build();

    datadogMetricDefinitions.forEach(definition -> {
      if (definition.getAnalysis() != null && definition.getAnalysis().getDeploymentVerification() != null
          && definition.getAnalysis().getDeploymentVerification().getEnabled() != null
          && definition.getAnalysis().getDeploymentVerification().getEnabled()) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(definition.getServiceInstanceIdentifierTag()),
            "metric: %s, serviceInstanceIdentifierTag is required for Deployment verification",
            definition.getMetricName());
      }
      TimeSeriesMetricType metricType = definition.getRiskProfile().getMetricType();
      metricInfoList.add(
          MetricInfo.builder()
              .metricName(definition.getMetricName())
              .identifier(definition.getIdentifier())
              .metric(definition.getMetric())
              .metricPath(definition.getMetricPath())
              .query(definition.getQuery())
              .groupingQuery(definition.getGroupingQuery())
              .metricType(metricType)
              .aggregation(definition.getAggregation())
              .metricTags(definition.getMetricTags())
              .isManualQuery(definition.isManualQuery())
              .isCustomCreatedMetric(definition.isCustomCreatedMetric())
              .serviceInstanceIdentifierTag(definition.getServiceInstanceIdentifierTag())
              .sli(SLIMetricTransformer.transformDTOtoEntity(definition.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(definition.getAnalysis()))
              .deploymentVerification(DevelopmentVerificationTransformer.transformDTOtoEntity(definition.getAnalysis()))
              .build());

      // add this metric to the pack and the corresponding thresholds
      Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
          definition.getMetric(), metricType, definition.getRiskProfile().getThresholdTypes());
      metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                  .thresholds(new ArrayList<>(thresholds))
                                  .type(metricType)
                                  .name(definition.getMetricName())
                                  .identifier(definition.getIdentifier())
                                  .included(true)
                                  .build());
    });
    this.setMetricPack(metricPack);
  }

  @Override
  public boolean isSLIEnabled() {
    return AnalysisInfoUtility.anySLIEnabled(metricInfoList);
  }

  @Override
  public boolean isLiveMonitoringEnabled() {
    return AnalysisInfoUtility.anyLiveMonitoringEnabled(metricInfoList);
  }

  @Override
  public boolean isDeploymentVerificationEnabled() {
    return AnalysisInfoUtility.anyDeploymentVerificationEnabled(metricInfoList);
  }

  @Override
  public Optional<String> maybeGetGroupName() {
    return Optional.empty(); // it does not have group name for some reason. Need to check and refactor.
  }

  @Override
  public List<MetricInfo> getMetricInfos() {
    if (metricInfoList == null) {
      return Collections.emptyList();
    }
    return metricInfoList;
  }

  @Override
  public void setMetricInfos(List<MetricInfo> metricInfos) {
    this.metricInfoList = metricInfos;
  }

  @Data
  @SuperBuilder
  @FieldNameConstants(innerTypeName = "MetricInfoKeys")
  public static class MetricInfo extends AnalysisInfo {
    private String metricPath;
    private String metric;
    private String query;
    private String groupingQuery;
    private String aggregation;
    private List<String> metricTags;
    private TimeSeriesMetricType metricType;
    boolean isManualQuery;
    boolean isCustomCreatedMetric;
    private String serviceInstanceIdentifierTag;
  }

  @Override
  protected void validateParams() {
    checkNotNull(metricInfoList, generateErrorMessageFromParam(DatadogCVConfigKeys.metricInfoList));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.DATADOG_METRICS;
  }

  @Override
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  public static class DatadogMetricCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<DatadogMetricCVConfig, DatadogMetricCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<DatadogMetricCVConfig> updateOperations, DatadogMetricCVConfig datadogMetricCVConfig) {
      setCommonOperations(updateOperations, datadogMetricCVConfig);
      updateOperations.set(DatadogCVConfigKeys.metricInfoList, datadogMetricCVConfig.getMetricInfoList());
      updateOperations.set(DatadogCVConfigKeys.dashboardName, datadogMetricCVConfig.getDashboardName());
      if (datadogMetricCVConfig.getDashboardId() != null) {
        updateOperations.set(DatadogCVConfigKeys.dashboardId, datadogMetricCVConfig.getDashboardId());
      }
    }
  }
}
