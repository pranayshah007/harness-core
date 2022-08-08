/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.azuremapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azureconnector.AzureConfig;
import io.harness.connector.entities.embedded.azureconnector.AzureManagedIdentityCredential;
import io.harness.connector.entities.embedded.azureconnector.AzureManualCredential;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuth;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoSshAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoUsernameToken;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
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
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureDTOToEntity implements ConnectorDTOToEntityMapper<AzureConnectorDTO, AzureConfig> {
  @Override
  public AzureConfig toConnectorEntity(AzureConnectorDTO connectorDTO) {
    final AzureCredentialDTO credential = connectorDTO.getCredential();
    final AzureCredentialType credentialType = credential.getAzureCredentialType();
    final AzureConfig azureConfig;
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        azureConfig = buildInheritFromDelegate(credential);
        break;
      case MANUAL_CREDENTIALS:
        azureConfig = buildManualCredential(credential);
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
    azureConfig.setAzureEnvironmentType(connectorDTO.getAzureEnvironmentType());

    return azureConfig;
  }

  public static AzureRepoAuthentication buildAuthenticationDetails(
      GitAuthType gitAuthType, AzureRepoCredentialsDTO credentialsDTO) {
    switch (gitAuthType) {
      case SSH:
        final AzureRepoSshCredentialsDTO sshCredentialsDTO = (AzureRepoSshCredentialsDTO) credentialsDTO;
        return AzureRepoSshAuthentication.builder()
            .sshKeyRef(SecretRefHelper.getSecretConfigString(sshCredentialsDTO.getSshKeyRef()))
            .build();
      case HTTP:
        final AzureRepoHttpCredentialsDTO httpCredentialsDTO = (AzureRepoHttpCredentialsDTO) credentialsDTO;
        final AzureRepoHttpAuthenticationType type = httpCredentialsDTO.getType();
        return AzureRepoHttpAuthentication.builder().type(type).auth(getHttpAuth(type, httpCredentialsDTO)).build();
      default:
        throw new UnknownEnumTypeException(
            "Azure Auth Type", gitAuthType == null ? null : gitAuthType.getDisplayName());
    }
  }

  private AzureConfig buildInheritFromDelegate(AzureCredentialDTO connector) {
    final AzureInheritFromDelegateDetailsDTO config = (AzureInheritFromDelegateDetailsDTO) connector.getConfig();
    final AzureMSIAuthDTO authDTO = config.getAuthDTO();

    AzureManagedIdentityCredential azureManagedIdentityCredential;

    if (authDTO instanceof AzureMSIAuthUADTO) {
      AzureMSIAuthUADTO azureMSIAuthUADTO = (AzureMSIAuthUADTO) authDTO;

      AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
          (AzureUserAssignedMSIAuthDTO) azureMSIAuthUADTO.getCredentials();

      azureManagedIdentityCredential = AzureManagedIdentityCredential.builder()
                                           .azureManagedIdentityType(azureMSIAuthUADTO.getAzureManagedIdentityType())
                                           .clientId(azureUserAssignedMSIAuthDTO.getClientId())
                                           .build();
    } else if (authDTO instanceof AzureMSIAuthSADTO) {
      AzureMSIAuthSADTO azureMSIAuthSADTO = (AzureMSIAuthSADTO) authDTO;
      azureManagedIdentityCredential = AzureManagedIdentityCredential.builder()
                                           .azureManagedIdentityType(azureMSIAuthSADTO.getAzureManagedIdentityType())
                                           .build();
    } else {
      throw new InvalidRequestException("Invalid ManagedIdentity credentials type.");
    }

    return AzureConfig.builder()
        .credentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .credential(azureManagedIdentityCredential)
        .build();
  }

  private AzureConfig buildManualCredential(AzureCredentialDTO connector) {
    final AzureManualDetailsDTO config = (AzureManualDetailsDTO) connector.getConfig();
    final AzureAuthDTO authDTO = config.getAuthDTO();

    final String secretKeyRef;
    switch (authDTO.getAzureSecretType()) {
      case SECRET_KEY:
        AzureClientSecretKeyDTO secretKeyDTO = (AzureClientSecretKeyDTO) authDTO.getCredentials();
        secretKeyRef = SecretRefHelper.getSecretConfigString(secretKeyDTO.getSecretKey());
        break;
      case KEY_CERT:
        AzureClientKeyCertDTO certDTO = (AzureClientKeyCertDTO) authDTO.getCredentials();
        secretKeyRef = SecretRefHelper.getSecretConfigString(certDTO.getClientCertRef());
        break;
      default:
        throw new InvalidRequestException("Invalid Secret type.");
    }
    AzureManualCredential azureManualCredential = AzureManualCredential.builder()
                                                      .tenantId(config.getTenantId())
                                                      .clientId(config.getClientId())
                                                      .secretKeyRef(secretKeyRef)
                                                      .azureSecretType(authDTO.getAzureSecretType())
                                                      .build();
    return AzureConfig.builder()
        .credentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .credential(azureManualCredential)
        .build();
  }
  private static AzureRepoHttpAuth getHttpAuth(
      AzureRepoHttpAuthenticationType type, AzureRepoHttpCredentialsDTO httpCredentialsDTO) {
    switch (type) {
      case USERNAME_AND_TOKEN:
        final AzureRepoUsernameTokenDTO usernameTokenDTO =
            (AzureRepoUsernameTokenDTO) httpCredentialsDTO.getHttpCredentialsSpec();

        return AzureRepoUsernameToken.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(usernameTokenDTO.getTokenRef()))
            .usernameRef(SecretRefHelper.getSecretConfigString(usernameTokenDTO.getUsernameRef()))
            .username(usernameTokenDTO.getUsername())
            .build();
      default:
        throw new UnknownEnumTypeException("Github Http Auth Type", type == null ? null : type.getDisplayName());
    }
  }
}
