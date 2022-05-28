/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthMetricDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;
import io.harness.cvng.core.validators.UniqueIdentifierCheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomHealthSourceMetricSpec extends MetricHealthSourceSpec {
  @UniqueIdentifierCheck List<CustomHealthMetricDefinition> metricDefinitions = new ArrayList<>();

  @Data
  @Builder
  public static class Key {
    String groupName;
    CVMonitoringCategory category;
    HealthSourceQueryType queryType;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    List<CustomHealthMetricCVConfig> existingDBCVConfigs =
        (List<CustomHealthMetricCVConfig>) (List<?>) existingCVConfigs;
    Map<Key, CustomHealthMetricCVConfig> existingConfigs = new HashMap<>();
    existingDBCVConfigs.forEach(config
        -> existingConfigs.put(Key.builder()
                                   .groupName(config.getGroupName())
                                   .category(config.getCategory())
                                   .queryType(config.getQueryType())
                                   .build(),
            config));

    Map<Key, CustomHealthMetricCVConfig> currentCVConfigs = getCVConfigs(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);
    Set<Key> deleted = Sets.difference(existingConfigs.keySet(), currentCVConfigs.keySet());
    Set<Key> added = Sets.difference(currentCVConfigs.keySet(), existingConfigs.keySet());
    Set<Key> updated = Sets.intersection(existingConfigs.keySet(), currentCVConfigs.keySet());

    List<CVConfig> updatedConfigs = updated.stream().map(currentCVConfigs::get).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid = updated.stream().map(existingConfigs::get).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigs.size(); i++) {
      updatedConfigs.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(existingConfigs::get).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(currentCVConfigs::get).collect(Collectors.toList()))
        .build();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_METRIC;
  }

  public Map<Key, CustomHealthMetricCVConfig> getCVConfigs(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name) {
    Map<Key, CustomHealthMetricCVConfig> cvConfigMap = new HashMap<>();
    metricDefinitions.forEach(metricDefinition -> {
      CustomHealthRequestDefinition customHealthDefinition = metricDefinition.getRequestDefinition();
      String groupName = metricDefinition.getGroupName();
      RiskProfile riskProfile = metricDefinition.getRiskProfile();

      if (riskProfile == null || riskProfile.getCategory() == null) {
        return;
      }

      Key cvConfigKey = Key.builder()
                            .category(riskProfile.getCategory())
                            .groupName(groupName)
                            .queryType(metricDefinition.getQueryType())
                            .build();

      CustomHealthMetricCVConfig existingCvConfig = cvConfigMap.get(cvConfigKey);
      List<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition> cvConfigMetricDefinitions =
          existingCvConfig != null && isNotEmpty(existingCvConfig.getMetricInfos()) ? existingCvConfig.getMetricInfos()
                                                                                    : new ArrayList<>();

      MetricResponseMapping metricResponseMapping = metricDefinition.getMetricResponseMapping();
      cvConfigMetricDefinitions.add(
          CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
              .metricName(metricDefinition.getMetricName())
              .metricType(riskProfile.getMetricType())
              .identifier(metricDefinition.getIdentifier())
              .requestDefinition(CustomHealthRequestDefinition.builder()
                                     .endTimeInfo(customHealthDefinition.getEndTimeInfo())
                                     .startTimeInfo(customHealthDefinition.getStartTimeInfo())
                                     .method(customHealthDefinition.getMethod())
                                     .requestBody(customHealthDefinition.getRequestBody())
                                     .urlPath(customHealthDefinition.getUrlPath())
                                     .build())
              .metricResponseMapping(metricResponseMapping)
              .sli(SLIMetricTransformer.transformDTOtoEntity(metricDefinition.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(metricDefinition.getAnalysis()))
              .deploymentVerification(
                  DevelopmentVerificationTransformer.transformDTOtoEntity(metricDefinition.getAnalysis()))
              .build());

      CustomHealthMetricCVConfig mappedCVConfig = CustomHealthMetricCVConfig.builder()
                                                      .groupName(groupName)
                                                      .metricDefinitions(cvConfigMetricDefinitions)
                                                      .queryType(metricDefinition.getQueryType())
                                                      .accountId(accountId)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .identifier(identifier)
                                                      .category(riskProfile.getCategory())
                                                      .connectorIdentifier(getConnectorRef())
                                                      .monitoringSourceName(name)
                                                      .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                      .build();
      mappedCVConfig.setMetricPack(mappedCVConfig.generateMetricPack(
          metricDefinition.getIdentifier(), metricDefinition.getMetricName(), metricDefinition.getRiskProfile()));
      cvConfigMap.put(cvConfigKey, mappedCVConfig);
    });

    return cvConfigMap;
  }
}
