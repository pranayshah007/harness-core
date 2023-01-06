/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.cvng.newrelic.NewRelicUtils;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class NewRelicDataCollectionInfo extends TimeSeriesDataCollectionInfo<NewRelicConnectorDTO> {
  private static final String QUERIES_KEY = "queries";
  private static final String METRIC_IDENTIFIERS_KEY = "metricIdentifiers";
  private static final String JSON_PATH_KEY = "jsonPaths";
  private static final String METRIC_JSON_PATH_KEY = "metricJsonPaths";
  private static final String TIMESTAMP_JSON_PATH_KEY = "timestampJsonPaths";
  private static final String HOST_JSON_PATH_KEY = "hostJsonPaths";
  private static final String METRIC_NAMES_KEY = "metricNames";

  private String applicationName;
  private long applicationId;
  private String groupName;
  private List<NewRelicMetricInfoDTO> metricInfoList;
  private MetricPackDTO metricPack;

  public List<NewRelicMetricInfoDTO> getMetricInfoList() {
    if (metricInfoList == null) {
      return new ArrayList<>();
    }
    return metricInfoList;
  }

  boolean customQuery;

  @Override
  public Map<String, Object> getDslEnvVariables(NewRelicConnectorDTO newRelicConnectorDTO) {
    if (customQuery) {
      return getEnvVariablesForCustomConfig();
    } else {
      return getEnvVariablesForAppIdBasedConfig();
    }
  }

  private Map<String, Object> getEnvVariablesForAppIdBasedConfig() {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("appName", getApplicationName());
    dslEnvVariables.put("appId", getApplicationId());

    List<String> listOfQueries =
        metricPack.getMetrics().stream().map(MetricPackDTO.MetricDefinitionDTO::getPath).collect(Collectors.toList());
    List<String> listOfJsonPaths = metricPack.getMetrics()
                                       .stream()
                                       .map(MetricPackDTO.MetricDefinitionDTO::getResponseJsonPath)
                                       .collect(Collectors.toList());
    List<String> listOfMetricNames =
        metricPack.getMetrics().stream().map(MetricPackDTO.MetricDefinitionDTO::getName).collect(Collectors.toList());

    List<String> listOfMetricIdentifiers = metricPack.getMetrics()
                                               .stream()
                                               .map(MetricPackDTO.MetricDefinitionDTO::getMetricIdentifier)
                                               .collect(Collectors.toList());

    Preconditions.checkState(listOfQueries.size() == listOfMetricNames.size());
    Preconditions.checkState(listOfQueries.size() == listOfJsonPaths.size());

    boolean nullPath = listOfJsonPaths.stream().anyMatch(path -> path == null);
    Preconditions.checkState(!nullPath, "There can't be any null json paths");

    dslEnvVariables.put(QUERIES_KEY, listOfQueries);
    dslEnvVariables.put(JSON_PATH_KEY, listOfJsonPaths);
    dslEnvVariables.put(METRIC_NAMES_KEY, listOfMetricNames);
    dslEnvVariables.put(METRIC_IDENTIFIERS_KEY, listOfMetricIdentifiers);

    dslEnvVariables.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    return dslEnvVariables;
  }

  private Map<String, Object> getEnvVariablesForCustomConfig() {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("appName", getApplicationName());
    dslEnvVariables.put("appId", getApplicationId());
    dslEnvVariables.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    dslEnvVariables.put("groupName", groupName);
    List<String> listOfQueries =
        getMetricInfoList().stream().map(NewRelicMetricInfoDTO::getNrql).collect(Collectors.toList());
    List<String> metricNames =
        getMetricInfoList().stream().map(NewRelicMetricInfoDTO::getMetricName).collect(Collectors.toList());
    List<String> metricValuePaths = getMetricInfoList()
                                        .stream()
                                        .map(infoDto -> infoDto.getResponseMapping().getMetricValueJsonPath())
                                        .collect(Collectors.toList());
    List<String> timestampPaths = getMetricInfoList()
                                      .stream()
                                      .map(infoDto -> infoDto.getResponseMapping().getTimestampJsonPath())
                                      .collect(Collectors.toList());

    List<String> hostJsonPaths = getMetricInfoList()
                                     .stream()
                                     .map(infoDto -> infoDto.getResponseMapping().getServiceInstanceJsonPath())
                                     .collect(Collectors.toList());

    List<String> metricIdentifiers =
        getMetricInfoList().stream().map(infoDto -> infoDto.getMetricIdentifier()).collect(Collectors.toList());

    dslEnvVariables.put(QUERIES_KEY, listOfQueries);
    dslEnvVariables.put(METRIC_IDENTIFIERS_KEY, metricIdentifiers);
    dslEnvVariables.put(METRIC_JSON_PATH_KEY, metricValuePaths);
    dslEnvVariables.put(TIMESTAMP_JSON_PATH_KEY, timestampPaths);
    dslEnvVariables.put(METRIC_NAMES_KEY, metricNames);
    dslEnvVariables.put(HOST_JSON_PATH_KEY, hostJsonPaths);

    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(NewRelicConnectorDTO newRelicConnectorDTO) {
    return NewRelicUtils.getBaseUrl(newRelicConnectorDTO);
  }

  @Override
  public Map<String, String> collectionHeaders(NewRelicConnectorDTO newRelicConnectorDTO) {
    return NewRelicUtils.collectionHeaders(newRelicConnectorDTO);
  }

  @Override
  public Map<String, String> collectionParams(NewRelicConnectorDTO newRelicConnectorDTO) {
    return Collections.emptyMap();
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class NewRelicMetricInfoDTO {
    String nrql;
    String metricName;
    String metricIdentifier;
    MetricResponseMappingDTO responseMapping;
  }
}
