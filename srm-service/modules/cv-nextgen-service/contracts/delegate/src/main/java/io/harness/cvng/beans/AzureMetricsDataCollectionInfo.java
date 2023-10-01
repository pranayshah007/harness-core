/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.cvng.utils.AzureUtils.AZURE_TOKEN_URL_FORMAT;

import io.harness.cvng.models.VerificationType;
import io.harness.cvng.utils.AzureUtils;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class AzureMetricsDataCollectionInfo extends TimeSeriesDataCollectionInfo<AzureConnectorDTO> {
  String groupName;
  List<MetricCollectionInfo> metricDefinitions;
  public List<MetricCollectionInfo> getMetricDefinitions() {
    if (metricDefinitions == null) {
      return new ArrayList<>();
    }
    return metricDefinitions;
  }

  @Override
  public Map<String, Object> getDslEnvVariables(AzureConnectorDTO connectorConfigDTO) {
    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureUtils.validateConnectorConfigurationType(connectorConfigDTO.getCredential().getConfig());
    AzureClientSecretKeyDTO azureClientSecretKeyDTO =
        AzureUtils.validateConnectorAuthenticationType(azureManualDetailsDTO.getAuthDTO().getCredentials());
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("clientId", azureManualDetailsDTO.getClientId());
    dslEnvVariables.put("clientSecret", String.valueOf(azureClientSecretKeyDTO.getSecretKey().getDecryptedValue()));
    dslEnvVariables.put("azureTokenUrl", String.format(AZURE_TOKEN_URL_FORMAT, azureManualDetailsDTO.getTenantId()));
    dslEnvVariables.put("groupName", groupName);
    dslEnvVariables.put("collectHostData", isCollectHostData());
    List<String> metricIdentifiers = new ArrayList<>();
    List<String> metricNames = new ArrayList<>();
    List<String> healthSourceMetricNames = new ArrayList<>();
    List<String> healthSourceMetricNamespaces = new ArrayList<>();
    List<String> resourceIds = new ArrayList<>();
    List<String> aggregationTypes = new ArrayList<>();
    List<String> serviceInstanceIdentifierTags = new ArrayList<>();
    getMetricDefinitions().forEach(metricDefinition -> {
      metricIdentifiers.add(metricDefinition.getMetricIdentifier());
      metricNames.add(metricDefinition.getMetricName());
      healthSourceMetricNames.add(metricDefinition.getHealthSourceMetricName());
      healthSourceMetricNamespaces.add(metricDefinition.getHealthSourceMetricNamespace());
      resourceIds.add(metricDefinition.getResourceId());
      aggregationTypes.add(metricDefinition.getAggregationType());
      serviceInstanceIdentifierTags.add(metricDefinition.getServiceInstanceIdentifierTag());
    });
    dslEnvVariables.put("metricIdentifiers", metricIdentifiers);
    dslEnvVariables.put("metricNames", metricNames);
    dslEnvVariables.put("healthSourceMetricNames", healthSourceMetricNames);
    dslEnvVariables.put("healthSourceMetricNamespaces", healthSourceMetricNamespaces);
    dslEnvVariables.put("resourceIds", resourceIds);
    dslEnvVariables.put("aggregationTypes", aggregationTypes);
    dslEnvVariables.put("serviceInstanceIdentifierTags", serviceInstanceIdentifierTags);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(AzureConnectorDTO connectorConfigDTO) {
    return AzureUtils.getBaseUrl(VerificationType.TIME_SERIES);
  }

  @Override
  public Map<String, String> collectionHeaders(AzureConnectorDTO connectorConfigDTO) {
    return AzureUtils.collectionHeaders();
  }

  @Override
  public Map<String, String> collectionParams(AzureConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class MetricCollectionInfo {
    String healthSourceMetricName;
    String healthSourceMetricNamespace;
    String resourceId;
    String aggregationType;
    String metricName;
    String metricIdentifier;
    String serviceInstanceIdentifierTag;
  }
}
