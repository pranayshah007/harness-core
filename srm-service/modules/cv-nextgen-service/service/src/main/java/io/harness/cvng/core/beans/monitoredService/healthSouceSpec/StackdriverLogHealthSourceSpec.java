/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "StackdriverLogHealthSource",
    description = "This is the StackDriver Log Health Source spec entity defined in Harness")
public class StackdriverLogHealthSourceSpec extends HealthSourceSpec {
  @NotNull String feature;
  @NotNull @NotEmpty @Valid List<StackdriverLogHealthSourceQueryDTO> queries;

  @Data
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class StackdriverLogHealthSourceQueryDTO {
    @NotNull String name;
    @NotNull String query;
    @NotNull String messageIdentifier;
    @NotNull String serviceInstanceIdentifier;
  }

  @Value
  @Builder
  private static class Key {
    String monitoredServiceIdentifier;
    String queryName;
  }

  @Override
  public CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name,
      List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<Key, StackdriverLogCVConfig> existingConfigMap = getExistingCVConfigMap(existingCVConfigs);
    Map<Key, StackdriverLogCVConfig> currentConfigMap = getCurrentCVConfigMap(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);

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
  public void validate() {
    Set<String> uniqueQueryNames = new HashSet<>();
    queries.forEach(query -> {
      if (uniqueQueryNames.contains(query.getName())) {
        throw new InvalidRequestException(String.format("Duplicate query name present %s", query.getName()));
      }
      uniqueQueryNames.add(query.getName());
    });
  }

  private Map<Key, StackdriverLogCVConfig> getExistingCVConfigMap(List<CVConfig> existingCVConfigs) {
    Map<Key, StackdriverLogCVConfig> existingConfigMap = new HashMap<>();
    List<StackdriverLogCVConfig> existingCVConfig = (List<StackdriverLogCVConfig>) (List<?>) existingCVConfigs;
    for (StackdriverLogCVConfig stackdriverLogCVConfig : existingCVConfig) {
      existingConfigMap.put(getKeyFromCVConfig(stackdriverLogCVConfig), stackdriverLogCVConfig);
    }
    return existingConfigMap;
  }

  private Map<Key, StackdriverLogCVConfig> getCurrentCVConfigMap(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name) {
    List<StackdriverLogCVConfig> cvConfigsFromThisObj = toCVConfigs(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);
    Map<Key, StackdriverLogCVConfig> currentCVConfigsMap = new HashMap<>();
    for (StackdriverLogCVConfig stackdriverLogCVConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromCVConfig(stackdriverLogCVConfig), stackdriverLogCVConfig);
    }
    return currentCVConfigsMap;
  }

  private Key getKeyFromCVConfig(StackdriverLogCVConfig stackdriverLogCVConfig) {
    return Key.builder()
        .monitoredServiceIdentifier(stackdriverLogCVConfig.getMonitoredServiceIdentifier())
        .queryName(stackdriverLogCVConfig.getQueryName())
        .build();
  }

  private List<StackdriverLogCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name) {
    List<StackdriverLogCVConfig> cvConfigs = new ArrayList<>();
    queries.forEach(queryDTO -> {
      StackdriverLogCVConfig stackdriverLogCVConfig =
          StackdriverLogCVConfig.builder()
              .accountId(accountId)
              .orgIdentifier(orgIdentifier)
              .projectIdentifier(projectIdentifier)
              .identifier(identifier)
              .connectorIdentifier(getConnectorRef())
              .monitoringSourceName(name)
              .productName(feature)
              .queryName(queryDTO.getName())
              .query(queryDTO.getQuery())
              .serviceInstanceIdentifier(queryDTO.getServiceInstanceIdentifier())
              .messageIdentifier(queryDTO.getMessageIdentifier())
              .category(CVMonitoringCategory.ERRORS)
              .monitoredServiceIdentifier(monitoredServiceIdentifier)
              .build();
      cvConfigs.add(stackdriverLogCVConfig);
    });
    return cvConfigs;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.STACKDRIVER_LOG;
  }
}
