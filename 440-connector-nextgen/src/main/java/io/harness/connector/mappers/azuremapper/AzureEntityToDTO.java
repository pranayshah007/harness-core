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
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
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
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.govern.Switch;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureEntityToDTO implements ConnectorEntityToDTOMapper<AzureConnectorDTO, AzureConfig> {
  @Override
  public AzureConnectorDTO createConnectorDTO(AzureConfig connector) {
    final AzureCredentialType credentialType = connector.getCredentialType();
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        return buildInheritFromDelegate(connector);
      case MANUAL_CREDENTIALS:
        return buildManualCredential(connector);
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
  }

  public static AzureRepoAuthenticationDTO buildAzureAuthentication(
      GitAuthType authType, AzureRepoAuthentication authenticationDetails) {
    AzureRepoCredentialsDTO credentialsDTO = null;
    switch (authType) {
      case SSH:
        final AzureRepoSshAuthentication githubSshAuthentication = (AzureRepoSshAuthentication) authenticationDetails;
        credentialsDTO = AzureRepoSshCredentialsDTO.builder()
                             .sshKeyRef(SecretRefHelper.createSecretRef(githubSshAuthentication.getSshKeyRef()))
                             .build();
        break;
      case HTTP:
        final AzureRepoHttpAuthentication httpAuthentication = (AzureRepoHttpAuthentication) authenticationDetails;
        final AzureRepoHttpAuthenticationType type = httpAuthentication.getType();
        final AzureRepoHttpAuth auth = httpAuthentication.getAuth();
        AzureRepoHttpCredentialsSpecDTO httpCredentialsSpecDTO = getHttpCredentialsSpecDTO(type, auth);
        credentialsDTO =
            AzureRepoHttpCredentialsDTO.builder().type(type).httpCredentialsSpec(httpCredentialsSpecDTO).build();
        break;
      default:
        Switch.unhandled(authType);
    }
    return AzureRepoAuthenticationDTO.builder().authType(authType).credentials(credentialsDTO).build();
  }

  private AzureConnectorDTO buildManualCredential(AzureConfig connector) {
    final AzureManualCredential auth = (AzureManualCredential) connector.getCredential();
    final SecretRefData secretRef = SecretRefHelper.createSecretRef(auth.getSecretKeyRef());
    final AzureAuthDTO azureAuthDTO = buildAzureAuthDTO(secretRef, auth.getAzureSecretType());
    final AzureManualDetailsDTO azureManualDetailsDTO = AzureManualDetailsDTO.builder()
                                                            .clientId(auth.getClientId())
                                                            .tenantId(auth.getTenantId())
                                                            .authDTO(azureAuthDTO)
                                                            .build();
    return AzureConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .azureEnvironmentType(connector.getAzureEnvironmentType())
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                        .config(azureManualDetailsDTO)
                        .build())
        .build();
  }

  private AzureAuthDTO buildAzureAuthDTO(SecretRefData secretRef, AzureSecretType azureSecretType) {
    AzureAuthDTO azureAuthDTO = AzureAuthDTO.builder().azureSecretType(azureSecretType).build();
    switch (azureSecretType) {
      case SECRET_KEY:
        azureAuthDTO.setCredentials(AzureClientSecretKeyDTO.builder().secretKey(secretRef).build());
        break;
      case KEY_CERT:
        azureAuthDTO.setCredentials(AzureClientKeyCertDTO.builder().clientCertRef(secretRef).build());
        break;
      default:
        throw new InvalidRequestException("Invalid secret type.");
    }
    return azureAuthDTO;
  }

  private AzureConnectorDTO buildInheritFromDelegate(AzureConfig connector) {
    final AzureManagedIdentityCredential auth = (AzureManagedIdentityCredential) connector.getCredential();
    final AzureManagedIdentityType azureManagedIdentityType = auth.getAzureManagedIdentityType();

    AzureMSIAuthDTO azureMSIAuthDTO;
    switch (azureManagedIdentityType) {
      case USER_ASSIGNED_MANAGED_IDENTITY: {
        azureMSIAuthDTO = AzureMSIAuthUADTO.builder()
                              .azureManagedIdentityType(azureManagedIdentityType)
                              .credentials(AzureUserAssignedMSIAuthDTO.builder().clientId(auth.getClientId()).build())
                              .build();
        break;
      }
      case SYSTEM_ASSIGNED_MANAGED_IDENTITY: {
        azureMSIAuthDTO = AzureMSIAuthSADTO.builder().azureManagedIdentityType(azureManagedIdentityType).build();
        break;
      }
      default: {
        throw new InvalidRequestException("Invalid ManagedIdentity credentials type.");
      }
    }

    return AzureConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .azureEnvironmentType(connector.getAzureEnvironmentType())
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                        .config(AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build())
                        .build())
        .build();
  }

  private static AzureRepoHttpCredentialsSpecDTO getHttpCredentialsSpecDTO(
      AzureRepoHttpAuthenticationType type, Object auth) {
    AzureRepoHttpCredentialsSpecDTO httpCredentialsSpecDTO = null;
    switch (type) {
      case USERNAME_AND_TOKEN:
        final AzureRepoUsernameToken usernameToken = (AzureRepoUsernameToken) auth;
        SecretRefData usernameReference = null;
        if (usernameToken.getUsernameRef() != null) {
          usernameReference = SecretRefHelper.createSecretRef(usernameToken.getUsernameRef());
        }
        httpCredentialsSpecDTO = AzureRepoUsernameTokenDTO.builder()
                                     .username(usernameToken.getUsername())
                                     .usernameRef(usernameReference)
                                     .tokenRef(SecretRefHelper.createSecretRef(usernameToken.getTokenRef()))
                                     .build();
        break;
      default:
        Switch.unhandled(type);
    }
    return httpCredentialsSpecDTO;
  }
}
