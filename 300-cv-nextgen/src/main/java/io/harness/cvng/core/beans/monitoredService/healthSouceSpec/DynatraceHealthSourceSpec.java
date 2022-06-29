/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MetricPackDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
public class DynatraceHealthSourceSpec extends MetricHealthSourceSpec {
  @NotNull String feature;
  @NotEmpty String serviceId;
  String serviceName;
  List<String> serviceMethodIds;
  @Valid Set<MetricPackDTO> metricPacks;
  List<DynatraceMetricDefinition> metricDefinitions;

  @Override
  public String getConnectorRef() {
    return connectorRef;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.DYNATRACE;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    List<DynatraceCVConfig> cvConfigsFromThisObj = toCVConfigs(ProjectParams.builder()
                                                                   .accountIdentifier(accountId)
                                                                   .orgIdentifier(orgIdentifier)
                                                                   .projectIdentifier(projectIdentifier)
                                                                   .build(),
        environmentRef, serviceRef, identifier, name, monitoredServiceIdentifier, metricPackService);
    Map<Key, DynatraceCVConfig> existingConfigMap = new HashMap<>();
    List<DynatraceCVConfig> existingDynatraceCVConfig = (List<DynatraceCVConfig>) (List<?>) existingCVConfigs;
    for (DynatraceCVConfig dynatraceCVConfig : existingDynatraceCVConfig) {
      existingConfigMap.put(getKeyFromCVConfig(dynatraceCVConfig), dynatraceCVConfig);
    }
    Map<Key, DynatraceCVConfig> currentCVConfigsMap = new HashMap<>();
    for (DynatraceCVConfig dynatraceCVConfig : cvConfigsFromThisObj) {
      currentCVConfigsMap.put(getKeyFromCVConfig(dynatraceCVConfig), dynatraceCVConfig);
    }
    Set<Key> deleted = Sets.difference(existingConfigMap.keySet(), currentCVConfigsMap.keySet());
    Set<Key> added = Sets.difference(currentCVConfigsMap.keySet(), existingConfigMap.keySet());
    Set<Key> updated = Sets.intersection(existingConfigMap.keySet(), currentCVConfigsMap.keySet());
    List<CVConfig> updatedConfigs = updated.stream().map(currentCVConfigsMap::get).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid = updated.stream().map(existingConfigMap::get).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigs.size(); i++) {
      updatedConfigs.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(existingConfigMap::get).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(currentCVConfigsMap::get).collect(Collectors.toList()))
        .build();
  }

  private List<DynatraceCVConfig> toCVConfigs(ProjectParams projectParams, String environmentRef, String serviceRef,
      String identifier, String name, String monitoredServiceIdentifier, MetricPackService metricPackService) {
    List<DynatraceCVConfig> cvConfigs = new ArrayList<>();
    // map metric packs to cvConfigs
    CollectionUtils.emptyIfNull(metricPacks).forEach(metricPack -> {
      MetricPack metricPackFromDb = metricPack.toMetricPack(projectParams.getAccountIdentifier(),
          projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), getType(), metricPackService);
      DynatraceCVConfig cvConfig = DynatraceCVConfig.builder()
                                       .accountId(projectParams.getAccountIdentifier())
                                       .orgIdentifier(projectParams.getOrgIdentifier())
                                       .projectIdentifier(projectParams.getProjectIdentifier())
                                       .identifier(identifier)
                                       .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                       .connectorIdentifier(getConnectorRef())
                                       .monitoringSourceName(name)
                                       .productName(feature)
                                       .dynatraceServiceName(serviceName)
                                       .dynatraceServiceId(serviceId)
                                       .serviceMethodIds(serviceMethodIds)
                                       .metricPack(metricPackFromDb)
                                       .category(metricPackFromDb.getCategory())
                                       .build();
      cvConfigs.add(cvConfig);
    });

    // map custom metrics to cvConfigs
    cvConfigs.addAll(CollectionUtils.emptyIfNull(metricDefinitions)
                         .stream()
                         .collect(Collectors.groupingBy(MetricDefinitionKey::fromMetricDefinition))
                         .values()
                         .stream()
                         .map(metricDefinitionList -> {
                           DynatraceCVConfig cvConfig =
                               DynatraceCVConfig.builder()
                                   .accountId(projectParams.getAccountIdentifier())
                                   .orgIdentifier(projectParams.getOrgIdentifier())
                                   .projectIdentifier(projectParams.getProjectIdentifier())
                                   .identifier(identifier)
                                   .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                   .connectorIdentifier(getConnectorRef())
                                   .monitoringSourceName(name)
                                   .productName(feature)
                                   .dynatraceServiceName(serviceName)
                                   .dynatraceServiceId(serviceId)
                                   .serviceMethodIds(serviceMethodIds)
                                   .groupName(metricDefinitionList.get(0).getGroupName())
                                   .category(metricDefinitionList.get(0).getRiskProfile().getCategory())
                                   .build();
                           cvConfig.populateFromMetricDefinitions(metricDefinitionList,
                               metricDefinitionList.get(0).getAnalysis().getRiskProfile().getCategory());
                           return cvConfig;
                         })
                         .collect(Collectors.toList()));
    return cvConfigs;
  }

  private Key getKeyFromCVConfig(DynatraceCVConfig cvConfig) {
    return Key.builder().metricPack(cvConfig.getMetricPack()).groupName(cvConfig.getGroupName()).build();
  }

  @Value
  @Builder
  private static class Key {
    String groupName;
    String serviceId;
    MetricPack metricPack;
  }

  @Data
  @SuperBuilder
  @NoArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class DynatraceMetricDefinition extends HealthSourceMetricDefinition {
    String groupName;
    String metricSelector;
    @JsonProperty(value = "isManualQuery")
    public boolean isManualQuery() {
      return isManualQuery;
    }
    boolean isManualQuery;
  }

  @Value
  @Builder
  private static class MetricDefinitionKey {
    String groupName;
    CVMonitoringCategory category;

    public static MetricDefinitionKey fromMetricDefinition(DynatraceMetricDefinition dynatraceMetricDefinition) {
      return MetricDefinitionKey.builder()
          .category(dynatraceMetricDefinition.getRiskProfile().getCategory())
          .groupName(dynatraceMetricDefinition.getGroupName())
          .build();
    }
  }
}
