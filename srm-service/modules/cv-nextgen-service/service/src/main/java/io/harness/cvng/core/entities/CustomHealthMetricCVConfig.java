/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.AnalysisInfoUtility;
import io.harness.exception.InvalidRequestException;

import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "CustomHealthMetricCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomHealthMetricCVConfig extends MetricCVConfig<CustomHealthCVConfigMetricDefinition> {
  String groupName;
  HealthSourceQueryType queryType;
  List<CustomHealthCVConfigMetricDefinition> metricDefinitions;

  @Override
  public boolean isSLIEnabled() {
    if (!queryType.equals(HealthSourceQueryType.SERVICE_BASED)) {
      return false;
    }
    return AnalysisInfoUtility.anySLIEnabled(metricDefinitions);
  }

  @Override
  public boolean isLiveMonitoringEnabled() {
    if (!queryType.equals(HealthSourceQueryType.SERVICE_BASED)) {
      return false;
    }
    return AnalysisInfoUtility.anyLiveMonitoringEnabled(metricDefinitions);
  }

  @Override
  public boolean isDeploymentVerificationEnabled() {
    if (!queryType.equals(HealthSourceQueryType.HOST_BASED)) {
      return false;
    }
    return AnalysisInfoUtility.anyDeploymentVerificationEnabled(metricDefinitions);
  }

  @Override
  public Optional<String> maybeGetGroupName() {
    return Optional.of(groupName);
  }

  @Override
  public List<CustomHealthCVConfigMetricDefinition> getMetricInfos() {
    if (metricDefinitions == null) {
      return Collections.emptyList();
    }
    return metricDefinitions;
  }

  @Override
  public void setMetricInfos(List<CustomHealthCVConfigMetricDefinition> metricInfos) {
    this.metricDefinitions = metricInfos;
  }

  @Data
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class CustomHealthCVConfigMetricDefinition extends AnalysisInfo {
    CustomHealthRequestDefinition requestDefinition;
    MetricResponseMapping metricResponseMapping;
    TimeSeriesMetricType metricType;
  }

  @Override
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_METRIC;
  }

  @Override
  protected void validateParams() {
    checkNotNull(groupName, generateErrorMessageFromParam(CustomHealthMetricCVConfigKeys.groupName));
    checkNotNull(metricDefinitions, generateErrorMessageFromParam(CustomHealthMetricCVConfigKeys.metricDefinitions));
    checkNotNull(queryType, generateErrorMessageFromParam(CustomHealthMetricCVConfigKeys.queryType));
    Set<String> uniqueMetricDefinitionsNames = new HashSet<>();

    for (int metricDefinitionIndex = 0; metricDefinitionIndex < metricDefinitions.size(); metricDefinitionIndex++) {
      CustomHealthCVConfigMetricDefinition metricDefinition = metricDefinitions.get(metricDefinitionIndex);
      CustomHealthRequestDefinition requestDefinition = metricDefinition.getRequestDefinition();

      checkNotNull(metricDefinition.getMetricName(),
          generateErrorMessageFromParam("metricName") + " for index " + metricDefinitionIndex);
      requestDefinition.validateParams();

      AnalysisInfo.SLI sliDTO = metricDefinition.getSli();
      AnalysisInfo.DeploymentVerification deploymentVerification = metricDefinition.getDeploymentVerification();
      AnalysisInfo.LiveMonitoring liveMonitoring = metricDefinition.getLiveMonitoring();

      switch (queryType) {
        case HOST_BASED:
          if ((liveMonitoring != null && liveMonitoring.enabled) || (sliDTO != null && sliDTO.enabled)) {
            throw new InvalidRequestException("Host based queries can only be used for continuous verification.");
          }
          break;
        case SERVICE_BASED:
          if (deploymentVerification != null && deploymentVerification.enabled) {
            throw new InvalidRequestException(
                "Service based queries can only be used for live monitoring and service level indicators.");
          }
          break;
        default:
          throw new InvalidRequestException(
              String.format("Invalid query type %s provided, must be SERVICE_BASED or HOST_BASED", queryType));
      }

      String uniqueKey = getMetricAndGroupNameKey(groupName, metricDefinition.getMetricName());
      if (uniqueMetricDefinitionsNames.contains(uniqueKey)) {
        throw new InvalidRequestException(
            String.format("Duplicate group name (%s) and metric name (%s) combination present.", groupName,
                metricDefinition.getMetricName()));
      }
      uniqueMetricDefinitionsNames.add(uniqueKey);
    }
  }

  public MetricPack generateMetricPack(
      MetricPack metricPack, String identifier, String metricName, RiskProfile riskProfile) {
    Set<TimeSeriesThreshold> timeSeriesThresholds = getThresholdsToCreateOnSaveForCustomProviders(
        metricName, riskProfile.getMetricType(), riskProfile.getThresholdTypes());
    if (metricPack == null) {
      metricPack = MetricPack.builder()
                       .category(riskProfile.getCategory())
                       .accountId(getAccountId())
                       .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                       .projectIdentifier(getProjectIdentifier())
                       .orgIdentifier(getOrgIdentifier())
                       .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                       .build();
    }
    metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                .thresholds(new ArrayList<>(timeSeriesThresholds))
                                .type(riskProfile.getMetricType())
                                .name(metricName)
                                .identifier(identifier)
                                .included(true)
                                .build());

    return metricPack;
  }

  public static class CustomHealthMetricCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<CustomHealthMetricCVConfig, CustomHealthMetricCVConfig> {
    @Override
    public void setUpdateOperations(UpdateOperations<CustomHealthMetricCVConfig> updateOperations,
        CustomHealthMetricCVConfig customHealthCVConfig) {
      setCommonOperations(updateOperations, customHealthCVConfig);
      updateOperations.set(CustomHealthMetricCVConfigKeys.groupName, customHealthCVConfig.getGroupName())
          .set(CustomHealthMetricCVConfigKeys.metricDefinitions, customHealthCVConfig.getMetricInfos())
          .set(CustomHealthMetricCVConfigKeys.queryType, customHealthCVConfig.getQueryType());
    }
  }

  private String getMetricAndGroupNameKey(String groupName, String metricName) {
    return String.format("%s%s", groupName, metricName);
  }
}
