/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.cvng.splunk.SplunkUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class SplunkMetricDataCollectionInfo extends TimeSeriesDataCollectionInfo<SplunkConnectorDTO> {
  String groupName;
  List<MetricInfo> metricInfos;
  @Override
  public Map<String, Object> getDslEnvVariables(SplunkConnectorDTO splunkConnectorDTO) {
    Map<String, Object> map = new HashMap<>();
    map.put("groupName", groupName);
    map.put("queries", metricInfos.stream().map(MetricInfo::getQuery).collect(Collectors.toList()));
    map.put("metricNames", metricInfos.stream().map(MetricInfo::getMetricName).collect(Collectors.toList()));
    map.put("metricIdentifiers", metricInfos.stream().map(MetricInfo::getIdentifier).collect(Collectors.toList()));
    return map;
  }

  @Override
  public String getBaseUrl(SplunkConnectorDTO splunkConnectorDTO) {
    return splunkConnectorDTO.getSplunkUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(SplunkConnectorDTO splunkConnectorDTO) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", SplunkUtils.getAuthorizationHeader(splunkConnectorDTO));
    return headers;
  }

  @Override
  public Map<String, String> collectionParams(SplunkConnectorDTO splunkConnectorDTO) {
    return Collections.emptyMap();
  }
  @Value
  @Builder
  public static class MetricInfo {
    String query;
    String identifier;
    String metricName;
  }
}
