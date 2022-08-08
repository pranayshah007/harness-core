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
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkMetricHealthSourceSpec;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@FieldNameConstants(innerTypeName = "SplunkMetricCVConfigKeys")
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SplunkMetricCVConfig extends MetricCVConfig<SplunkMetricInfo> {
  @NotNull private String groupName;
  @NotNull private List<SplunkMetricInfo> metricInfos;

  public void populateFromMetricDefinitions(
      List<SplunkMetricHealthSourceSpec.SplunkMetricDefinition> metricDefinitions, CVMonitoringCategory category) {
    if (metricInfos == null) {
      metricInfos = new ArrayList<>();
    }
    Preconditions.checkNotNull(metricDefinitions);
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.SPLUNK_METRIC)
                                .projectIdentifier(getProjectIdentifier())
                                .identifier(category.getDisplayName())
                                .build();

    metricDefinitions.forEach(metricDefinition -> {
      TimeSeriesMetricType metricType = metricDefinition.getRiskProfile().getMetricType();
      metricInfos.add(
          SplunkMetricInfo.builder()
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
    });
    this.setMetricPack(metricPack);
  }
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
  public Optional<String> maybeGetGroupName() {
    return Optional.of(groupName);
  }

  @Override
  public List<SplunkMetricInfo> getMetricInfos() {
    return metricInfos;
  }

  @Override
  public void setMetricInfos(List<SplunkMetricInfo> metricInfos) {
    this.metricInfos = metricInfos;
  }

  @Override
  protected void validateParams() {
    checkNotNull(groupName, generateErrorMessageFromParam(SplunkMetricCVConfigKeys.groupName));
    checkNotNull(metricInfos, generateErrorMessageFromParam(SplunkMetricCVConfigKeys.metricInfos));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.SPLUNK_METRIC;
  }

  @Override
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  public static class SplunkMetricUpdatableEntity
      extends MetricCVConfigUpdatableEntity<SplunkMetricCVConfig, SplunkMetricCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<SplunkMetricCVConfig> updateOperations, SplunkMetricCVConfig cvConfig) {
      setCommonOperations(updateOperations, cvConfig);
      updateOperations.set(SplunkMetricCVConfigKeys.groupName, cvConfig.getGroupName())
          .set(SplunkMetricCVConfigKeys.metricInfos, cvConfig.getMetricInfos());
    }
  }
}
