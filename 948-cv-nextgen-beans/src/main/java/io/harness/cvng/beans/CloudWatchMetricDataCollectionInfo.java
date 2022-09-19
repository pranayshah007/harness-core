/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.utils.CloudWatchUtils;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudWatchMetricDataCollectionInfo extends TimeSeriesDataCollectionInfo<AwsConnectorDTO> {
  static final String SERVICE = "monitoring";
  static final String GROUPING_CLAUSE = " GROUP BY ";
  String region;
  String groupName;
  List<CloudWatchMetricInfoDTO> metricInfos;
  MetricPackDTO metricPack;

  @Override
  public Map<String, Object> getDslEnvVariables(AwsConnectorDTO awsConnectorDTO) {
    if (isCollectHostData()) {
      metricInfos.forEach(metric -> {
        String finalExpression = String.format("%s%s%s", metric.getExpression(), GROUPING_CLAUSE,
            metric.getResponseMapping().getServiceInstanceJsonPath());
        metric.setFinalExpression(finalExpression);
      });
    }
    return CloudWatchUtils.getDslEnvVariables(
        region, groupName, SERVICE, awsConnectorDTO, metricInfos, isCollectHostData());
  }

  @Override
  public String getBaseUrl(AwsConnectorDTO awsConnectorDTO) {
    return CloudWatchUtils.getBaseUrl(region, SERVICE);
  }

  @Override
  public Map<String, String> collectionHeaders(AwsConnectorDTO awsConnectorDTO) {
    return new HashMap<>();
  }

  @Override
  public Map<String, String> collectionParams(AwsConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class CloudWatchMetricInfoDTO {
    String expression;
    String finalExpression;
    String metricName;
    String metricIdentifier;
    MetricResponseMappingDTO responseMapping;
  }
}
