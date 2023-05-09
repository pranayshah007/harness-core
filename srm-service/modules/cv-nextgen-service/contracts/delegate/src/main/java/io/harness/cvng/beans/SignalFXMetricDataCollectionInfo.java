/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.cvng.signalfx.SignalFXUtils;

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
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class SignalFXMetricDataCollectionInfo extends TimeSeriesDataCollectionInfo<SignalFXConnectorDTO> {
  private static final String GROUPING_CLAUSE_HOST = ".mean(by='%s').publish()";
  private static final String GROUPING_CLAUSE_NON_HOST = ".mean().publish()";
  String groupName;

  List<MetricCollectionInfo> metricDefinitions;
  public List<MetricCollectionInfo> getMetricDefinitions() {
    if (metricDefinitions == null) {
      return new ArrayList<>();
    }
    return metricDefinitions;
  }

  @Override
  public Map<String, Object> getDslEnvVariables(SignalFXConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    List<String> metricIdentifiers = CollectionUtils.emptyIfNull(
        getMetricDefinitions().stream().map(MetricCollectionInfo::getMetricIdentifier).collect(Collectors.toList()));
    if (isCollectHostData()) {
      getMetricDefinitions().forEach((MetricCollectionInfo metric) -> {
        String fullQuery =
            String.format(metric.getQuery() + GROUPING_CLAUSE_HOST, metric.getServiceInstanceIdentifierTag());

        metric.setQuery(fullQuery);
        dslEnvVariables.put("serviceInstanceIdentifierTag", metric.getServiceInstanceIdentifierTag());
      });
    } else {
      getMetricDefinitions().forEach((MetricCollectionInfo metric) -> {
        String fullQuery = String.format("%s%s", metric.getQuery(), GROUPING_CLAUSE_NON_HOST);
        metric.setQuery(fullQuery);
        dslEnvVariables.put("serviceInstanceIdentifierTag", metric.getServiceInstanceIdentifierTag());
      });
    }

    List<String> queries = CollectionUtils.emptyIfNull(
        getMetricDefinitions().stream().map(MetricCollectionInfo::getQuery).collect(Collectors.toList()));

    dslEnvVariables.put("queries", queries);
    dslEnvVariables.put("groupName", groupName);
    dslEnvVariables.put("metricIdentifiers", metricIdentifiers);
    dslEnvVariables.put("metricNames",
        getMetricDefinitions().stream().map(MetricCollectionInfo::getMetricName).collect(Collectors.toList()));
    dslEnvVariables.put("collectHostData", isCollectHostData());
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(SignalFXConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(SignalFXConnectorDTO connectorConfigDTO) {
    return SignalFXUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(SignalFXConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class MetricCollectionInfo {
    String query;
    String metricName;
    String metricIdentifier;
    String serviceInstanceIdentifierTag;
  }
}
