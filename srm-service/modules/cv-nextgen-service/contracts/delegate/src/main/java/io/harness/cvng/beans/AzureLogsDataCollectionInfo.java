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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class AzureLogsDataCollectionInfo extends LogDataCollectionInfo<AzureConnectorDTO> {
  String resourceId;
  String query;
  String serviceInstanceIdentifier;
  String timeStampIdentifier;
  String messageIdentifier;

  @Override
  public Map<String, Object> getDslEnvVariables(AzureConnectorDTO connectorConfigDTO) {
    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureUtils.validateConnectorConfigurationType(connectorConfigDTO.getCredential().getConfig());
    AzureClientSecretKeyDTO azureClientSecretKeyDTO =
        AzureUtils.validateConnectorAuthenticationType(azureManualDetailsDTO.getAuthDTO().getCredentials());
    Map<String, Object> envVars = new HashMap<>();
    envVars.put("resourceId", resourceId);
    envVars.put("clientId", azureManualDetailsDTO.getClientId());
    envVars.put("clientSecret", String.valueOf(azureClientSecretKeyDTO.getSecretKey().getDecryptedValue()));
    envVars.put("azureTokenUrl", String.format(AZURE_TOKEN_URL_FORMAT, azureManualDetailsDTO.getTenantId()));
    envVars.put("query", query);
    envVars.put("serviceInstanceIdentifier", "$." + serviceInstanceIdentifier);
    envVars.put("timeStampIdentifier", "$." + timeStampIdentifier);
    envVars.put("messageIdentifier", "$." + messageIdentifier);
    return envVars;
  }

  @Override
  public String getBaseUrl(AzureConnectorDTO connectorConfigDTO) {
    return AzureUtils.getBaseUrl(VerificationType.LOG);
  }

  @Override
  public Map<String, String> collectionHeaders(AzureConnectorDTO connectorConfigDTO) {
    return AzureUtils.collectionHeaders();
  }

  @Override
  public Map<String, String> collectionParams(AzureConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
