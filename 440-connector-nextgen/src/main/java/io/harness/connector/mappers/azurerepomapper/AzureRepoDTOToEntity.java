/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.azurerepomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnectionType;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnector;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuth;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoSshAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoTokenApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoUsernameToken;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
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
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

import com.google.common.annotations.VisibleForTesting;

@OwnedBy(HarnessTeam.PL)
public class AzureRepoDTOToEntity implements ConnectorDTOToEntityMapper<AzureRepoConnectorDTO, AzureRepoConnector> {
  @Override
  public AzureRepoConnector toConnectorEntity(AzureRepoConnectorDTO configDTO) {
    if (configDTO == null) {
      throw new InvalidRequestException("AzureRepo Config DTO is not found");
    }
    if (configDTO.getAuthentication() == null) {
      throw new InvalidRequestException("No Authentication Details Found in the ConnectorDisconnectHandler");
    }
    GitAuthType gitAuthType = getAuthType(configDTO.getAuthentication());
    AzureRepoAuthentication azureRepoAuthentication =
        buildAuthenticationDetails(gitAuthType, configDTO.getAuthentication().getCredentials());
    boolean hasApiAccess = hasApiAccess(configDTO.getApiAccess());
    AzureRepoApiAccessType apiAccessType = null;
    AzureRepoApiAccess azureRepoApiAccess = null;
    if (hasApiAccess) {
      apiAccessType = getApiAccessType(configDTO.getApiAccess());
      azureRepoApiAccess = getApiAcessByType(configDTO.getApiAccess().getSpec(), apiAccessType);
    }
    return AzureRepoConnector.builder()
        .connectionType(getAzureRepoConnectionType(configDTO.getConnectionType()))
        .authType(gitAuthType)
        .hasApiAccess(hasApiAccess)
        .apiAccessType(apiAccessType)
        .authenticationDetails(azureRepoAuthentication)
        .azureRepoApiAccess(azureRepoApiAccess)
        .url(configDTO.getUrl())
        .validationRepo(configDTO.getValidationRepo())
        .build();
  }

  public AzureRepoAuthentication buildAuthenticationDetails(
      GitAuthType gitAuthType, AzureRepoCredentialsDTO credentialsDTO) {
    if (gitAuthType == null) {
      throw new InvalidRequestException("Auth Type not found");
    }
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
        throw new UnknownEnumTypeException("AzureRepo Auth Type ", gitAuthType.getDisplayName());
    }
  }

  private AzureRepoHttpAuth getHttpAuth(
      AzureRepoHttpAuthenticationType type, AzureRepoHttpCredentialsDTO httpCredentialsDTO) {
    if (type == null) {
      throw new InvalidRequestException("AzureRepo Http Auth Type not found");
    }
    switch (type) {
      case USERNAME_AND_TOKEN:
        final AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
            (AzureRepoUsernameTokenDTO) httpCredentialsDTO.getHttpCredentialsSpec();
        String usernameReference = getUsernameRefFromSecret(azureRepoUsernameTokenDTO.getUsernameRef());
        return AzureRepoUsernameToken.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(azureRepoUsernameTokenDTO.getTokenRef()))
            .username(azureRepoUsernameTokenDTO.getUsername())
            .usernameRef(usernameReference)
            .build();
      default:
        throw new UnknownEnumTypeException("AzureRepo Http Auth Type ", type.getDisplayName());
    }
  }

  private String getUsernameRefFromSecret(SecretRefData secretRefData) {
    String usernameRef = null;
    if (secretRefData != null) {
      usernameRef = SecretRefHelper.getSecretConfigString(secretRefData);
    }
    return usernameRef;
  }

  private AzureRepoApiAccess getApiAcessByType(AzureRepoApiAccessSpecDTO spec, AzureRepoApiAccessType apiAccessType) {
    if (apiAccessType == null) {
      throw new InvalidRequestException("AzureRepo Api Access Type not found");
    }
    switch (apiAccessType) {
      case TOKEN:
        final AzureRepoTokenSpecDTO tokenSpec = (AzureRepoTokenSpecDTO) spec;
        return AzureRepoTokenApiAccess.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(tokenSpec.getTokenRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("AzureRepo Api Access Type ", apiAccessType.getDisplayName());
    }
  }

  private AzureRepoApiAccessType getApiAccessType(AzureRepoApiAccessDTO apiAccess) {
    return apiAccess.getType();
  }

  private boolean hasApiAccess(AzureRepoApiAccessDTO apiAccess) {
    return apiAccess != null;
  }

  private GitAuthType getAuthType(AzureRepoAuthenticationDTO authentication) {
    return authentication.getAuthType();
  }

  @VisibleForTesting
  public AzureRepoConnectionType getAzureRepoConnectionType(AzureRepoConnectionTypeDTO connectionType) {
    switch (connectionType) {
      case PROJECT:
        return AzureRepoConnectionType.PROJECT;
      case REPO:
        return AzureRepoConnectionType.REPO;
      default:
        throw new UnknownEnumTypeException("AzureRepo Connection Type ", connectionType.name());
    }
  }
}
