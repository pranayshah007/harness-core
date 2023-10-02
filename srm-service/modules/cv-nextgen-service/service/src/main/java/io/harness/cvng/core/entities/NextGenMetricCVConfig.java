/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.monitoredService.MetricThreshold;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;
import io.harness.data.structure.UUIDGenerator;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("NEXTGEN_METRIC")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "CVConfigKeys")
@EqualsAndHashCode(callSuper = true)
public class NextGenMetricCVConfig extends MetricCVConfig<NextGenMetricInfo> {
  @NotNull private String groupName;

  @NotNull private DataSourceType dataSourceType;

  @NotNull private List<NextGenMetricInfo> metricInfos;

  HealthSourceParams healthSourceParams;
  @Override
  public boolean isSLIEnabled() {
    return AnalysisInfoUtility.anySLIEnabled(metricInfos);
  }

  @Override
  public boolean isLiveMonitoringEnabled() {
    return AnalysisInfoUtility.anyLiveMonitoringEnabled(metricInfos);
  }

  @Override
  public boolean isDeploymentVerificationEnabled() {
    return AnalysisInfoUtility.anyDeploymentVerificationEnabled(metricInfos);
  }

  @Override
  protected void validateParams() {
    checkNotNull(groupName, generateErrorMessageFromParam(NextGenMetricCVConfig.CVConfigKeys.groupName));
    checkNotNull(metricInfos, generateErrorMessageFromParam(NextGenMetricCVConfig.CVConfigKeys.metricInfos));
  }

  @Override
  public DataSourceType getType() {
    return dataSourceType;
  }

  @Override
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  public Optional<String> maybeGetGroupName() {
    return Optional.of(groupName);
  }

  @Override
  public List<NextGenMetricInfo> getMetricInfos() {
    if (metricInfos == null) {
      return Collections.emptyList();
    }
    return metricInfos;
  }

  @Override
  public void setMetricInfos(List<NextGenMetricInfo> metricInfos) {
    this.metricInfos = metricInfos;
  }

  public void populateFromQueryDefinitions(List<QueryDefinition> queryDefinitions, CVMonitoringCategory category) {
    if (metricInfos == null) {
      metricInfos = new ArrayList<>();
    }
    Preconditions.checkNotNull(queryDefinitions);
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .orgIdentifier(getOrgIdentifier())
                                .dataSourceType(dataSourceType)
                                .projectIdentifier(getProjectIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .build();

    queryDefinitions.forEach(
        (QueryDefinition queryDefinition) -> setMetricPackFromQueryDefinition(metricPack, queryDefinition));
    this.setMetricPack(metricPack);
  }

  private void setMetricPackFromQueryDefinition(MetricPack metricPack, QueryDefinition queryDefinition) {
    TimeSeriesMetricType metricType = queryDefinition.getRiskProfile().getMetricType();
    metricInfos.add(NextGenMetricInfo.builder()
                        .metricName(queryDefinition.getName())
                        .metricType(metricType)
                        .query(Optional.ofNullable(queryDefinition.getQuery()).map(String::trim).orElse(null))
                        .queryParams(queryDefinition.getQueryParams().getQueryParamsEntity())
                        .identifier(queryDefinition.getIdentifier())
                        .sli(SLIMetricTransformer.transformQueryDefinitiontoEntity(queryDefinition))
                        .liveMonitoring(LiveMonitoringTransformer.transformQueryDefinitiontoEntity(queryDefinition))
                        .deploymentVerification(
                            DevelopmentVerificationTransformer.transformQueryDefinitiontoEntity(queryDefinition))
                        .build());
    Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
        queryDefinition.getName(), metricType, queryDefinition.getRiskProfile().getThresholdTypes());
    metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                .thresholds(new ArrayList<>(thresholds))
                                .type(metricType)
                                .name(queryDefinition.getName())
                                .identifier(queryDefinition.getIdentifier())
                                .included(true)
                                .build());
  }

  @Singleton
  public static class UpdatableEntity
      extends MetricCVConfigUpdatableEntity<NextGenMetricCVConfig, NextGenMetricCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<NextGenMetricCVConfig> updateOperations, NextGenMetricCVConfig cvConfig) {
      setCommonOperations(updateOperations, cvConfig);
      updateOperations.set(NextGenMetricCVConfig.CVConfigKeys.groupName, cvConfig.getGroupName())
          .set(CVConfigKeys.metricInfos, cvConfig.getMetricInfos());
    }
  }

  public void addCustomMetricThresholds(List<QueryDefinition> queryDefinitions) {
    getMetricPack().getMetrics().forEach(metric -> queryDefinitions.forEach(queryDefinition -> {
      if (!isEmpty(queryDefinition.getMetricThresholds())) {
        queryDefinition.getMetricThresholds()
            .stream()
            .filter(metricThreshold -> metric.getName().equals(metricThreshold.getMetricName()))
            .forEach(metricThreshold -> setMetricThreshold(metric, metricThreshold));
      }
    }));
  }

  private void setMetricThreshold(MetricPack.MetricDefinition metric, MetricThreshold metricThreshold) {
    metricThreshold.getTimeSeriesThresholdCriteria().forEach(criteria -> {
      List<TimeSeriesThreshold> timeSeriesThresholds =
          metric.getThresholds() != null ? metric.getThresholds() : new ArrayList<>();
      TimeSeriesThreshold timeSeriesThreshold =
          TimeSeriesThreshold.builder()
              .uuid(UUIDGenerator.generateUuid())
              .accountId(getAccountId())
              .projectIdentifier(getProjectIdentifier())
              .dataSourceType(getType())
              .metricIdentifier(metric.getIdentifier())
              .metricType(metric.getType())
              .metricName(metricThreshold.getMetricName())
              .action(metricThreshold.getType().getTimeSeriesThresholdActionType())
              .criteria(criteria)
              .thresholdConfigType(ThresholdConfigType.USER_DEFINED)
              .build();
      timeSeriesThresholds.add(timeSeriesThreshold);
      metric.setThresholds(timeSeriesThresholds);
    });
  }

  public void addMetricPackAndInfo(List<QueryDefinition> queryDefinitions) {
    CVMonitoringCategory category = queryDefinitions.get(0).getRiskProfile().getCategory();
    MetricPack metricPack = MetricPack.builder()
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .accountId(getAccountId())
                                .orgIdentifier(getOrgIdentifier())
                                .projectIdentifier(getProjectIdentifier())
                                .category(category)
                                .dataSourceType(dataSourceType)
                                .build();
    this.metricInfos =
        queryDefinitions.stream()
            .map((QueryDefinition queryDefinition) -> {
              NextGenMetricInfo metricInfo =
                  NextGenMetricInfo.builder()
                      .identifier(queryDefinition.getIdentifier())
                      .metricName(queryDefinition.getName())
                      .query(queryDefinition.getQuery().trim())
                      .queryParams(queryDefinition.getQueryParams().getQueryParamsEntity())
                      .sli(SLIMetricTransformer.transformQueryDefinitiontoEntity(queryDefinition))
                      .liveMonitoring(LiveMonitoringTransformer.transformQueryDefinitiontoEntity(queryDefinition))
                      .deploymentVerification(
                          DevelopmentVerificationTransformer.transformQueryDefinitiontoEntity(queryDefinition))
                      .metricType(queryDefinition.getRiskProfile().getMetricType())
                      .build();
              // Setting default thresholds
              Set<TimeSeriesThreshold> thresholds =
                  getThresholdsToCreateOnSaveForCustomProviders(metricInfo.getMetricName(), metricInfo.getMetricType(),
                      queryDefinition.getRiskProfile().getThresholdTypes());

              metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                          .thresholds(new ArrayList<>(thresholds))
                                          .type(metricInfo.getMetricType())
                                          .name(metricInfo.getMetricName())
                                          .identifier(metricInfo.getIdentifier())
                                          .included(true)
                                          .build());
              return metricInfo;
            })
            .collect(Collectors.toList());

    this.setMetricPack(metricPack);
  }
}