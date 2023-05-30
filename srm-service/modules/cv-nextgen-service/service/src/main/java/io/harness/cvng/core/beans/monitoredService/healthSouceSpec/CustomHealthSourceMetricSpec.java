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
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DataFormatException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.apache.commons.lang3.StringUtils;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "CustomHealthSourceMetric",
    description = "This is the Custom Metric Health Source spec entity defined in Harness")
public class CustomHealthSourceMetricSpec extends MetricHealthSourceSpec {
  private static final String JSON_PATH_ARRAY_DELIMITER = ".[*].";
  private static final String INVALID_DATA_PATH_ERROR_MESSAGE = "Json paths do not match.";
  private static final String INVALID_CHARACTER_ERROR_MESSAGE = "Incorrect json path for %s";
  private static final String NO_ARRAY_FOUND_ERROR_MESSAGE = "No array found in json path for %s.";
  private static final String MISSING_KEY_ERROR_MESSAGE = "Can not derive relative path. Missing key.";
  private static final String EMPTY_JSON_PATH = "Json path for %s is empty or null.";
  private static final String PATH_TYPE_METRIC_VALUE = "metric value";
  private static final String PATH_TYPE_TIMESTAMP = "timestamp";
  private static final String PATH_TYPE_SERVICE_INSTANCE = "service instance";

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
      HealthSourceQueryType queryType = metricDefinition.getQueryType();
      validateJsonPaths(metricResponseMapping, queryType);
      populateRelativeJsonPaths(metricResponseMapping);
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
      mappedCVConfig.setMetricPack(
          mappedCVConfig.generateMetricPack(existingCvConfig != null ? existingCvConfig.getMetricPack() : null,
              metricDefinition.getIdentifier(), metricDefinition.getMetricName(), metricDefinition.getRiskProfile()));
      cvConfigMap.put(cvConfigKey, mappedCVConfig);
    });

    return cvConfigMap;
  }

  private void validateJsonPaths(MetricResponseMapping metricResponseMapping, HealthSourceQueryType queryType) {
    String metricValueJsonPath = metricResponseMapping.getMetricValueJsonPath();
    String timestampJsonPath = metricResponseMapping.getTimestampJsonPath();
    String serviceInstanceJsonPath = metricResponseMapping.getServiceInstanceJsonPath();
    Preconditions.checkState(EmptyPredicate.isNotEmpty(metricValueJsonPath), EMPTY_JSON_PATH, PATH_TYPE_METRIC_VALUE);
    int countMatchesInMetricPath = StringUtils.countMatches(metricValueJsonPath, JSON_PATH_ARRAY_DELIMITER);
    Preconditions.checkState(countMatchesInMetricPath != 0, NO_ARRAY_FOUND_ERROR_MESSAGE, PATH_TYPE_METRIC_VALUE);
    Preconditions.checkState(countMatchesInMetricPath == 2, INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_METRIC_VALUE);
    Preconditions.checkState(EmptyPredicate.isNotEmpty(timestampJsonPath), EMPTY_JSON_PATH, PATH_TYPE_TIMESTAMP);
    int countMatchesInTimestampPath = StringUtils.countMatches(timestampJsonPath, JSON_PATH_ARRAY_DELIMITER);
    Preconditions.checkState(countMatchesInTimestampPath != 0, NO_ARRAY_FOUND_ERROR_MESSAGE, PATH_TYPE_TIMESTAMP);
    Preconditions.checkState(countMatchesInTimestampPath == 2, INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_TIMESTAMP);
    if (HealthSourceQueryType.HOST_BASED.equals(queryType)) {
      Preconditions.checkState(
          EmptyPredicate.isNotEmpty(serviceInstanceJsonPath), EMPTY_JSON_PATH, PATH_TYPE_SERVICE_INSTANCE);
      int countMatchesInServiceInstancePath =
          StringUtils.countMatches(serviceInstanceJsonPath, JSON_PATH_ARRAY_DELIMITER);
      Preconditions.checkState(
          countMatchesInServiceInstancePath != 0, NO_ARRAY_FOUND_ERROR_MESSAGE, PATH_TYPE_SERVICE_INSTANCE);
      Preconditions.checkState(
          countMatchesInServiceInstancePath == 1, INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_SERVICE_INSTANCE);
    }
  }

  private void populateRelativeJsonPaths(MetricResponseMapping metricResponseMapping) {
    String serviceInstanceListJsonPath = getServiceInstanceListJsonPath(metricResponseMapping);
    metricResponseMapping.setServiceInstanceListJsonPath(serviceInstanceListJsonPath);
    String metricListJsonPath = getMetricListJsonPath(metricResponseMapping);
    metricResponseMapping.setRelativeMetricListJsonPath(metricListJsonPath);
    metricResponseMapping.setRelativeMetricValueJsonPath(metricResponseMapping.getMetricValueJsonPath().substring(
        serviceInstanceListJsonPath.length() + metricListJsonPath.length() + (2 * JSON_PATH_ARRAY_DELIMITER.length())));
    metricResponseMapping.setRelativeTimestampJsonPath(metricResponseMapping.getTimestampJsonPath().substring(
        serviceInstanceListJsonPath.length() + metricListJsonPath.length() + (2 * JSON_PATH_ARRAY_DELIMITER.length())));
    if (StringUtils.isNotEmpty(metricResponseMapping.getServiceInstanceJsonPath())) {
      metricResponseMapping.setRelativeServiceInstanceValueJsonPath(
          metricResponseMapping.getServiceInstanceJsonPath().substring(
              serviceInstanceListJsonPath.length() + JSON_PATH_ARRAY_DELIMITER.length()));
    }
  }

  private String getServiceInstanceListJsonPath(MetricResponseMapping metricResponseMapping) {
    String metricValueJsonPath = metricResponseMapping.getMetricValueJsonPath();
    String timestampJsonPath = metricResponseMapping.getTimestampJsonPath();
    String serviceInstanceJsonPath = metricResponseMapping.getServiceInstanceJsonPath();
    int metricValuePathDelimiterIndex = metricValueJsonPath.indexOf(JSON_PATH_ARRAY_DELIMITER);
    int timestampValuePathDelimiterIndex = timestampJsonPath.indexOf(JSON_PATH_ARRAY_DELIMITER);
    if (metricValuePathDelimiterIndex == 0 || timestampValuePathDelimiterIndex == 0) {
      throw new DataFormatException(MISSING_KEY_ERROR_MESSAGE, null);
    }
    String pathDerivedFromMetricValuePath = metricValueJsonPath.substring(0, metricValuePathDelimiterIndex);
    String pathDerivedFromTimestampValuePath = timestampJsonPath.substring(0, timestampValuePathDelimiterIndex);

    if (!pathDerivedFromMetricValuePath.equals(pathDerivedFromTimestampValuePath)) {
      throw new DataFormatException(INVALID_DATA_PATH_ERROR_MESSAGE, null);
    }
    if (StringUtils.isNotEmpty(serviceInstanceJsonPath)) {
      int serviceInstanceValuePathDelimiterIndex = serviceInstanceJsonPath.indexOf(JSON_PATH_ARRAY_DELIMITER);
      if (serviceInstanceValuePathDelimiterIndex == 0) {
        throw new DataFormatException(MISSING_KEY_ERROR_MESSAGE, null);
      }
      String pathDerivedFromServiceInstanceValuePath =
          serviceInstanceJsonPath.substring(0, serviceInstanceValuePathDelimiterIndex);
      if (!pathDerivedFromMetricValuePath.equals(pathDerivedFromServiceInstanceValuePath)) {
        throw new DataFormatException(INVALID_DATA_PATH_ERROR_MESSAGE, null);
      }
    }
    return pathDerivedFromMetricValuePath;
  }

  private String getMetricListJsonPath(MetricResponseMapping metricResponseMapping) {
    String serviceInstanceListJsonPath = metricResponseMapping.getServiceInstanceListJsonPath();
    String metricValueJsonPath = metricResponseMapping.getMetricValueJsonPath();
    String timestampJsonPath = metricResponseMapping.getTimestampJsonPath();
    int startingIndex = serviceInstanceListJsonPath.length() + JSON_PATH_ARRAY_DELIMITER.length();
    String reducedMetricValuePath = metricValueJsonPath.substring(startingIndex);
    String reducedTimestampValuePath = timestampJsonPath.substring(startingIndex);
    int metricValuePathDelimiterIndex = reducedMetricValuePath.indexOf(JSON_PATH_ARRAY_DELIMITER);
    int timestampValuePathDelimiterIndex = reducedTimestampValuePath.indexOf(JSON_PATH_ARRAY_DELIMITER);
    if (metricValuePathDelimiterIndex == 0 || timestampValuePathDelimiterIndex == 0) {
      throw new DataFormatException(MISSING_KEY_ERROR_MESSAGE, null);
    }
    String pathDerivedFromMetricValuePath = reducedMetricValuePath.substring(0, metricValuePathDelimiterIndex);
    String pathDerivedFromTimestampValuePath = reducedTimestampValuePath.substring(0, timestampValuePathDelimiterIndex);
    if (!pathDerivedFromMetricValuePath.equals(pathDerivedFromTimestampValuePath)) {
      throw new DataFormatException(INVALID_DATA_PATH_ERROR_MESSAGE, null);
    }
    return pathDerivedFromMetricValuePath;
  }
}
