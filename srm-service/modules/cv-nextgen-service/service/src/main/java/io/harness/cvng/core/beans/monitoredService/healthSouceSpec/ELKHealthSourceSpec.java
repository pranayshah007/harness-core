/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.ELKCVConfig;
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
import javax.validation.constraints.NotEmpty;
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
@Schema(name = "ELKHealthSource", description = "This is the ELK Log Health Source spec entity defined in Harness",
    hidden = true)
public class ELKHealthSourceSpec extends HealthSourceSpec {
  @NotNull @NotEmpty String feature;

  @NotNull @NotEmpty @Valid List<ELKHealthSourceQueryDTO> queries;

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Builder
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ELKHealthSourceQueryDTO {
    @NotNull @NotEmpty String name;
    @NotNull String query;
    @NotNull @NotEmpty String index;
    @NotNull @NotEmpty String serviceInstanceIdentifier;
    @NotNull @NotEmpty String timeStampIdentifier;
    @NotNull @NotEmpty String timeStampFormat;
    @NotNull @NotEmpty String messageIdentifier;
  }

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class Key {
    String monitoredServiceIdentifier;
    String queryName;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<ELKHealthSourceSpec.Key, ELKCVConfig> existingConfigMap = getExistingCVConfigMap(existingCVConfigs);
    Map<ELKHealthSourceSpec.Key, ELKCVConfig> currentConfigMap = getCurrentCVConfigMap(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);

    Set<ELKHealthSourceSpec.Key> deleted = Sets.difference(existingConfigMap.keySet(), currentConfigMap.keySet());
    Set<ELKHealthSourceSpec.Key> added = Sets.difference(currentConfigMap.keySet(), existingConfigMap.keySet());
    Set<ELKHealthSourceSpec.Key> updated = Sets.intersection(existingConfigMap.keySet(), currentConfigMap.keySet());
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
  public DataSourceType getType() {
    return DataSourceType.ELASTICSEARCH;
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

  private Map<ELKHealthSourceSpec.Key, ELKCVConfig> getExistingCVConfigMap(List<CVConfig> existingCVConfigs) {
    Map<ELKHealthSourceSpec.Key, ELKCVConfig> existingConfigMap = new HashMap<>();
    List<ELKCVConfig> existingCVConfig = (List<ELKCVConfig>) (List<?>) existingCVConfigs;
    for (ELKCVConfig elkCVConfig : existingCVConfig) {
      existingConfigMap.put(getKeyFromCVConfig(elkCVConfig), elkCVConfig);
    }
    return existingConfigMap;
  }

  private Map<ELKHealthSourceSpec.Key, ELKCVConfig> getCurrentCVConfigMap(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name) {
    List<ELKCVConfig> cvConfigsFromThisObj = toCVConfigs(accountId, orgIdentifier, projectIdentifier, environmentRef,
        serviceRef, monitoredServiceIdentifier, identifier, name);
    Map<ELKHealthSourceSpec.Key, ELKCVConfig> currentCVConfigsMap = new HashMap<>();
    for (ELKCVConfig elkCVConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromCVConfig(elkCVConfig), elkCVConfig);
    }
    return currentCVConfigsMap;
  }

  private ELKHealthSourceSpec.Key getKeyFromCVConfig(ELKCVConfig elkCVConfig) {
    return ELKHealthSourceSpec.Key.builder()
        .monitoredServiceIdentifier(elkCVConfig.getMonitoredServiceIdentifier())
        .queryName(elkCVConfig.getQueryName())
        .build();
  }

  private List<ELKCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name) {
    List<ELKCVConfig> cvConfigs = new ArrayList<>();
    queries.forEach(queryDTO -> {
      ELKCVConfig elkCVConfig = ELKCVConfig.builder()
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
                                    .index(queryDTO.getIndex())
                                    .timeStampIdentifier(queryDTO.getTimeStampIdentifier())
                                    .timeStampFormat(queryDTO.getTimeStampFormat())
                                    .messageIdentifier(queryDTO.getMessageIdentifier())
                                    .category(CVMonitoringCategory.ERRORS)
                                    .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                    .build();
      cvConfigs.add(elkCVConfig);
    });
    return cvConfigs;
  }
}
