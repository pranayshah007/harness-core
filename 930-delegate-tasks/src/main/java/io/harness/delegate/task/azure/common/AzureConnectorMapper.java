/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConfig.AzureConfigBuilder;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;

import com.google.inject.Singleton;

@OwnedBy(CDP)
@Singleton
public class AzureConnectorMapper {
  public AzureConfig toAzureConfig(AzureConnectorDTO connector) {
    AzureCredentialDTO credentials = connector.getCredential();
    AzureConfigBuilder azureConfigBuilder =
        AzureConfig.builder().azureEnvironmentType(connector.getAzureEnvironmentType());
    switch (credentials.getAzureCredentialType()) {
      case INHERIT_FROM_DELEGATE: {
        AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
            (AzureInheritFromDelegateDetailsDTO) credentials.getConfig();
        AzureMSIAuthDTO azureMSIAuthDTO = azureInheritFromDelegateDetailsDTO.getAuthDTO();

        if (azureMSIAuthDTO instanceof AzureMSIAuthUADTO) {
          AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
              ((AzureMSIAuthUADTO) azureMSIAuthDTO).getCredentials();
          azureConfigBuilder.azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
          azureConfigBuilder.clientId(azureUserAssignedMSIAuthDTO.getClientId());
        } else if (azureMSIAuthDTO instanceof AzureMSIAuthSADTO) {
          azureConfigBuilder.azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED);
        } else {
          throw new IllegalStateException(
              "Unexpected ManagedIdentity credentials type : " + azureMSIAuthDTO.getClass().getName());
        }
        break;
      }
      case MANUAL_CREDENTIALS: {
        AzureManualDetailsDTO azureManualDetailsDTO = (AzureManualDetailsDTO) credentials.getConfig();
        azureConfigBuilder.clientId(azureManualDetailsDTO.getClientId());
        azureConfigBuilder.tenantId(azureManualDetailsDTO.getTenantId());
        switch (azureManualDetailsDTO.getAuthDTO().getAzureSecretType()) {
          case SECRET_KEY:
            azureConfigBuilder.azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET);
            AzureClientSecretKeyDTO secretKey =
                (AzureClientSecretKeyDTO) azureManualDetailsDTO.getAuthDTO().getCredentials();
            azureConfigBuilder.key(secretKey.getSecretKey().getDecryptedValue());
            break;
          case KEY_CERT:
            azureConfigBuilder.azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
            AzureClientKeyCertDTO cert = (AzureClientKeyCertDTO) azureManualDetailsDTO.getAuthDTO().getCredentials();
            azureConfigBuilder.cert(String.valueOf(cert.getClientCertRef().getDecryptedValue()).getBytes());
            break;
          default:
            throw new IllegalStateException(
                "Unexpected secret type : " + azureManualDetailsDTO.getAuthDTO().getAzureSecretType());
        }
        break;
      }
      default:
        throw new IllegalStateException("Unexpected azure credential type : " + credentials.getAzureCredentialType());
    }

    return azureConfigBuilder.build();
  }
}
