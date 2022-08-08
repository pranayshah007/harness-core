/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomHealthSourceLogSpec extends HealthSourceSpec {
  List<CustomHealthLogDefinition> logDefinitions = new ArrayList<>();

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    List<CustomHealthLogCVConfig> existingDBCVConfigs = (List<CustomHealthLogCVConfig>) (List<?>) existingCVConfigs;
    Map<String, CustomHealthLogCVConfig> existingConfigs = new HashMap<>();
    existingDBCVConfigs.forEach(config -> existingConfigs.put(config.getQueryName(), config));

    Map<String, CustomHealthLogCVConfig> currentCVConfigs = getCVConfigs(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);

    Set<String> deleted = Sets.difference(existingConfigs.keySet(), currentCVConfigs.keySet());
    Set<String> added = Sets.difference(currentCVConfigs.keySet(), existingConfigs.keySet());
    Set<String> updated = Sets.intersection(existingConfigs.keySet(), currentCVConfigs.keySet());

    List<CVConfig> updatedConfigs = updated.stream().map(currentCVConfigs::get).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid = updated.stream().map(existingConfigs::get).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigs.size(); i++) {
      updatedConfigs.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(existingConfigs::get).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(currentCVConfigs::get).collect(Collectors.toList()))
        .build();
  }

  public Map<String, CustomHealthLogCVConfig> getCVConfigs(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name) {
    Map<String, CustomHealthLogCVConfig> cvConfigMap = new HashMap<>();
    logDefinitions.forEach(logDefinition -> {
      String queryName = logDefinition.getQueryName();

      String cvConfigKey = queryName;
      CustomHealthLogCVConfig existingCvConfig = cvConfigMap.get(cvConfigKey);

      if (existingCvConfig != null) {
        return;
      }

      CustomHealthRequestDefinition requestDefinition = logDefinition.getRequestDefinition();
      CustomHealthLogCVConfig mappedCVConfig =
          CustomHealthLogCVConfig.builder()
              .accountId(accountId)
              .orgIdentifier(orgIdentifier)
              .projectIdentifier(projectIdentifier)
              .monitoringSourceName(name)
              .connectorIdentifier(getConnectorRef())
              .identifier(identifier)
              .monitoredServiceIdentifier(monitoredServiceIdentifier)
              .logMessageJsonPath(logDefinition.getLogMessageJsonPath())
              .timestampJsonPath(logDefinition.getTimestampJsonPath())
              .serviceInstanceJsonPath(logDefinition.getServiceInstanceJsonPath())
              .queryName(queryName)
              .query(requestDefinition.getUrlPath())
              .category(CVMonitoringCategory.ERRORS)
              .requestDefinition(CustomHealthRequestDefinition.builder()
                                     .requestBody(requestDefinition.getRequestBody())
                                     .method(requestDefinition.getMethod())
                                     .startTimeInfo(requestDefinition.getStartTimeInfo())
                                     .endTimeInfo(requestDefinition.getEndTimeInfo())
                                     .urlPath(requestDefinition.getUrlPath())
                                     .build())
              .build();

      cvConfigMap.put(cvConfigKey, mappedCVConfig);
    });

    return cvConfigMap;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_LOG;
  }
}
