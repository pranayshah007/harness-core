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
import io.harness.cvng.core.entities.SumologicMetricCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.utils.CloudWatchUtils;

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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "SumologicMetricHealthSource",
    description = "This is the Sumologic Metric Health Source spec entity defined in Harness")
public class SumologicMetricHealthSourceSpec extends MetricHealthSourceSpec {
  private List<SumologicMetricDefinition> metricDefinitions;

  @Override
  public void validate() {
    getMetricDefinitions().forEach(metricDefinition -> {
      Preconditions.checkArgument(StringUtils.isNotBlank(metricDefinition.getIdentifier())
              && metricDefinition.getIdentifier().matches(
                  CloudWatchUtils.METRIC_QUERY_IDENTIFIER_REGEX), // TODO Make regex for sumologic.
          "Metric identifier does not match the expected pattern: " + CloudWatchUtils.METRIC_QUERY_IDENTIFIER_REGEX);
      Preconditions.checkArgument(
          !(Objects.nonNull(metricDefinition.getAnalysis())
              && Objects.nonNull(metricDefinition.getAnalysis().getDeploymentVerification())
              && Objects.nonNull(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled())
              && metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()
              && (Objects.isNull(metricDefinition.getResponseMapping())
                  || StringUtils.isEmpty(metricDefinition.getResponseMapping().getServiceInstanceJsonPath()))),
          "Service instance label/key/path shouldn't be empty for Deployment Verification");
    });
  }
  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<Key, SumologicMetricCVConfig> existingConfigMap = getExistingCVConfigMap(existingCVConfigs);
    Map<Key, SumologicMetricCVConfig> currentConfigMap = getCurrentCVConfigMap(
        accountId, orgIdentifier, projectIdentifier, monitoredServiceIdentifier, identifier, name);

    Set<SumologicMetricHealthSourceSpec.Key> deleted =
        Sets.difference(existingConfigMap.keySet(), currentConfigMap.keySet());
    Set<SumologicMetricHealthSourceSpec.Key> added =
        Sets.difference(currentConfigMap.keySet(), existingConfigMap.keySet());
    Set<SumologicMetricHealthSourceSpec.Key> updated =
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

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class Key {
    String groupName;
  }

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @SuperBuilder
  @AllArgsConstructor
  public static class SumologicMetricDefinition extends HealthSourceMetricDefinition {
    @NotNull String groupName;
    @NotNull String query;
    MetricResponseMapping responseMapping;
    String serviceInstanceIdentifierTag;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.SUMOLOGIC_METRICS;
  }

  @Override
  public List<SumologicMetricHealthSourceSpec.SumologicMetricDefinition> getMetricDefinitions() {
    return CollectionUtils.isEmpty(metricDefinitions) ? Collections.emptyList() : metricDefinitions;
  }

  private Map<SumologicMetricHealthSourceSpec.Key, SumologicMetricCVConfig> getExistingCVConfigMap(
      List<CVConfig> existingCVConfigs) {
    return ((List<SumologicMetricCVConfig>) (List<?>) existingCVConfigs)
        .stream()
        .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
  }

  private SumologicMetricHealthSourceSpec.Key getKeyFromCVConfig(@NotNull SumologicMetricCVConfig cvConfig) {
    return SumologicMetricHealthSourceSpec.Key.builder().groupName(cvConfig.getGroupName()).build();
  }

  private Map<SumologicMetricHealthSourceSpec.Key, SumologicMetricCVConfig> getCurrentCVConfigMap(String accountId,
      String orgIdentifier, String projectIdentifier, String monitoredServiceIdentifier, String identifier,
      String name) {
    Map<SumologicMetricHealthSourceSpec.Key, List<SumologicMetricHealthSourceSpec.SumologicMetricDefinition>>
        keySumologicMetricDefinitionMap = new HashMap<>();

    metricDefinitions.forEach(metricDefinition -> {
      SumologicMetricHealthSourceSpec.Key key =
          SumologicMetricHealthSourceSpec.Key.builder().groupName(metricDefinition.getGroupName()).build();
      List<SumologicMetricHealthSourceSpec.SumologicMetricDefinition> SumologicMetricDefinitions =
          keySumologicMetricDefinitionMap.getOrDefault(key, new ArrayList<>());
      SumologicMetricDefinitions.add(metricDefinition);
      keySumologicMetricDefinitionMap.put(key, SumologicMetricDefinitions);
    });
    Map<Key, SumologicMetricCVConfig> sumologicMetricCVConfigs = new HashMap<>();
    keySumologicMetricDefinitionMap.forEach((key, metricDefinitionGroup) -> {
      SumologicMetricCVConfig sumologicMetricCVConfig = SumologicMetricCVConfig.builder()
                                                            .groupName(key.getGroupName())
                                                            .accountId(accountId)
                                                            .verificationType(VerificationType.TIME_SERIES)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                            .identifier(identifier)
                                                            .category(CVMonitoringCategory.ERRORS) // TODO Why errors
                                                            .connectorIdentifier(connectorRef)
                                                            .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                            .monitoringSourceName(name)
                                                            .build();
      sumologicMetricCVConfig.populateFromMetricDefinitions(metricDefinitionGroup, CVMonitoringCategory.ERRORS);
      sumologicMetricCVConfigs.put(key, sumologicMetricCVConfig);
    });

    return sumologicMetricCVConfigs;
  }
}