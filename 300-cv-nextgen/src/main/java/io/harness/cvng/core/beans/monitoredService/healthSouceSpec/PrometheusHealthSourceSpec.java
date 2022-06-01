/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig;
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
public class PrometheusHealthSourceSpec extends MetricHealthSourceSpec {
  @UniqueIdentifierCheck @Valid List<PrometheusMetricDefinition> metricDefinitions;

  public List<PrometheusMetricDefinition> getMetricDefinitions() {
    if (metricDefinitions == null) {
      return Collections.emptyList();
    }
    return metricDefinitions;
  }

  @Override
  public CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentRef, String serviceRef, String monitoredServiceIdentifier, String identifier, String name,
      List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    metricDefinitions.forEach(metricDefinition -> {
      metricDefinition.setServiceIdentifier(serviceRef);
      metricDefinition.setEnvIdentifier(environmentRef);
    });
    List<PrometheusCVConfig> cvConfigsFromThisObj =
        toCVConfigs(accountId, orgIdentifier, projectIdentifier, monitoredServiceIdentifier, identifier, name);
    Map<Key, PrometheusCVConfig> existingConfigMap = new HashMap<>();

    List<PrometheusCVConfig> existingSDCVConfigs = (List<PrometheusCVConfig>) (List<?>) existingCVConfigs;

    for (PrometheusCVConfig prometheusCVConfig : existingSDCVConfigs) {
      existingConfigMap.put(getKeyFromConfig(prometheusCVConfig), prometheusCVConfig);
    }

    Map<Key, PrometheusCVConfig> currentCVConfigsMap = new HashMap<>();
    for (PrometheusCVConfig prometheusCVConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromConfig(prometheusCVConfig), prometheusCVConfig);
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
    return DataSourceType.PROMETHEUS;
  }

  @Override
  public void validate() {
    getMetricDefinitions().forEach(metricDefinition
        -> Preconditions.checkArgument(
            !(Objects.nonNull(metricDefinition.getAnalysis())
                && Objects.nonNull(metricDefinition.getAnalysis().getDeploymentVerification())
                && Objects.nonNull(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled())
                && metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()
                && StringUtils.isEmpty(metricDefinition.getServiceInstanceFieldName())),
            "Service instance field shouldn't be empty for Deployment verification"));
  }

  private List<PrometheusCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String monitoredServiceIdentifier, String identifier, String name) {
    List<PrometheusCVConfig> cvConfigs = new ArrayList<>();
    // One cvConfig per service+environment+groupName+category.
    Map<Key, List<PrometheusMetricDefinition>> keyDefinitionMap = new HashMap<>();
    metricDefinitions.forEach(prometheusMetricDefinition -> {
      Key key = getKeyFromPrometheusMetricDefinition(prometheusMetricDefinition);
      if (!keyDefinitionMap.containsKey(key)) {
        keyDefinitionMap.put(key, new ArrayList<>());
      }
      keyDefinitionMap.get(key).add(prometheusMetricDefinition);
    });

    keyDefinitionMap.forEach((key, definitionList) -> {
      CVMonitoringCategory category = key.getCategory();
      PrometheusCVConfig cvConfig = PrometheusCVConfig.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(identifier)
                                        .connectorIdentifier(getConnectorRef())
                                        .monitoringSourceName(name)
                                        .groupName(key.getGroupName())
                                        .category(category)
                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                        .build();

      cvConfig.populateFromMetricDefinitions(definitionList, category);
      cvConfigs.add(cvConfig);
    });

    return cvConfigs;
  }
  private Key getKeyFromPrometheusMetricDefinition(PrometheusMetricDefinition prometheusMetricDefinition) {
    return Key.builder()
        .category(prometheusMetricDefinition.getRiskProfile().getCategory())
        .groupName(prometheusMetricDefinition.getGroupName())
        .build();
  }

  private Key getKeyFromConfig(PrometheusCVConfig prometheusCVConfig) {
    return Key.builder()
        .monitoredServiceIdentifier(prometheusCVConfig.getMonitoredServiceIdentifier())
        .groupName(prometheusCVConfig.getGroupName())
        .category(prometheusCVConfig.getCategory())
        .build();
  }

  @Value
  @Builder
  private static class Key {
    String monitoredServiceIdentifier;
    CVMonitoringCategory category;
    String groupName;
  }
}
