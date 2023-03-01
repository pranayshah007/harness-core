/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO;
import io.harness.cvng.utils.AwsUtils.AwsAccessKeys;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public class CloudWatchUtils {
  public static final String CLOUDWATCH_GET_METRIC_DATA_API_TARGET = "GraniteServiceVersion20100801.GetMetricData";
  public static final String METRIC_QUERY_IDENTIFIER_REGEX = "^[a-z][a-zA-Z0-9_]*$";
  public static String getBaseUrl(String region, String serviceName) {
    return "https://" + serviceName + "." + region + ".amazonaws.com";
  }

  public static Map<String, Object> getDslEnvVariables(String region, String group, String expression,
      String metricName, String metricIdentifier, String service, AwsConnectorDTO connectorDTO,
      boolean collectHostData) {
    Map<String, Object> dslEnvVariables =
        populateCommonDslEnvVariables(region, group, service, connectorDTO, collectHostData);
    dslEnvVariables.put("body", getRequestPayload(expression, metricName, metricIdentifier));
    return dslEnvVariables;
  }

  public static Map<String, Object> getDslEnvVariables(String region, String group, String service,
      AwsConnectorDTO connectorDTO, List<CloudWatchMetricInfoDTO> cloudWatchMetricInfoDTOs, boolean collectHostData) {
    Map<String, Object> dslEnvVariables =
        populateCommonDslEnvVariables(region, group, service, connectorDTO, collectHostData);

    List<List<Map<String, Object>>> requestBodies = new ArrayList<>();
    List<String> metricNames = new ArrayList<>();
    List<String> metricIdentifiers = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(cloudWatchMetricInfoDTOs)) {
      requestBodies.addAll(cloudWatchMetricInfoDTOs.stream()
                               .map(dto -> {
                                 metricNames.add(dto.getMetricName());
                                 metricIdentifiers.add(dto.getMetricIdentifier());
                                 return getRequestPayload(
                                     dto.getFinalExpression(), dto.getMetricName(), dto.getMetricIdentifier());
                               })
                               .collect(Collectors.toList()));
    }
    dslEnvVariables.put("bodies", requestBodies);
    dslEnvVariables.put("metricNames", metricNames);
    dslEnvVariables.put("metricIdentifiers", metricIdentifiers);
    return dslEnvVariables;
  }

  private CloudWatchUtils() {}

  private static Map<String, Object> populateCommonDslEnvVariables(
      String region, String group, String service, AwsConnectorDTO connectorDTO, boolean collectHostData) {
    AwsAccessKeys awsCredentials = AwsUtils.getAwsCredentials(connectorDTO);
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("region", region);
    dslEnvVariables.put("groupName", group);
    dslEnvVariables.put("awsSecretKey", awsCredentials.getSecretAccessKey());
    dslEnvVariables.put("awsAccessKey", awsCredentials.getAccessKeyId());
    dslEnvVariables.put("awsSecurityToken", awsCredentials.getSessionToken());
    dslEnvVariables.put("serviceName", service);
    dslEnvVariables.put("url", getBaseUrl(region, service));
    dslEnvVariables.put("awsTarget", CLOUDWATCH_GET_METRIC_DATA_API_TARGET);
    dslEnvVariables.put("collectHostData", collectHostData);
    return dslEnvVariables;
  }

  private static List<Map<String, Object>> getRequestPayload(
      String expression, String metricName, String metricIdentifier) {
    List<Map<String, Object>> metricQueries = new ArrayList<>();
    Map<String, Object> queryMap = new HashMap<>();
    queryMap.put("Expression", expression);
    queryMap.put("Label", metricName);
    queryMap.put("Id", metricIdentifier);
    queryMap.put("Period", 60);
    metricQueries.add(queryMap);
    return metricQueries;
  }
}
