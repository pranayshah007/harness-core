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
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SplunkMetricCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.models.VerificationType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplunkMetricHealthSourceSpec extends MetricHealthSourceSpec {
  @NotNull String feature;
  private List<SplunkMetricDefinition> metricDefinitions;

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @SuperBuilder
  @AllArgsConstructor
  public static class SplunkMetricDefinition extends HealthSourceMetricDefinition {
    @NotNull String groupName;
    @NotNull String query;
  }

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class Key {
    String groupName;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.SPLUNK_METRIC;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<Key, SplunkMetricCVConfig> existingConfigMap = getExistingCVConfigMap(existingCVConfigs);
    Map<Key, SplunkMetricCVConfig> currentConfigMap = getCurrentCVConfigMap(
        accountId, orgIdentifier, projectIdentifier, monitoredServiceIdentifier, identifier, name);

    Set<Key> deleted = Sets.difference(existingConfigMap.keySet(), currentConfigMap.keySet());
    Set<Key> added = Sets.difference(currentConfigMap.keySet(), existingConfigMap.keySet());
    Set<Key> updated = Sets.intersection(existingConfigMap.keySet(), currentConfigMap.keySet());
    List<CVConfig> updatedConfigs = updated.stream().map(currentConfigMap::get).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid = updated.stream().map(existingConfigMap::get).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigs.size(); i++) {
      updatedConfigs.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(existingConfigMap::get).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(currentConfigMap::get).collect(Collectors.toList()))
        .build();
  }

  @Override
  public void validate() {}

  private Map<Key, SplunkMetricCVConfig> getExistingCVConfigMap(List<CVConfig> existingCVConfigs) {
    return ((List<SplunkMetricCVConfig>) (List<?>) existingCVConfigs)
        .stream()
        .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
  }

  private Key getKeyFromCVConfig(@NotNull SplunkMetricCVConfig cvConfig) {
    return Key.builder().groupName(cvConfig.getGroupName()).build();
  }

  private Map<Key, SplunkMetricCVConfig> getCurrentCVConfigMap(String accountId, String orgIdentifier,
      String projectIdentifier, String monitoredServiceIdentifier, String identifier, String name) {
    Map<Key, List<SplunkMetricDefinition>> keySplunkMetricDefinitionMap = new HashMap<>();

    metricDefinitions.forEach(metricDefinition -> {
      Key key = Key.builder().groupName(metricDefinition.getGroupName()).build();
      List<SplunkMetricDefinition> splunkMetricDefinitions =
          keySplunkMetricDefinitionMap.getOrDefault(key, new ArrayList<>());
      splunkMetricDefinitions.add(metricDefinition);
      keySplunkMetricDefinitionMap.put(key, splunkMetricDefinitions);
    });
    Map<Key, SplunkMetricCVConfig> splunkMetricCVConfigs = new HashMap<>();
    keySplunkMetricDefinitionMap.forEach((key, metricDefinitionGroup) -> {
      SplunkMetricCVConfig splunkMetricCVConfig = SplunkMetricCVConfig.builder()
                                                      .groupName(key.getGroupName())
                                                      .accountId(accountId)
                                                      .verificationType(VerificationType.TIME_SERIES)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                      .identifier(identifier)
                                                      .category(CVMonitoringCategory.ERRORS)
                                                      .connectorIdentifier(connectorRef)
                                                      .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                      .monitoringSourceName(name)
                                                      .productName(feature)
                                                      .build();
      splunkMetricCVConfig.populateFromMetricDefinitions(metricDefinitionGroup, CVMonitoringCategory.ERRORS);
      splunkMetricCVConfigs.put(key, splunkMetricCVConfig);
    });

    return splunkMetricCVConfigs;
  }
}
