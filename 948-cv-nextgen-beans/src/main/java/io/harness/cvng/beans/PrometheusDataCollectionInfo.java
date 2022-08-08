/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.cvng.prometheus.PrometheusUtils;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Data
@Builder
public class PrometheusDataCollectionInfo extends TimeSeriesDataCollectionInfo<PrometheusConnectorDTO> {
  private String groupName;
  private List<MetricCollectionInfo> metricCollectionInfoList;
  @Default private Integer maximumHostSizeAllowed = 100;

  public List<MetricCollectionInfo> getMetricCollectionInfoList() {
    if (metricCollectionInfoList == null) {
      return Collections.emptyList();
    }
    return metricCollectionInfoList;
  }
  @Data
  @Builder
  public static class MetricCollectionInfo {
    private String query;
    private String metricName;
    private String metricIdentifier;
    private String filters;
    private String serviceInstanceField;
  }
  @Override
  public Map<String, Object> getDslEnvVariables(PrometheusConnectorDTO connectorConfigDTO) {
    Map<String, Object> collectionEnvs = new HashMap<>();
    List<String> queryList = new ArrayList<>();
    List<String> metricNameList = new ArrayList<>();
    List<String> metricIdentifierList = new ArrayList<>();
    List<String> filterList = new ArrayList<>();
    List<String> serviceInstanceFieldList = new ArrayList<>();

    getMetricCollectionInfoList().forEach(metricCollectionInfo -> {
      queryList.add(metricCollectionInfo.getQuery());
      metricNameList.add(metricCollectionInfo.getMetricName());
      metricIdentifierList.add(metricCollectionInfo.getMetricIdentifier());
      filterList.add(metricCollectionInfo.getFilters());
      serviceInstanceFieldList.add(metricCollectionInfo.getServiceInstanceField());
    });

    Preconditions.checkState(queryList.size() == metricNameList.size());
    collectionEnvs.put("queryList", queryList);
    collectionEnvs.put("metricNameList", metricNameList);
    collectionEnvs.put("metricIdentifiers", metricIdentifierList);
    collectionEnvs.put("filterList", filterList);
    collectionEnvs.put("serviceInstanceFieldList", serviceInstanceFieldList);
    collectionEnvs.put("groupName", groupName);
    collectionEnvs.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    collectionEnvs.put("maximumHostSizeAllowed", maximumHostSizeAllowed);

    return collectionEnvs;
  }

  @Override
  public String getBaseUrl(PrometheusConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(PrometheusConnectorDTO connectorConfigDTO) {
    return PrometheusUtils.getHeaders(connectorConfigDTO);
  }

  @Override
  public Map<String, String> collectionParams(PrometheusConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
