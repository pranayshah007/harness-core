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
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.validators.UniqueIdentifierCheck;
import io.harness.cvng.utils.CloudWatchUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "CloudWatchMetricsHealthSource",
    description = "This is the Cloud Watch Metric Health Source spec entity defined in Harness", hidden = true)
public class CloudWatchMetricsHealthSourceSpec extends MetricHealthSourceSpec {
  @NotNull @NotBlank String region;
  @NotNull @NotBlank String feature;
  @Valid @UniqueIdentifierCheck @NotEmpty List<CloudWatchMetricDefinition> metricDefinitions;

  @Override
  public void validate() {
    getMetricDefinitions().forEach(metricDefinition -> {
      Preconditions.checkArgument(StringUtils.isNotBlank(metricDefinition.getIdentifier())
              && metricDefinition.getIdentifier().matches(CloudWatchUtils.METRIC_QUERY_IDENTIFIER_REGEX),
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
    Map<Key, CloudWatchMetricCVConfig> existingConfigMap = new HashMap<>();
    List<CloudWatchMetricCVConfig> existingCloudWatchCvConfigs =
        (List<CloudWatchMetricCVConfig>) (List<?>) existingCVConfigs;
    for (CloudWatchMetricCVConfig cloudWatchMetricCVConfig : existingCloudWatchCvConfigs) {
      existingConfigMap.put(Key.fromCVConfig(cloudWatchMetricCVConfig), cloudWatchMetricCVConfig);
    }

    List<CloudWatchMetricCVConfig> cvConfigsFromThisObj =
        toCVConfigs(accountId, orgIdentifier, projectIdentifier, monitoredServiceIdentifier, identifier, name);
    Map<Key, CloudWatchMetricCVConfig> newCvConfigMap = new HashMap<>();
    for (CloudWatchMetricCVConfig cloudWatchMetricCVConfig : cvConfigsFromThisObj) {
      newCvConfigMap.put(Key.fromCVConfig(cloudWatchMetricCVConfig), cloudWatchMetricCVConfig);
    }

    Set<Key> deleted = Sets.difference(existingConfigMap.keySet(), newCvConfigMap.keySet());
    Set<Key> added = Sets.difference(newCvConfigMap.keySet(), existingConfigMap.keySet());
    Set<Key> updated = Sets.intersection(existingConfigMap.keySet(), newCvConfigMap.keySet());

    List<CVConfig> updatedConfigsWithoutUuid = updated.stream().map(newCvConfigMap::get).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid = updated.stream().map(existingConfigMap::get).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigsWithoutUuid.size(); i++) {
      updatedConfigsWithoutUuid.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(existingConfigMap::get).collect(Collectors.toList()))
        .updated(updatedConfigsWithoutUuid)
        .added(added.stream().map(newCvConfigMap::get).collect(Collectors.toList()))
        .build();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CLOUDWATCH_METRICS;
  }

  public List<CloudWatchMetricDefinition> getMetricDefinitions() {
    return CollectionUtils.isEmpty(metricDefinitions) ? Collections.emptyList() : metricDefinitions;
  }

  // As of now we do not provide out-of-box metric packs for CloudWatch metrics. Once we do, we need to create
  // cvConfigs from those metric packs too.
  private List<CloudWatchMetricCVConfig> toCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String monitoredServiceIdentifier, String identifier, String name) {
    List<CloudWatchMetricCVConfig> cvConfigs =
        CollectionUtils.emptyIfNull(metricDefinitions)
            .stream()
            .collect(Collectors.groupingBy(MetricDefinitionKey::fromMetricDefinition))
            .values()
            .stream()
            .map(mdList -> {
              CloudWatchMetricCVConfig metricCVConfig = CloudWatchMetricCVConfig.builder()
                                                            .accountId(accountId)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .identifier(identifier)
                                                            .connectorIdentifier(getConnectorRef())
                                                            .monitoringSourceName(name)
                                                            .productName(feature)
                                                            .region(region)
                                                            .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                            .groupName(mdList.get(0).getGroupName())
                                                            .category(mdList.get(0).getRiskProfile().getCategory())
                                                            .build();
              metricCVConfig.addMetricPackAndInfo(mdList);
              return metricCVConfig;
            })
            .collect(Collectors.toList());

    // Add user defined metric thresholds to respective cvConfigs
    cvConfigs.forEach(cvConfig -> cvConfig.addMetricThresholds(metricPacks, metricDefinitions));

    cvConfigs.stream()
        .filter(cvConfig -> CollectionUtils.isNotEmpty(cvConfig.getMetricInfos()))
        .flatMap(cvConfig -> cvConfig.getMetricInfos().stream())
        .forEach(metricInfo -> {
          if (metricInfo.getDeploymentVerification().isEnabled()) {
            if (StringUtils.isEmpty(metricInfo.getResponseMapping().getServiceInstanceJsonPath())) {
              throw new BadRequestException("ServiceInstanceJsonPath should be set for Deployment Verification");
            }
          }
        });
    return cvConfigs;
  }

  @Data
  @SuperBuilder
  @NoArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class CloudWatchMetricDefinition extends HealthSourceMetricDefinition {
    @NotNull @NotBlank String groupName;
    @NotNull @NotBlank String expression;
    MetricResponseMapping responseMapping;
  }

  @Value
  @Builder
  private static class Key {
    String region;
    String groupName;
    MetricPack metricPack;

    public static Key fromCVConfig(CloudWatchMetricCVConfig cloudWatchMetricCVConfig) {
      return Key.builder()
          .region(cloudWatchMetricCVConfig.getRegion())
          .groupName(cloudWatchMetricCVConfig.getGroupName())
          .metricPack(cloudWatchMetricCVConfig.getMetricPack())
          .build();
    }
  }

  @Value
  @Builder
  private static class MetricDefinitionKey {
    String groupName;
    CVMonitoringCategory category;

    public static MetricDefinitionKey fromMetricDefinition(CloudWatchMetricDefinition cloudWatchMetricDefinition) {
      return MetricDefinitionKey.builder()
          .groupName(cloudWatchMetricDefinition.getGroupName())
          .category(cloudWatchMetricDefinition.getRiskProfile().getCategory())
          .build();
    }
  }
}
