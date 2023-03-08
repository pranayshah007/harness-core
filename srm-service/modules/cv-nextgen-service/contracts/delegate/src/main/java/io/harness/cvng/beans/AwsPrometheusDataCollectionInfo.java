/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.utils.AwsUtils;
import io.harness.cvng.utils.AwsUtils.AwsAccessKeys;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
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
public class AwsPrometheusDataCollectionInfo extends TimeSeriesDataCollectionInfo<AwsConnectorDTO> {
  static final String URL_PREFIX = "aps-workspaces";
  static final String URL_SUFFIX_DATA_COLLECTION = "query_range";
  static final String URL_SUFFIX_HOST_COLLECTION_1 = "label/";
  static final String URL_SUFFIX_HOST_COLLECTION_2 = "/values";
  static final String URL_SUFFIX_COMMON_1 = "/workspaces/";
  static final String URL_SUFFIX_COMMON_2 = "/api/v1/";
  static final String AWS_SERVICE = "aps";
  static final Integer MAX_HOST_SIZE_ALLOWED = 100;

  String region;
  String workspaceId;
  String groupName;
  List<MetricCollectionInfo> metricCollectionInfoList;

  @Override
  public Map<String, Object> getDslEnvVariables(AwsConnectorDTO awsConnectorDTO) {
    String baseUrlForDataCollection = getBaseUrl(awsConnectorDTO) + URL_SUFFIX_DATA_COLLECTION;
    String baseUrlForHostCollection = getBaseUrl(awsConnectorDTO) + URL_SUFFIX_HOST_COLLECTION_1;
    List<String> baseUrlsForHostCollection = new ArrayList<>();
    List<String> metricNameList = new ArrayList<>();
    List<String> metricIdentifierList = new ArrayList<>();
    List<String> serviceInstanceFieldList = new ArrayList<>();
    List<String> filterList = new ArrayList<>();
    List<String> queryList = new ArrayList<>();
    getMetricCollectionInfoList().forEach(metricCollectionInfo -> {
      metricNameList.add(metricCollectionInfo.getMetricName());
      metricIdentifierList.add(metricCollectionInfo.getMetricIdentifier());
      queryList.add(metricCollectionInfo.getQuery());
      if (isCollectHostData()) {
        baseUrlsForHostCollection.add(
            baseUrlForHostCollection + metricCollectionInfo.getServiceInstanceField() + URL_SUFFIX_HOST_COLLECTION_2);
        serviceInstanceFieldList.add(metricCollectionInfo.getServiceInstanceField());
        filterList.add(metricCollectionInfo.getFilters());
      }
    });
    Preconditions.checkState(queryList.size() == metricNameList.size());
    AwsAccessKeys awsCredentials = AwsUtils.getAwsCredentials(awsConnectorDTO);
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    dslEnvVariables.put("serviceName", AWS_SERVICE);
    dslEnvVariables.put("maximumHostSizeAllowed", MAX_HOST_SIZE_ALLOWED);
    dslEnvVariables.put("region", region);
    dslEnvVariables.put("groupName", groupName);
    dslEnvVariables.put("awsSecretKey", awsCredentials.getSecretAccessKey());
    dslEnvVariables.put("awsAccessKey", awsCredentials.getAccessKeyId());
    dslEnvVariables.put("awsSecurityToken", awsCredentials.getSessionToken());
    dslEnvVariables.put("baseUrlForDataCollection", baseUrlForDataCollection);
    dslEnvVariables.put("queryList", queryList);
    dslEnvVariables.put("baseUrlsForHostCollection", baseUrlsForHostCollection);
    dslEnvVariables.put("metricNameList", metricNameList);
    dslEnvVariables.put("metricIdentifiers", metricIdentifierList);
    dslEnvVariables.put("filterList", filterList);
    dslEnvVariables.put("serviceInstanceFieldList", serviceInstanceFieldList);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(AwsConnectorDTO awsConnectorDTO) {
    return AwsUtils.getBaseUrl(region, URL_PREFIX) + URL_SUFFIX_COMMON_1 + workspaceId + URL_SUFFIX_COMMON_2;
  }

  @Override
  public Map<String, String> collectionHeaders(AwsConnectorDTO awsConnectorDTO) {
    return new HashMap<>();
  }

  @Override
  public Map<String, String> collectionParams(AwsConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  public List<MetricCollectionInfo> getMetricCollectionInfoList() {
    if (metricCollectionInfoList == null) {
      return Collections.emptyList();
    }
    return metricCollectionInfoList;
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class MetricCollectionInfo {
    String query;
    String metricName;
    String metricIdentifier;
    String filters;
    String serviceInstanceField;
  }
}
