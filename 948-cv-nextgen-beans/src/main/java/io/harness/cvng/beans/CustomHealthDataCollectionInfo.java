/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfoUtils;
import io.harness.serializer.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomHealthDataCollectionInfo extends TimeSeriesDataCollectionInfo<CustomHealthConnectorDTO> {
  String groupName;
  List<CustomHealthMetricInfo> metricInfoList;

  public List<CustomHealthMetricInfo> getMetricInfoList() {
    if (metricInfoList == null) {
      return Collections.emptyList();
    }
    return metricInfoList;
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class CustomHealthMetricInfo {
    String metricName;
    String metricIdentifier;
    String body;
    String urlPath;
    CustomHealthMethod method;
    MetricResponseMappingDTO responseMapping;
    TimestampInfo startTime;
    TimestampInfo endTime;
  }

  @Override
  public Map<String, Object> getDslEnvVariables(CustomHealthConnectorDTO connectorConfigDTO) {
    List<String> metricNames = new ArrayList<>();
    List<String> metricIdentifiers = new ArrayList<>();
    List<String> urlPaths = new ArrayList<>();
    List<Object> bodies = new ArrayList<>();
    List<String> methods = new ArrayList<>();
    List<String> startTimePlaceholders = new ArrayList<>();
    List<String> endTimePlaceholders = new ArrayList<>();
    List<String> startTimeFormats = new ArrayList<>();
    List<String> endTimeFormats = new ArrayList<>();
    List<String> timestampJSONPaths = new ArrayList<>();
    List<String> metricValueJSONPaths = new ArrayList<>();
    List<String> serviceInstanceJSONPaths = new ArrayList<>();

    getMetricInfoList().forEach(metricInfo -> {
      metricNames.add(metricInfo.getMetricName());
      metricIdentifiers.add(metricInfo.getMetricIdentifier());
      bodies.add(isEmpty(metricInfo.getBody()) ? null : JsonUtils.asMap(metricInfo.body));
      methods.add(metricInfo.getMethod().toString());
      urlPaths.add(metricInfo.getUrlPath());
      startTimePlaceholders.add(metricInfo.getStartTime().getPlaceholder());
      endTimePlaceholders.add(metricInfo.getEndTime().getPlaceholder());
      startTimeFormats.add(metricInfo.getStartTime().getTimestampFormat().toString());
      endTimeFormats.add(metricInfo.getEndTime().getTimestampFormat().toString());

      MetricResponseMappingDTO responseMapping = metricInfo.getResponseMapping();
      timestampJSONPaths.add(responseMapping.getTimestampJsonPath());
      metricValueJSONPaths.add(responseMapping.getMetricValueJsonPath());
      serviceInstanceJSONPaths.add(
          isEmpty(responseMapping.getServiceInstanceJsonPath()) ? null : responseMapping.getServiceInstanceJsonPath());
    });

    Map<String, Object> envVars = new HashMap<>();
    envVars.put("metricNames", metricNames);
    envVars.put("metricIdentifiers", metricIdentifiers);
    envVars.put("methods", methods);
    envVars.put("urlPaths", urlPaths);
    envVars.put("startTimePlaceholders", startTimePlaceholders);
    envVars.put("startTimeFormats", startTimeFormats);
    envVars.put("endTimePlaceholders", endTimePlaceholders);
    envVars.put("endTimeFormats", endTimeFormats);
    envVars.put("timestampValueJSONPaths", timestampJSONPaths);
    envVars.put("metricValueJSONPaths", metricValueJSONPaths);
    envVars.put("serviceInstanceJSONPaths", serviceInstanceJSONPaths);
    envVars.put("groupName", groupName);
    envVars.put("bodies", bodies);
    return envVars;
  }

  @Override
  public String getBaseUrl(CustomHealthConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getBaseURL();
  }

  @Override
  public Map<String, String> collectionHeaders(CustomHealthConnectorDTO connectorConfigDTO) {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getHeaders());
  }

  @Override
  public Map<String, String> collectionParams(CustomHealthConnectorDTO connectorConfigDTO) {
    return CustomHealthConnectorValidationInfoUtils.convertKeyAndValueListToMap(connectorConfigDTO.getParams());
  }
}
