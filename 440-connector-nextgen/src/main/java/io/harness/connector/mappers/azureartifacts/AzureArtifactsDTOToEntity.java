/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.azureartifacts;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azureartifacts.AzureArtifactsAuthentication;
import io.harness.connector.entities.embedded.azureartifacts.AzureArtifactsConnector;
import io.harness.connector.entities.embedded.azureartifacts.AzureArtifactsTokenCredentials;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactsDTOToEntity
    implements ConnectorDTOToEntityMapper<AzureArtifactsConnectorDTO, AzureArtifactsConnector> {
  @Override
  public AzureArtifactsConnector toConnectorEntity(AzureArtifactsConnectorDTO configDTO) {
    if (configDTO == null) {
      throw new InvalidRequestException("AzureArtifacts Config DTO is not found");
    }

    if (configDTO.getAuth() == null) {
      throw new InvalidRequestException("No Authentication Details Found in the ConnectorDisconnectHandler");
    }

    AzureArtifactsAuthentication azureArtifactsAuthentication =
        buildAuthenticationDetails(configDTO.getAuth().getCredentials());

    return AzureArtifactsConnector.builder()
        .authenticationDetails(azureArtifactsAuthentication)
        .azureArtifactsUrl(configDTO.getAzureArtifactsUrl())
        .build();
  }

  public AzureArtifactsAuthentication buildAuthenticationDetails(AzureArtifactsCredentialsDTO httpCredentialsDTO) {
    final AzureArtifactsAuthenticationType type = httpCredentialsDTO.getType();

    return AzureArtifactsAuthentication.builder().type(type).auth(getHttpAuth(type, httpCredentialsDTO)).build();
  }

  private AzureArtifactsTokenCredentials getHttpAuth(
      AzureArtifactsAuthenticationType type, AzureArtifactsCredentialsDTO credentialsDTO) {
    if (type == null) {
      throw new InvalidRequestException("AzureArtifacts Auth Type not found");
    }

    switch (type) {
      case PERSONAL_ACCESS_TOKEN:
        final AzureArtifactsTokenDTO azureArtifactsTokenDTO = credentialsDTO.getCredentialsSpec();

        return AzureArtifactsTokenCredentials.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(azureArtifactsTokenDTO.getTokenRef()))
            .build();

      default:
        throw new UnknownEnumTypeException("AzureArtifacts Auth Type ", type.getDisplayName());
    }
  }
}
