/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import static io.harness.cvng.beans.DataSourceType.ERROR_TRACKING;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;

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
public class ErrorTrackingHealthSourceSpec extends HealthSourceSpec {
  @NotNull String feature;

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class Key {
    String monitoredServiceIdentifier;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<ErrorTrackingHealthSourceSpec.Key, ErrorTrackingCVConfig> existingConfigMap =
        getExistingCVConfigMap(existingCVConfigs);
    Map<ErrorTrackingHealthSourceSpec.Key, ErrorTrackingCVConfig> currentConfigMap = getCurrentCVConfigMap(accountId,
        orgIdentifier, projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);

    Set<ErrorTrackingHealthSourceSpec.Key> deleted =
        Sets.difference(existingConfigMap.keySet(), currentConfigMap.keySet());
    Set<ErrorTrackingHealthSourceSpec.Key> added =
        Sets.difference(currentConfigMap.keySet(), existingConfigMap.keySet());
    Set<ErrorTrackingHealthSourceSpec.Key> updated =
        Sets.intersection(existingConfigMap.keySet(), currentConfigMap.keySet());
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
    return ERROR_TRACKING;
  }

  private List<ErrorTrackingCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name) {
    List<ErrorTrackingCVConfig> cvConfigs = new ArrayList<>();
    ErrorTrackingCVConfig errorTrackingCVConfig = ErrorTrackingCVConfig.builder()
                                                      .accountId(accountId)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .identifier(identifier)
                                                      .connectorIdentifier(getConnectorRef())
                                                      .monitoringSourceName(name)
                                                      .productName(ERROR_TRACKING.toString())
                                                      .envIdentifier(environmentRef)
                                                      .serviceIdentifier(serviceRef)
                                                      .queryName(ERROR_TRACKING.toString())
                                                      .query(serviceRef + ":" + environmentRef)
                                                      .category(CVMonitoringCategory.ERRORS)
                                                      .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                      .build();
    cvConfigs.add(errorTrackingCVConfig);
    return cvConfigs;
  }

  private Map<ErrorTrackingHealthSourceSpec.Key, ErrorTrackingCVConfig> getExistingCVConfigMap(
      List<CVConfig> existingCVConfigs) {
    Map<ErrorTrackingHealthSourceSpec.Key, ErrorTrackingCVConfig> existingConfigMap = new HashMap<>();
    List<ErrorTrackingCVConfig> existingCVConfig = (List<ErrorTrackingCVConfig>) (List<?>) existingCVConfigs;
    for (ErrorTrackingCVConfig cvConfig : existingCVConfig) {
      existingConfigMap.put(getKeyFromCVConfig(cvConfig), cvConfig);
    }
    return existingConfigMap;
  }

  private Map<ErrorTrackingHealthSourceSpec.Key, ErrorTrackingCVConfig> getCurrentCVConfigMap(String accountId,
      String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef,
      String monitoredServiceIdentifier, String identifier, String name) {
    List<ErrorTrackingCVConfig> cvConfigsFromThisObj = toCVConfigs(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);
    Map<ErrorTrackingHealthSourceSpec.Key, ErrorTrackingCVConfig> currentCVConfigsMap = new HashMap<>();
    for (ErrorTrackingCVConfig cvConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromCVConfig(cvConfig), cvConfig);
    }
    return currentCVConfigsMap;
  }

  private ErrorTrackingHealthSourceSpec.Key getKeyFromCVConfig(@NotNull ErrorTrackingCVConfig cvConfig) {
    return Key.builder().monitoredServiceIdentifier(cvConfig.getMonitoredServiceIdentifier()).build();
  }
}
