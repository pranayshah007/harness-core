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
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
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
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SplunkHealthSource", description = "This is the Splunk Log Health Source spec entity defined in Harness")
public class SplunkHealthSourceSpec extends HealthSourceSpec {
  @NotNull String feature;
  @NotNull @NotEmpty @Valid List<SplunkHealthSourceQueryDTO> queries;

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Builder
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SplunkHealthSourceQueryDTO {
    @NotNull String name;
    @NotNull String query;
    @NotNull String serviceInstanceIdentifier;
  }

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class Key {
    String monitoredServiceIdentifier;
    String queryName;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.SPLUNK;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<Key, SplunkCVConfig> existingConfigMap = getExistingCVConfigMap(existingCVConfigs);
    Map<Key, SplunkCVConfig> currentConfigMap = getCurrentCVConfigMap(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);

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
    Set<String> uniqueQueries = new HashSet<>();
    queries.forEach(query -> {
      if (uniqueQueries.contains(query.getQuery())) {
        throw new InvalidRequestException(String.format("Duplicate query present for query named: ", query.getName()));
      }
      uniqueQueries.add(query.getQuery());
    });
  }

  private Map<Key, SplunkCVConfig> getExistingCVConfigMap(List<CVConfig> existingCVConfigs) {
    return ((List<SplunkCVConfig>) (List<?>) existingCVConfigs)
        .stream()
        .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
  }

  private Key getKeyFromCVConfig(@NotNull SplunkCVConfig cvConfig) {
    return Key.builder()
        .monitoredServiceIdentifier(cvConfig.getMonitoredServiceIdentifier())
        .queryName(cvConfig.getQueryName())
        .build();
  }

  private Map<Key, SplunkCVConfig> getCurrentCVConfigMap(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name) {
    return queries.stream()
        .map(queryDTO
            -> SplunkCVConfig.builder()
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
                   .category(CVMonitoringCategory.ERRORS)
                   .monitoredServiceIdentifier(monitoredServiceIdentifier)
                   .build())
        .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
  }
}
