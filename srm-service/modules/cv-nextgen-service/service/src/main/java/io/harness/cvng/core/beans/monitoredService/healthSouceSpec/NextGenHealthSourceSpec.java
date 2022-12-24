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
import io.harness.cvng.core.beans.healthsource.HealthSourceParamsDTO;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.HealthSourceParams;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.validators.UniqueIdentifierCheck;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;
import io.harness.cvng.models.VerificationType;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NextGenHealthSourceSpec extends MetricHealthSourceSpec {
  DataSourceType dataSourceType;
  @UniqueIdentifierCheck List<QueryDefinition> queryDefinitions = Collections.emptyList();

  @Builder.Default HealthSourceParamsDTO healthSourceParams = HealthSourceParamsDTO.builder().build();
  @Data
  @Builder
  private static class Key {
    String monitoredServiceIdentifier;
    String queryIdentifier;
    String groupName;
    CVMonitoringCategory category;
  }

  private Key getKeyFromCVConfig(@NotNull NextGenLogCVConfig cvConfig) {
    return Key.builder()
        .monitoredServiceIdentifier(cvConfig.getMonitoredServiceIdentifier())
        .queryIdentifier(cvConfig.getQueryName())
        .build();
  }

  private Key getKeyFromCVConfig(@NotNull NextGenMetricCVConfig cvConfig) {
    return Key.builder()
        .monitoredServiceIdentifier(cvConfig.getMonitoredServiceIdentifier())
        .category(cvConfig.getCategory())
        .groupName(cvConfig.getGroupName())
        .build();
  }

  private Map<Key, CVConfig> getExistingCVConfigMap(List<CVConfig> existingCVConfigs) {
    switch (dataSourceType.getVerificationType()) {
      case TIME_SERIES:
        return ((List<NextGenMetricCVConfig>) (List<?>) existingCVConfigs)
            .stream()
            .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
      case LOG:
        return ((List<NextGenLogCVConfig>) (List<?>) existingCVConfigs)
            .stream()
            .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
      default:
        throw new NotImplementedForHealthSourceException("Not Implemented for this health source.");
    }
  }
  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    Map<Key, CVConfig> existingConfigMap = getExistingCVConfigMap(existingCVConfigs);
    Map<Key, CVConfig> currentConfigMap = getCurrentCVConfigMap(
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
  public DataSourceType getType() {
    return dataSourceType;
  }

  @Override
  public void validate() {
    Preconditions.checkNotNull(dataSourceType, "The data source type cannot be null");
    Set<String> uniqueQueryNames = new HashSet<>();
    queryDefinitions.forEach((QueryDefinition query) -> {
      Preconditions.checkArgument(
          StringUtils.isNotBlank(query.getIdentifier()), "Query identifier does not match the expected pattern.");
      if (uniqueQueryNames.contains(query.getName())) {
        throw new InvalidRequestException(String.format("Duplicate query name present %s", query.getName()));
      }
      uniqueQueryNames.add(query.getName());
    });
    if (dataSourceType.getVerificationType() == VerificationType.LOG) {
      queryDefinitions.forEach((QueryDefinition query)
                                   -> Preconditions.checkArgument(Objects.isNull(query.getMetricThresholds())
                                           || query.getMetricThresholds().size() == 0,
                                       "Metric Thresholds should not be present for logs."));
    } else {
      queryDefinitions.forEach((QueryDefinition query) -> {
        Preconditions.checkArgument(
            StringUtils.isNotBlank(query.getGroupName()), "GroupName must be present for metrics");
        if (Objects.nonNull(query.getContinuousVerificationEnabled()) && query.getContinuousVerificationEnabled()) {
          Preconditions.checkArgument(Objects.nonNull(query.getQueryParams())
                  && StringUtils.isNotEmpty(query.getQueryParams().getServiceInstanceField()),
              "Service instance label/key/path shouldn't be empty for Deployment Verification");
        }
      });
    }
  }
  public DataSourceType getDataSourceType() {
    return dataSourceType;
  }

  private Map<Key, CVConfig> getCurrentCVConfigMap(String accountId, String orgIdentifier, String projectIdentifier,
      String monitoredServiceIdentifier, String identifier, String name) {
    switch (dataSourceType.getVerificationType()) {
      case TIME_SERIES:
        Map<Key, List<QueryDefinition>> metricDefinitionMap = new HashMap<>();
        queryDefinitions.forEach((QueryDefinition queryDefinition) -> {
          Key key = Key.builder()
                        .groupName(queryDefinition.getGroupName())
                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                        .category(queryDefinition.getRiskProfile().getCategory())
                        .build();
          List<QueryDefinition> metricDefinitions = metricDefinitionMap.getOrDefault(key, new ArrayList<>());
          metricDefinitions.add(queryDefinition);
          metricDefinitionMap.put(key, metricDefinitions);
        });

        Map<Key, CVConfig> sumologicMetricCVConfigs = new HashMap<>();
        metricDefinitionMap.forEach((Key key, List<QueryDefinition> queryDefinitions) -> {
          NextGenMetricCVConfig nextGenMetricCVConfig =
              NextGenMetricCVConfig.builder()
                  .groupName(key.getGroupName())
                  .accountId(accountId)
                  .verificationType(VerificationType.TIME_SERIES)
                  .dataSourceType(dataSourceType)
                  .orgIdentifier(orgIdentifier)
                  .healthSourceParams(HealthSourceParams.builder().region(healthSourceParams.getRegion()).build())
                  .projectIdentifier(projectIdentifier)
                  .monitoredServiceIdentifier(monitoredServiceIdentifier)
                  .identifier(identifier)
                  .category(queryDefinitions.get(0).getRiskProfile().getCategory())
                  .connectorIdentifier(connectorRef)
                  .monitoredServiceIdentifier(monitoredServiceIdentifier)
                  .monitoringSourceName(name)
                  .build();
          nextGenMetricCVConfig.populateFromQueryDefinitions(
              queryDefinitions, queryDefinitions.get(0).getRiskProfile().getCategory());
          nextGenMetricCVConfig.addCustomMetricThresholds(queryDefinitions);
          sumologicMetricCVConfigs.put(key, nextGenMetricCVConfig);
        });
        return sumologicMetricCVConfigs;
      case LOG:
        return queryDefinitions.stream()
            .map(queryDTO
                -> NextGenLogCVConfig.builder()
                       .accountId(accountId)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .dataSourceType(dataSourceType)
                       .identifier(identifier)
                       .connectorIdentifier(getConnectorRef())
                       .monitoringSourceName(name)
                       .queryName(queryDTO.getName())
                       .query(queryDTO.getQuery())
                       .queryParams(QueryParams.builder()
                                        .serviceInstanceField(queryDTO.getQueryParams().getServiceInstanceField())
                                        .build())
                       .category(CVMonitoringCategory.ERRORS)
                       .monitoredServiceIdentifier(monitoredServiceIdentifier)
                       .build())
            .collect(Collectors.toMap(this::getKeyFromCVConfig, cvConfig -> cvConfig));
      default:
        throw new NotImplementedForHealthSourceException("Not Implemented.");
    }
  }
  @JsonIgnore
  @Deprecated
  public List<HealthSourceMetricDefinition> getMetricDefinitions() {
    return queryDefinitions.stream()
        .map(queryDefinition
            -> HealthSourceMetricDefinition.builder()
                   .metricName(queryDefinition.getName())
                   .identifier(queryDefinition.getIdentifier())
                   .riskProfile(queryDefinition.getRiskProfile())
                   .build())
        .collect(Collectors.toList());
  }
}