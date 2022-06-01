/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.StackdriverDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.validators.UniqueIdentifierCheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StackdriverMetricHealthSourceSpec extends MetricHealthSourceSpec {
  @UniqueIdentifierCheck @Valid private List<StackdriverDefinition> metricDefinitions;

  public List<StackdriverDefinition> getMetricDefinitions() {
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
                && StringUtils.isEmpty(metricDefinition.getServiceInstanceField())),
            "Service instance field shouldn't be empty for Deployment verification"));
  }

  @Override
  public CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name,
      List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    List<StackdriverCVConfig> cvConfigsFromThisObj = toCVConfigs(accountId, orgIdentifier, projectIdentifier,
        environmentRef, serviceRef, monitoredServiceIdentifier, identifier, name);
    Map<Key, StackdriverCVConfig> existingConfigMap = new HashMap<>();

    List<StackdriverCVConfig> existingSDCVConfigs = (List<StackdriverCVConfig>) (List<?>) existingCVConfigs;

    for (StackdriverCVConfig stackdriverCVConfig : existingSDCVConfigs) {
      existingConfigMap.put(getKeyFromConfig(stackdriverCVConfig), stackdriverCVConfig);
    }

    Map<Key, StackdriverCVConfig> currentCVConfigsMap = new HashMap<>();
    for (StackdriverCVConfig stackdriverCVConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromConfig(stackdriverCVConfig), stackdriverCVConfig);
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
    return CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(key -> existingConfigMap.get(key)).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(key -> currentCVConfigsMap.get(key)).collect(Collectors.toList()))
        .build();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.STACKDRIVER;
  }

  private List<StackdriverCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name) {
    // group things under same service_env_category_dashboard into one config
    Map<Key, List<StackdriverDefinition>> keyToDefinitionMap = new HashMap<>();

    metricDefinitions.forEach(definition -> {
      Key key = Key.builder()
                    .category(definition.getRiskProfile().getCategory())
                    .dashboardName(definition.getDashboardName())
                    .build();
      if (!keyToDefinitionMap.containsKey(key)) {
        keyToDefinitionMap.put(key, new ArrayList<>());
      }
      keyToDefinitionMap.get(key).add(definition);
    });

    List<StackdriverCVConfig> cvConfigs = new ArrayList<>();

    keyToDefinitionMap.forEach((key, stackdriverDefinitions) -> {
      StackdriverCVConfig cvConfig = StackdriverCVConfig.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .identifier(identifier)
                                         .connectorIdentifier(getConnectorRef())
                                         .monitoringSourceName(name)
                                         .category(key.getCategory())
                                         .dashboardName(key.getDashboardName())
                                         .dashboardPath(stackdriverDefinitions.get(0).getDashboardPath())
                                         .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                         .build();
      cvConfig.fromStackdriverDefinitions(stackdriverDefinitions, key.getCategory());
      cvConfigs.add(cvConfig);
    });

    return cvConfigs;
  }

  private Key getKeyFromConfig(StackdriverCVConfig cvConfig) {
    return Key.builder()
        .category(cvConfig.getMetricPack().getCategory())
        .dashboardName(cvConfig.getDashboardName())
        .build();
  }

  @Value
  @Builder
  private static class Key {
    CVMonitoringCategory category;
    String dashboardName;
  }
}
