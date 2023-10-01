/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.validators.UniqueIdentifierCheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "AppDynamicsHealthSource",
    description = "This is the AppDynamics Metric Health Source spec entity defined in Harness")
public class AppDynamicsHealthSourceSpec extends MetricHealthSourceSpec {
  @NotNull String feature;
  @NotEmpty String applicationName;
  @NotEmpty String tierName;
  @Valid @UniqueIdentifierCheck List<AppDMetricDefinitions> metricDefinitions;
  public List<AppDMetricDefinitions> getMetricDefinitions() {
    if (metricDefinitions == null) {
      return Collections.emptyList();
    }
    return metricDefinitions;
  }
  @Override
  public void validate() {
    getMetricDefinitions().forEach(metricDefinition
        -> Preconditions.checkArgument(
            !(Objects.nonNull(metricDefinition.getAnalysis())
                && Objects.nonNull(metricDefinition.getAnalysis().getDeploymentVerification())
                && Objects.nonNull(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled())
                && metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()
                && (StringUtils.isEmpty(
                        metricDefinition.getAnalysis().getDeploymentVerification().getServiceInstanceMetricPath())
                    && StringUtils.isEmpty(metricDefinition.getCompleteServiceInstanceMetricPath()))),
            "Service metric path shouldn't be empty for Deployment Verification"));
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    List<AppDynamicsCVConfig> cvConfigsFromThisObj = toCVConfigs(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name, metricPackService);
    Map<Key, AppDynamicsCVConfig> existingConfigMap = new HashMap<>();
    List<AppDynamicsCVConfig> existingAppDCVConfig = (List<AppDynamicsCVConfig>) (List<?>) existingCVConfigs;
    for (AppDynamicsCVConfig appDynamicsCVConfig : existingAppDCVConfig) {
      existingConfigMap.put(getKeyFromCVConfig(appDynamicsCVConfig), appDynamicsCVConfig);
    }
    Map<Key, AppDynamicsCVConfig> currentCVConfigsMap = new HashMap<>();
    for (AppDynamicsCVConfig appDynamicsCVConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromCVConfig(appDynamicsCVConfig), appDynamicsCVConfig);
    }
    Set<Key> deleted = Sets.difference(existingConfigMap.keySet(), currentCVConfigsMap.keySet());
    Set<Key> added = Sets.difference(currentCVConfigsMap.keySet(), existingConfigMap.keySet());
    Set<Key> updated = Sets.intersection(existingConfigMap.keySet(), currentCVConfigsMap.keySet());
    List<CVConfig> updatedConfigs =
        updated.stream().map(key -> currentCVConfigsMap.get(key)).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid =
        updated.stream().map(key -> existingConfigMap.get(key)).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigs.size(); i++) {
      updatedConfigs.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(key -> existingConfigMap.get(key)).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(key -> currentCVConfigsMap.get(key)).collect(Collectors.toList()))
        .build();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.APP_DYNAMICS;
  }

  private List<AppDynamicsCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name,
      MetricPackService metricPackService) {
    List<AppDynamicsCVConfig> cvConfigs = new ArrayList<>();
    CollectionUtils.emptyIfNull(metricPacks)
        .stream()
        .filter(
            metricPack -> !metricPack.getIdentifier().equalsIgnoreCase(MonitoredServiceConstants.CUSTOM_METRIC_PACK))
        .forEach(metricPack -> {
          MetricPack metricPackFromDb =
              metricPack.toMetricPack(accountId, orgIdentifier, projectIdentifier, getType(), metricPackService);
          AppDynamicsCVConfig appDynamicsCVConfig = AppDynamicsCVConfig.builder()
                                                        .accountId(accountId)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .identifier(identifier)
                                                        .connectorIdentifier(getConnectorRef())
                                                        .monitoringSourceName(name)
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .productName(feature)
                                                        .applicationName(applicationName)
                                                        .tierName(tierName)
                                                        .metricPack(metricPackFromDb)
                                                        .category(metricPackFromDb.getCategory())
                                                        .build();
          cvConfigs.add(appDynamicsCVConfig);
        });
    cvConfigs.addAll(CollectionUtils.emptyIfNull(metricDefinitions)
                         .stream()
                         .collect(Collectors.groupingBy(MetricDefinitionKey::fromMetricDefinition))
                         .values()
                         .stream()
                         .map(mdList -> {
                           AppDynamicsCVConfig appDynamicsCVConfig =
                               AppDynamicsCVConfig.builder()
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .identifier(identifier)
                                   .connectorIdentifier(getConnectorRef())
                                   .monitoringSourceName(name)
                                   .productName(feature)
                                   .applicationName(applicationName)
                                   .tierName(tierName)
                                   .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                   .groupName(mdList.get(0).getGroupName())
                                   .category(mdList.get(0).getRiskProfile().getCategory())
                                   .build();
                           appDynamicsCVConfig.populateFromMetricDefinitions(
                               metricDefinitions, metricDefinitions.get(0).getRiskProfile().getCategory());
                           return appDynamicsCVConfig;
                         })
                         .collect(Collectors.toList()));
    cvConfigs.forEach(appDynamicsCVConfig -> appDynamicsCVConfig.addMetricThresholds(metricPacks, metricDefinitions));
    cvConfigs.stream()
        .filter(cvConfig -> CollectionUtils.isNotEmpty(cvConfig.getMetricInfos()))
        .flatMap(cvConfig -> cvConfig.getMetricInfos().stream())
        .forEach(metricInfo -> {
          if (metricInfo.getDeploymentVerification().isEnabled()) {
            if (StringUtils.isEmpty(metricInfo.getCompleteServiceInstanceMetricPath())) {
              throw new BadRequestException("ServiceInstanceMetricPath should be set for Deployment Verification");
            }
            Preconditions.checkArgument(
                metricInfo.getCompleteServiceInstanceMetricPath().contains("|Individual Nodes|*|"),
                "ServiceInstanceMetricPath should contain |Individual Nodes|*|");
          }
        });
    return cvConfigs;
  }

  private Key getKeyFromCVConfig(AppDynamicsCVConfig appDynamicsCVConfig) {
    return Key.builder()
        .appName(appDynamicsCVConfig.getApplicationName())
        .metricPack(appDynamicsCVConfig.getMetricPack())
        .tierName(appDynamicsCVConfig.getTierName())
        .groupName(appDynamicsCVConfig.getGroupName())
        .build();
  }

  @Data
  @SuperBuilder
  @NoArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class AppDMetricDefinitions extends HealthSourceMetricDefinition {
    String groupName;
    @Deprecated String baseFolder;
    @Deprecated String metricPath;
    String completeMetricPath;
    String completeServiceInstanceMetricPath;
  }

  @Value
  @Builder
  private static class Key {
    String appName;
    String tierName;
    MetricPack metricPack;
    String groupName;
  }

  @Value
  @Builder
  private static class MetricDefinitionKey {
    String groupName;
    CVMonitoringCategory category;

    public static MetricDefinitionKey fromMetricDefinition(AppDMetricDefinitions appDMetricDefinitions) {
      return MetricDefinitionKey.builder()
          .category(appDMetricDefinitions.getRiskProfile().getCategory())
          .groupName(appDMetricDefinitions.getGroupName())
          .build();
    }
  }
}
