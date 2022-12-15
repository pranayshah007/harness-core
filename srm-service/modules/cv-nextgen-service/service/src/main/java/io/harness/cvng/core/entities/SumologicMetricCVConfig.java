/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricDefinition;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
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
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("SUMOLOGIC_METRIC")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "CVConfigKeys")
@EqualsAndHashCode(callSuper = true)
public class SumologicMetricCVConfig extends MetricCVConfig<SumologicMetricInfo> {
  @NotNull private String groupName;

  @NotNull private List<SumologicMetricInfo> metricInfos;
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
    checkNotNull(groupName, generateErrorMessageFromParam(SumologicMetricCVConfig.CVConfigKeys.groupName));
    checkNotNull(metricInfos, generateErrorMessageFromParam(SumologicMetricCVConfig.CVConfigKeys.metricInfos));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.SUMOLOGIC_METRICS;
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
  public List<SumologicMetricInfo> getMetricInfos() {
    if (metricInfos == null) {
      return Collections.emptyList();
    }
    return metricInfos;
  }

  @Override
  public void setMetricInfos(List<SumologicMetricInfo> metricInfos) {
    this.metricInfos = metricInfos;
  }

  public void populateFromMetricDefinitions(List<MetricDefinition> metricDefinitions, CVMonitoringCategory category) {
    if (metricInfos == null) {
      metricInfos = new ArrayList<>();
    }
    Preconditions.checkNotNull(metricDefinitions);
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.SUMOLOGIC_METRICS)
                                .projectIdentifier(getProjectIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .build();

    metricDefinitions.forEach(
        (MetricDefinition metricDefinition) -> setMetricPackFromMetricDefinition(metricPack, metricDefinition));
    this.setMetricPack(metricPack);
  }

  private void setMetricPackFromMetricDefinition(MetricPack metricPack, MetricDefinition metricDefinition) {
    TimeSeriesMetricType metricType = metricDefinition.getRiskProfile().getMetricType();
    metricInfos.add(SumologicMetricInfo.builder()
                        .metricName(metricDefinition.getMetricName())
                        .query(metricDefinition.getQuery())
                        .identifier(metricDefinition.getIdentifier())
                        .sli(SLIMetricTransformer.transformDTOtoEntity(metricDefinition.getSli()))
                        .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(metricDefinition.getAnalysis()))
                        .deploymentVerification(
                            DevelopmentVerificationTransformer.transformDTOtoEntity(metricDefinition.getAnalysis()))
                        .build());

    // add the relevant thresholds to metricPack
    Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
        metricDefinition.getMetricName(), metricType, metricDefinition.getRiskProfile().getThresholdTypes());
    metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                .thresholds(new ArrayList<>(thresholds))
                                .type(metricType)
                                .name(metricDefinition.getMetricName())
                                .identifier(metricDefinition.getIdentifier())
                                .included(true)
                                .build());
  }

  public static class UpdatableEntity
      extends MetricCVConfigUpdatableEntity<SumologicMetricCVConfig, SumologicMetricCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<SumologicMetricCVConfig> updateOperations, SumologicMetricCVConfig cvConfig) {
      setCommonOperations(updateOperations, cvConfig);
      updateOperations.set(SumologicMetricCVConfig.CVConfigKeys.groupName, cvConfig.getGroupName())
          .set(CVConfigKeys.metricInfos, cvConfig.getMetricInfos());
    }
  }

  // TODO understand if these can be removed like splunk , very Imp to revisit.
  private MetricPack createMetricPack(CVMonitoringCategory category) {
    return MetricPack.builder()
        .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
        .accountId(getAccountId())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .category(category)
        .dataSourceType(DataSourceType.SUMOLOGIC_METRICS)
        .build();
  }

  // TODO remove

  public void addMetricPackAndInfo(List<MetricDefinition> metricDefinitions) {
    CVMonitoringCategory category = metricDefinitions.get(0).getRiskProfile().getCategory();
    MetricPack metricPack = createMetricPack(category);
    this.metricInfos =
        metricDefinitions.stream()
            .map((MetricDefinition metricDefinition) -> {
              SumologicMetricInfo metricInfo =
                  SumologicMetricInfo.builder()
                      .identifier(metricDefinition.getIdentifier())
                      .metricName(metricDefinition.getMetricName())
                      .query(metricDefinition.getQuery())
                      .responseMapping(metricDefinition.getResponseMapping())
                      .sli(SLIMetricTransformer.transformDTOtoEntity(metricDefinition.getSli()))
                      .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(metricDefinition.getAnalysis()))
                      .deploymentVerification(
                          DevelopmentVerificationTransformer.transformDTOtoEntity(metricDefinition.getAnalysis()))
                      .metricType(metricDefinition.getRiskProfile().getMetricType())
                      .build();
              // Setting default thresholds
              Set<TimeSeriesThreshold> thresholds =
                  getThresholdsToCreateOnSaveForCustomProviders(metricInfo.getMetricName(), metricInfo.getMetricType(),
                      metricDefinition.getRiskProfile().getThresholdTypes());

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