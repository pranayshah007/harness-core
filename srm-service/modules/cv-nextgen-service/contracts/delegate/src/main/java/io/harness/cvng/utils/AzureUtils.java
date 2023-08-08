/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.cvng.models.VerificationType;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.exception.InvalidRequestException;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class AzureUtils {
  private final String AZURE_LOGS_BASE_URL = "https://api.loganalytics.io/";
  private final String AZURE_METRICS_BASE_URL = "https://management.azure.com";
  public static final String AZURE_TOKEN_URL_FORMAT = "https://login.microsoftonline.com/%s/oauth2/token";

  public String getBaseUrl(VerificationType type) {
    if (type == VerificationType.LOG) {
      return AZURE_LOGS_BASE_URL;
    } else {
      return AZURE_METRICS_BASE_URL;
    }
  }

  public Map<String, String> collectionHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-type", "application/json");
    return headers;
  }

  public AzureManualDetailsDTO validateConnectorConfigurationType(AzureCredentialSpecDTO azureCredentialSpecDTO) {
    if (!(azureCredentialSpecDTO instanceof AzureManualDetailsDTO)) {
      throw new InvalidRequestException("Please configure the connector through \"specify credentials\" step");
    }
    return (AzureManualDetailsDTO) azureCredentialSpecDTO;
  }

  public AzureClientSecretKeyDTO validateConnectorAuthenticationType(AzureAuthCredentialDTO azureAuthCredentialDTO) {
    if (!(azureAuthCredentialDTO instanceof AzureClientSecretKeyDTO)) {
      throw new InvalidRequestException("Please configure the connector with client secret");
    }
    return (AzureClientSecretKeyDTO) azureAuthCredentialDTO;
  }
}
