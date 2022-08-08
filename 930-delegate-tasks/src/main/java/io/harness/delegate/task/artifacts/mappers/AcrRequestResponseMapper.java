/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AcrRequestResponseMapper {
  public AzureConfig toAzureInternalConfig(
      AcrArtifactDelegateRequest acrArtifactDelegateRequest, SecretDecryptionService secretDecryptionService) {
    AzureCredentialDTO credential = acrArtifactDelegateRequest.getAzureConnectorDTO().getCredential();
    List<EncryptedDataDetail> encryptedDataDetails = acrArtifactDelegateRequest.getEncryptedDataDetails();
    AzureCredentialType azureCredentialType = credential.getAzureCredentialType();
    AzureEnvironmentType azureEnvironmentType =
        acrArtifactDelegateRequest.getAzureConnectorDTO().getAzureEnvironmentType();
    return toAzureInternalConfig(
        credential, encryptedDataDetails, azureCredentialType, azureEnvironmentType, secretDecryptionService);
  }
  public AzureConfig toAzureInternalConfig(AzureCredentialDTO credential,
      List<EncryptedDataDetail> encryptedDataDetails, AzureCredentialType azureCredentialType,
      AzureEnvironmentType azureEnvironmentType, SecretDecryptionService secretDecryptionService) {
    AzureConfig azureConfig = AzureConfig.builder().azureEnvironmentType(azureEnvironmentType).build();
    switch (azureCredentialType) {
      case INHERIT_FROM_DELEGATE: {
        AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
            (AzureInheritFromDelegateDetailsDTO) credential.getConfig();
        AzureMSIAuthDTO azureMSIAuthDTO = azureInheritFromDelegateDetailsDTO.getAuthDTO();

        if (azureMSIAuthDTO instanceof AzureMSIAuthUADTO) {
          AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
              ((AzureMSIAuthUADTO) azureMSIAuthDTO).getCredentials();
          azureConfig.setAzureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
          azureConfig.setClientId(azureUserAssignedMSIAuthDTO.getClientId());
        } else if (azureMSIAuthDTO instanceof AzureMSIAuthSADTO) {
          azureConfig.setAzureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED);
        } else {
          throw new IllegalStateException(
              "Unexpected ManagedIdentity credentials type : " + azureMSIAuthDTO.getClass().getName());
        }
        break;
      }
      case MANUAL_CREDENTIALS: {
        AzureManualDetailsDTO azureManualDetailsDTO = (AzureManualDetailsDTO) credential.getConfig();
        secretDecryptionService.decrypt(
            ((AzureManualDetailsDTO) credential.getConfig()).getAuthDTO().getCredentials(), encryptedDataDetails);
        azureConfig.setClientId(azureManualDetailsDTO.getClientId());
        azureConfig.setTenantId(azureManualDetailsDTO.getTenantId());
        switch (azureManualDetailsDTO.getAuthDTO().getAzureSecretType()) {
          case SECRET_KEY:
            azureConfig.setAzureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET);
            AzureClientSecretKeyDTO secretKey =
                (AzureClientSecretKeyDTO) azureManualDetailsDTO.getAuthDTO().getCredentials();
            azureConfig.setKey(secretKey.getSecretKey().getDecryptedValue());
            break;
          case KEY_CERT:
            azureConfig.setAzureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
            AzureClientKeyCertDTO cert = (AzureClientKeyCertDTO) azureManualDetailsDTO.getAuthDTO().getCredentials();
            azureConfig.setCert(String.valueOf(cert.getClientCertRef().getDecryptedValue()).getBytes());
            break;
          default:
            throw new IllegalStateException(
                "Unexpected secret type : " + azureManualDetailsDTO.getAuthDTO().getAzureSecretType());
        }
        break;
      }
      default:
        throw new IllegalStateException("Unexpected azure credential type : " + azureCredentialType);
    }
    return azureConfig;
  }

  public AcrArtifactDelegateResponse toAcrResponse(
      BuildDetailsInternal buildDetailsInternal, AcrArtifactDelegateRequest request) {
    return AcrArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal))
        .subscription(request.getSubscription())
        .registry(request.getRegistry())
        .repository(request.getRepository())
        .tag(buildDetailsInternal.getNumber())
        .sourceType(ArtifactSourceType.ACR)
        .build();
  }
}
