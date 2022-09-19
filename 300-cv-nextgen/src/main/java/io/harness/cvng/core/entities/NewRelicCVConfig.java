/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec.NewRelicMetricDefinition;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicMetricInfo;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("NEW_RELIC")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "NewRelicCVConfigKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NewRelicCVConfig extends MetricCVConfig<NewRelicMetricInfo> {
  private String applicationName;
  private long applicationId;
  private String groupName;
  private List<NewRelicMetricInfo> metricInfos;
  private HealthSourceQueryType queryType;

  boolean customQuery;

  public boolean isCustomQuery() {
    return isNotEmpty(metricInfos);
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.NEW_RELIC;
  }

  @Override
  @JsonIgnore
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  protected void validateParams() {
    boolean appIdPresent = false, customMetricPresent = false;
    if (isEmpty(metricInfos)) {
      checkNotNull(applicationName, generateErrorMessageFromParam(NewRelicCVConfigKeys.applicationName));
      checkNotNull(applicationId, generateErrorMessageFromParam(NewRelicCVConfigKeys.applicationId));
      appIdPresent = true;
    } else {
      customMetricPresent = true;
    }
    Preconditions.checkState(appIdPresent || customMetricPresent,
        "CVConfig should have either application based setup or custom metric setup or both.");
  }

  @Override
  public boolean isSLIEnabled() {
    if (!getMetricPack().getIdentifier().equals(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)) {
      return false;
    }
    return AnalysisInfoUtility.anySLIEnabled(metricInfos);
  }

  @Override
  public boolean isLiveMonitoringEnabled() {
    if (!getMetricPack().getIdentifier().equals(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)) {
      return true;
    }
    return AnalysisInfoUtility.anyLiveMonitoringEnabled(metricInfos);
  }

  @Override
  public boolean isDeploymentVerificationEnabled() {
    if (!getMetricPack().getIdentifier().equals(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)) {
      return true;
    }
    return AnalysisInfoUtility.anyDeploymentVerificationEnabled(metricInfos);
  }

  @Override
  public Optional<String> maybeGetGroupName() {
    return Optional.ofNullable(groupName);
  }

  @Override
  public List<NewRelicMetricInfo> getMetricInfos() {
    if (metricInfos == null) {
      return Collections.emptyList();
    }
    return metricInfos;
  }

  public static class NewRelicCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<NewRelicCVConfig, NewRelicCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<NewRelicCVConfig> updateOperations, NewRelicCVConfig newRelicCVConfig) {
      setCommonOperations(updateOperations, newRelicCVConfig);
      updateOperations.set(NewRelicCVConfigKeys.applicationName, newRelicCVConfig.getApplicationName())
          .set(NewRelicCVConfigKeys.applicationId, newRelicCVConfig.getApplicationId());
      if (newRelicCVConfig.getMetricInfos() != null) {
        updateOperations.set(NewRelicCVConfigKeys.metricInfos, newRelicCVConfig.getMetricInfos());
      }
    }
  }

  public void populateFromMetricDefinitions(
      List<NewRelicMetricDefinition> metricDefinitions, CVMonitoringCategory category) {
    if (this.metricInfos == null) {
      this.metricInfos = new ArrayList<>();
    }
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.NEW_RELIC)
                                .projectIdentifier(getProjectIdentifier())
                                .orgIdentifier(getOrgIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .category(category)
                                .build();

    metricDefinitions.stream().filter(md -> md.getGroupName().equals(getGroupName())).forEach(md -> {
      NewRelicMetricInfo info =
          NewRelicMetricInfo.builder()
              .identifier(md.getIdentifier())
              .metricName(md.getMetricName())
              .nrql(md.getNrql())
              .responseMapping(md.getResponseMapping())
              .sli(SLIMetricTransformer.transformDTOtoEntity(md.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(md.getAnalysis()))
              .deploymentVerification(DevelopmentVerificationTransformer.transformDTOtoEntity(md.getAnalysis()))
              .metricType(md.getRiskProfile().getMetricType())
              .build();
      this.metricInfos.add(info);
      Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
          info.getMetricName(), info.getMetricType(), md.getRiskProfile().getThresholdTypes());

      metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                  .thresholds(new ArrayList<>(thresholds))
                                  .type(info.getMetricType())
                                  .name(info.getMetricName())
                                  .identifier(info.getIdentifier())
                                  .included(true)
                                  .build());
    });

    this.setMetricPack(metricPack);
  }

  public void addMetricThresholds(
      Set<TimeSeriesMetricPackDTO> timeSeriesMetricPacks, List<NewRelicMetricDefinition> metricDefinitions) {
    if (isEmpty(timeSeriesMetricPacks)) {
      return;
    }
    Map<String, HealthSourceMetricDefinition> mapOfMetricDefinitions =
        emptyIfNull(metricDefinitions)
            .stream()
            .collect(Collectors.toMap(NewRelicMetricDefinition::getMetricName, metricDefinition -> metricDefinition));
    getMetricPack().getMetrics().forEach(metric -> {
      timeSeriesMetricPacks.forEach(timeSeriesMetricPackDTO -> {
        if (!isEmpty(timeSeriesMetricPackDTO.getMetricThresholds())) {
          timeSeriesMetricPackDTO.getMetricThresholds()
              .stream()
              .filter(metricPackDTO -> metric.getName().equals(metricPackDTO.getMetricName()))
              .forEach(metricPackDTO -> metricPackDTO.getTimeSeriesThresholdCriteria().forEach(criteria -> {
                List<TimeSeriesThreshold> timeSeriesThresholds =
                    metric.getThresholds() != null ? metric.getThresholds() : new ArrayList<>();
                String metricName = metricPackDTO.getMetricName();
                TimeSeriesThreshold timeSeriesThreshold =
                    TimeSeriesThreshold.builder()
                        .accountId(getAccountId())
                        .projectIdentifier(getProjectIdentifier())
                        .dataSourceType(getType())
                        .metricType(metric.getType())
                        .metricIdentifier(metric.getIdentifier())
                        .metricName(metricName)
                        .action(metricPackDTO.getType().getTimeSeriesThresholdActionType())
                        .criteria(criteria)
                        .thresholdConfigType(ThresholdConfigType.USER_DEFINED)
                        .deviationType(getDeviationType(
                            mapOfMetricDefinitions, metricName, metric, timeSeriesMetricPackDTO.getIdentifier()))
                        .build();
                if (!MonitoredServiceConstants.CUSTOM_METRIC_PACK.equalsIgnoreCase(
                        timeSeriesMetricPackDTO.getIdentifier())) {
                  timeSeriesThreshold.setMetricGroupName(metricPackDTO.getGroupName());
                }
                timeSeriesThresholds.add(timeSeriesThreshold);
                metric.setThresholds(timeSeriesThresholds);
              }));
        }
      });
    });
  }

  @Value
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class NewRelicMetricInfo extends AnalysisInfo {
    String nrql;
    TimeSeriesMetricType metricType;
    MetricResponseMapping responseMapping;
  }
}
