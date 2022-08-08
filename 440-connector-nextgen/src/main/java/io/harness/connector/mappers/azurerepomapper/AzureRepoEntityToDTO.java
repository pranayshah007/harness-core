/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.azurerepomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnectionType;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnector;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuth;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoSshAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoTokenApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoUsernameToken;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

import com.google.common.annotations.VisibleForTesting;

@OwnedBy(HarnessTeam.PL)
public class AzureRepoEntityToDTO implements ConnectorEntityToDTOMapper<AzureRepoConnectorDTO, AzureRepoConnector> {
  @Override
  public AzureRepoConnectorDTO createConnectorDTO(AzureRepoConnector connector) {
    if (connector == null) {
      throw new InvalidRequestException("Connector object not found");
    }
    AzureRepoAuthenticationDTO azureAuthenticationDTO =
        buildAzureRepoAuthentication(connector.getAuthType(), connector.getAuthenticationDetails());
    AzureRepoApiAccessDTO azureApiAccess = null;
    if (connector.isHasApiAccess()) {
      azureApiAccess = buildApiAccess(connector);
    }
    return AzureRepoConnectorDTO.builder()
        .apiAccess(azureApiAccess)
        .connectionType(getAzureRepoConnectionTypeDTO(connector.getConnectionType()))
        .authentication(azureAuthenticationDTO)
        .url(connector.getUrl())
        .validationRepo(connector.getValidationRepo())
        .build();
  }

  public AzureRepoAuthenticationDTO buildAzureRepoAuthentication(
      GitAuthType authType, AzureRepoAuthentication authenticationDetails) {
    AzureRepoCredentialsDTO azureCredentialsDTO = null;
    if (authType == null) {
      throw new InvalidRequestException("AzureRepo Auth Type not found");
    }
    switch (authType) {
      case SSH:
        final AzureRepoSshAuthentication azureSshAuthentication = (AzureRepoSshAuthentication) authenticationDetails;
        azureCredentialsDTO = AzureRepoSshCredentialsDTO.builder()
                                  .sshKeyRef(SecretRefHelper.createSecretRef(azureSshAuthentication.getSshKeyRef()))
                                  .build();
        break;
      case HTTP:
        final AzureRepoHttpAuthentication azureHttpAuthentication = (AzureRepoHttpAuthentication) authenticationDetails;
        final AzureRepoHttpAuthenticationType type = azureHttpAuthentication.getType();
        final AzureRepoHttpAuth auth = azureHttpAuthentication.getAuth();
        AzureRepoHttpCredentialsSpecDTO azureHttpCredentialsSpecDTO = getHttpCredentialsSpecDTO(type, auth);
        azureCredentialsDTO =
            AzureRepoHttpCredentialsDTO.builder().type(type).httpCredentialsSpec(azureHttpCredentialsSpecDTO).build();
        break;
      default:
        throw new UnknownEnumTypeException("AzureRepo Auth Type", authType.getDisplayName());
    }
    return AzureRepoAuthenticationDTO.builder().authType(authType).credentials(azureCredentialsDTO).build();
  }

  private AzureRepoHttpCredentialsSpecDTO getHttpCredentialsSpecDTO(AzureRepoHttpAuthenticationType type, Object auth) {
    AzureRepoHttpCredentialsSpecDTO azureHttpCredentialsSpecDTO = null;
    if (type == null) {
      throw new InvalidRequestException("AzureRepo Http Auth Type not found");
    }
    switch (type) {
      case USERNAME_AND_TOKEN:
        final AzureRepoUsernameToken usernameToken = (AzureRepoUsernameToken) auth;
        SecretRefData usernameReference = null;
        if (usernameToken.getUsernameRef() != null) {
          usernameReference = SecretRefHelper.createSecretRef(usernameToken.getUsernameRef());
        }
        azureHttpCredentialsSpecDTO = AzureRepoUsernameTokenDTO.builder()
                                          .username(usernameToken.getUsername())
                                          .usernameRef(usernameReference)
                                          .tokenRef(SecretRefHelper.createSecretRef(usernameToken.getTokenRef()))
                                          .build();
        break;
      default:
        throw new UnknownEnumTypeException("AzureRepo Http Auth Type", type.getDisplayName());
    }
    return azureHttpCredentialsSpecDTO;
  }

  private AzureRepoApiAccessDTO buildApiAccess(AzureRepoConnector connector) {
    final AzureRepoApiAccessType apiAccessType = connector.getApiAccessType();
    AzureRepoApiAccessSpecDTO apiAccessSpecDTO = null;
    if (apiAccessType == null) {
      throw new InvalidRequestException("AzureRepo Api Access Type not found");
    }
    switch (apiAccessType) {
      case TOKEN:
        final AzureRepoTokenApiAccess azureTokenApiAccess = (AzureRepoTokenApiAccess) connector.getAzureRepoApiAccess();
        apiAccessSpecDTO = AzureRepoTokenSpecDTO.builder()
                               .tokenRef(SecretRefHelper.createSecretRef(azureTokenApiAccess.getTokenRef()))
                               .build();
        break;
      default:
        throw new UnknownEnumTypeException("AzureRepo Api Access Type", apiAccessType.getDisplayName());
    }
    return AzureRepoApiAccessDTO.builder().type(apiAccessType).spec(apiAccessSpecDTO).build();
  }

  @VisibleForTesting
  public AzureRepoConnectionTypeDTO getAzureRepoConnectionTypeDTO(AzureRepoConnectionType connectionType) {
    switch (connectionType) {
      case PROJECT:
        return AzureRepoConnectionTypeDTO.PROJECT;
      case REPO:
        return AzureRepoConnectionTypeDTO.REPO;
      default:
        throw new UnknownEnumTypeException("AzureRepo Connection Type ", connectionType.name());
    }
  }
}
