/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.cvng.beans.DeviationType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec.AppDMetricDefinitions;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.MetricInfo;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
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
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("APP_DYNAMICS")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "AppDynamicsCVConfigKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppDynamicsCVConfig extends MetricCVConfig<MetricInfo> {
  private String applicationName;
  private String tierName;
  private String groupName;
  private List<MetricInfo> metricInfos;

  @Override
  public DataSourceType getType() {
    return DataSourceType.APP_DYNAMICS;
  }

  @Override
  @JsonIgnore
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  protected void validateParams() {
    checkNotNull(applicationName, generateErrorMessageFromParam(AppDynamicsCVConfigKeys.applicationName));
    checkNotNull(tierName, generateErrorMessageFromParam(AppDynamicsCVConfigKeys.tierName));
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
  public List<MetricInfo> getMetricInfos() {
    populateCompleteMetricPaths();
    if (metricInfos == null) {
      return Collections.emptyList();
    }
    return metricInfos;
  }

  @Override
  public void setMetricInfos(List<MetricInfo> metricInfos) {
    this.metricInfos = metricInfos;
    populateCompleteMetricPaths();
  }

  public void setTierName(String tierName) {
    this.tierName = tierName;
    populateCompleteMetricPaths();
  }

  public static class AppDynamicsCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<AppDynamicsCVConfig, AppDynamicsCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<AppDynamicsCVConfig> updateOperations, AppDynamicsCVConfig appDynamicsCVConfig) {
      setCommonOperations(updateOperations, appDynamicsCVConfig);
      updateOperations.set(AppDynamicsCVConfigKeys.applicationName, appDynamicsCVConfig.getApplicationName())
          .set(AppDynamicsCVConfigKeys.tierName, appDynamicsCVConfig.getTierName());
      if (appDynamicsCVConfig.getMetricInfos() != null) {
        updateOperations.set(AppDynamicsCVConfigKeys.metricInfos, appDynamicsCVConfig.getMetricInfos());
      }
    }
  }

  public void populateFromMetricDefinitions(
      List<AppDMetricDefinitions> metricDefinitions, CVMonitoringCategory category) {
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.APP_DYNAMICS)
                                .projectIdentifier(getProjectIdentifier())
                                .orgIdentifier(getOrgIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .category(category)
                                .build();
    if (this.metricInfos == null) {
      this.metricInfos = new ArrayList<>();
    }

    metricDefinitions.stream().filter(md -> md.getGroupName().equals(getGroupName())).forEach(md -> {
      MetricInfo metricInfo =
          MetricInfo.builder()
              .identifier(md.getIdentifier())
              .metricName(md.getMetricName())
              .baseFolder(md.getBaseFolder())
              .metricPath(md.getMetricPath())
              .completeServiceInstanceMetricPath(md.getCompleteServiceInstanceMetricPath())
              .completeMetricPath(md.getCompleteMetricPath())
              .sli(SLIMetricTransformer.transformDTOtoEntity(md.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(md.getAnalysis()))
              .deploymentVerification(DevelopmentVerificationTransformer.transformDTOtoEntity(md.getAnalysis()))
              .metricType(md.getRiskProfile().getMetricType())
              .build();
      this.metricInfos.add(metricInfo);
      Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
          metricInfo.getMetricName(), metricInfo.getMetricType(), md.getRiskProfile().getThresholdTypes());
      metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                  .thresholds(new ArrayList<>(thresholds))
                                  .type(metricInfo.getMetricType())
                                  .name(metricInfo.getMetricName())
                                  .identifier(metricInfo.getIdentifier())
                                  .included(true)
                                  .build());
    });
    this.setMetricPack(metricPack);
    populateCompleteMetricPaths();
  }

  public void addMetricThresholds(
      Set<TimeSeriesMetricPackDTO> timeSeriesMetricPacks, List<AppDMetricDefinitions> metricDefinitions) {
    if (isEmpty(timeSeriesMetricPacks)) {
      return;
    }
    Map<String, AppDMetricDefinitions> mapOfMetricDefinitions = metricDefinitions.stream().collect(
        Collectors.toMap(AppDMetricDefinitions::getMetricName, metricDefinition -> metricDefinition));
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
                List<TimeSeriesThresholdType> thresholdTypes =
                    mapOfMetricDefinitions.get(metricName).getRiskProfile().getThresholdTypes();
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
                        .deviationType(DeviationType.getDeviationType(thresholdTypes))
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

  @Data
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @FieldNameConstants(innerTypeName = "AppDynamicsMetricInfoKeys")
  public static class MetricInfo extends AnalysisInfo {
    @Deprecated String baseFolder;
    @Deprecated String metricPath;
    String completeMetricPath;
    String completeServiceInstanceMetricPath;
    TimeSeriesMetricType metricType;
  }

  public void populateCompleteMetricPaths() {
    CollectionUtils.emptyIfNull(metricInfos).forEach(metricInfo -> populateCompleteMetricPaths(metricInfo));
  }

  // TODO: remove after UI change and data migration to completeMetricPaths.
  private void populateCompleteMetricPaths(MetricInfo metricInfo) {
    if (StringUtils.isEmpty(tierName)) {
      // If tier is not yet set, skip
      return;
    }
    if (StringUtils.isEmpty(metricInfo.getCompleteMetricPath())) {
      metricInfo.setCompleteMetricPath(getCompleteMetricPath(metricInfo.getBaseFolder(), metricInfo.getMetricPath()));
    }
    if (StringUtils.isEmpty(metricInfo.getCompleteServiceInstanceMetricPath())
        && StringUtils.isNotEmpty(metricInfo.getDeploymentVerification().getServiceInstanceMetricPath())) {
      metricInfo.setCompleteServiceInstanceMetricPath(getCompleteMetricPath(
          metricInfo.getBaseFolder(), metricInfo.getDeploymentVerification().getServiceInstanceMetricPath()));
    }
  }

  private String getCompleteMetricPath(String basePath, String metricPath) {
    return basePath + "|" + tierName + "|" + metricPath;
  }
}
