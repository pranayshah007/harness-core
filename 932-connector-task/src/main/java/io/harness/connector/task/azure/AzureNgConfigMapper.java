/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.azure;

import static io.harness.azure.model.AzureConstants.DEFAULT_CERT_FILE_NAME;
import static io.harness.filesystem.FileIo.writeFile;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

@OwnedBy(HarnessTeam.CI)
@Singleton
public class AzureNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public AzureConfig mapAzureConfigWithDecryption(AzureConnectorDTO azureConnectorDTO,
      List<EncryptedDataDetail> encryptionDetails, LazyAutoCloseableWorkingDirectory authWorkingDir)
      throws IOException {
    AzureCredentialDTO credential = azureConnectorDTO.getCredential();
    AzureCredentialType credentialType = credential.getAzureCredentialType();
    AzureEnvironmentType azureEnvironmentType = azureConnectorDTO.getAzureEnvironmentType();
    AzureConfig azureConfig = AzureConfig.builder().azureEnvironmentType(azureEnvironmentType).build();
    boolean executeOnDelegate = azureConnectorDTO.getExecuteOnDelegate();

    if (!executeOnDelegate && credentialType == AzureCredentialType.INHERIT_FROM_DELEGATE) {
      throw new InvalidRequestException(
          format("Connector with credential type %s does not support validation through harness", credentialType));
    }

    if (credentialType == AzureCredentialType.MANUAL_CREDENTIALS) {
      AzureManualDetailsDTO config = (AzureManualDetailsDTO) credential.getConfig();
      AzureAuthCredentialDTO azureAuthCredentialDTO = (AzureAuthCredentialDTO) decryptionHelper.decrypt(
          ((AzureManualDetailsDTO) credential.getConfig()).getAuthDTO().getCredentials(), encryptionDetails);
      azureConfig.setClientId(config.getClientId());
      azureConfig.setTenantId(config.getTenantId());

      switch (config.getAuthDTO().getAzureSecretType()) {
        case SECRET_KEY:
          azureConfig.setAzureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET);
          AzureClientSecretKeyDTO secretKey = (AzureClientSecretKeyDTO) azureAuthCredentialDTO;
          azureConfig.setKey(secretKey.getSecretKey().getDecryptedValue());
          break;
        case KEY_CERT:
          azureConfig.setAzureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
          AzureClientKeyCertDTO cert = (AzureClientKeyCertDTO) azureAuthCredentialDTO;
          byte[] certInBytes = String.valueOf(cert.getClientCertRef().getDecryptedValue()).getBytes();
          String certFilePath = format(
              "%s%s.pem", authWorkingDir.createDirectory().workingDir().getAbsolutePath(), DEFAULT_CERT_FILE_NAME);
          writeFile(certFilePath, certInBytes);
          azureConfig.setCertFilePath(certFilePath);
          azureConfig.setCert(certInBytes);
          break;
        default:
          throw new IllegalStateException("Unexpected secret type : " + config.getAuthDTO().getAzureSecretType());
      }
    } else if (credentialType == AzureCredentialType.INHERIT_FROM_DELEGATE) {
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
    } else {
      throw new IllegalStateException("Unexpected azure credential type : " + credentialType);
    }
    return azureConfig;
  }
}
