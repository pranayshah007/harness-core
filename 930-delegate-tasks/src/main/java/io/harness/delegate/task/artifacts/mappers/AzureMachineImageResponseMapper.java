package io.harness.delegate.task.artifacts.mappers;

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
import io.harness.delegate.task.artifacts.azuremachineimage.AzureMachineImageDelegateRequest;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.List;
import lombok.experimental.UtilityClass;
@UtilityClass
public class AzureMachineImageResponseMapper {
  public AzureConfig toAzureInternalConfig(AzureMachineImageDelegateRequest azureMachineImageDelegateRequest,
      SecretDecryptionService secretDecryptionService) {
    AzureCredentialDTO credential = azureMachineImageDelegateRequest.getAzureConnectorDTO().getCredential();
    List<EncryptedDataDetail> encryptedDataDetails = azureMachineImageDelegateRequest.getEncryptedDataDetails();
    AzureCredentialType azureCredentialType = credential.getAzureCredentialType();
    AzureEnvironmentType azureEnvironmentType =
        azureMachineImageDelegateRequest.getAzureConnectorDTO().getAzureEnvironmentType();
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
}
