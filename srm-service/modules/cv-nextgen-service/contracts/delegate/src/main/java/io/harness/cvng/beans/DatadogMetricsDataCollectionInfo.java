/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.cvng.datadog.DatadogUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatadogMetricsDataCollectionInfo extends TimeSeriesDataCollectionInfo<DatadogConnectorDTO> {
  private String groupName;
  private List<MetricCollectionInfo> metricDefinitions;

  public List<MetricCollectionInfo> getMetricDefinitions() {
    if (metricDefinitions == null) {
      return new ArrayList<>();
    }
    return metricDefinitions;
  }

  @Override
  public Map<String, Object> getDslEnvVariables(DatadogConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    List<String> formulaList =
        getMetricDefinitions().stream().map(MetricCollectionInfo::getFormula).collect(Collectors.toList());
    List<List<String>> formulaQueriesList =
        getMetricDefinitions().stream().map(MetricCollectionInfo::getFormulaQueries).collect(Collectors.toList());
    List<String> serviceInstanceIdentifierTagList = getMetricDefinitions()
                                                        .stream()
                                                        .map(MetricCollectionInfo::getServiceInstanceIdentifierTag)
                                                        .collect(Collectors.toList());
    List<String> queries = CollectionUtils.emptyIfNull(
        getMetricDefinitions()
            .stream()
            .map(metricCollectionInfo -> {
              if (isCollectHostData() && metricCollectionInfo.getServiceInstanceIdentifierTag() != null
                  && metricCollectionInfo.getGroupingQuery() != null) {
                return metricCollectionInfo.getGroupingQuery();
              }
              return metricCollectionInfo.getQuery();
            })
            .collect(Collectors.toList()));
    List<String> metricIdentifiers = CollectionUtils.emptyIfNull(
        getMetricDefinitions().stream().map(MetricCollectionInfo::getMetricIdentifier).collect(Collectors.toList()));
    dslEnvVariables.put("formulaList", formulaList);
    dslEnvVariables.put("formulaQueriesList", formulaQueriesList);
    dslEnvVariables.put("queries", queries);
    dslEnvVariables.put("serviceInstanceIdentifierTagList", serviceInstanceIdentifierTagList);
    dslEnvVariables.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    dslEnvVariables.put("groupName", groupName);
    dslEnvVariables.put("metricIdentifiers", metricIdentifiers);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(DatadogConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(DatadogConnectorDTO connectorConfigDTO) {
    return DatadogUtils.collectionHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(DatadogConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  @Data
  @Builder
  public static class MetricCollectionInfo {
    private String query;
    private List<String> formulaQueries;
    private String formula;
    private String groupingQuery;
    private String metricName;
    private String metricIdentifier;
    private String metric;
    private String serviceInstanceIdentifierTag;
  }
}
