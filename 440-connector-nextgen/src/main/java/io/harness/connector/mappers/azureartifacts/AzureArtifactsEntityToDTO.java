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
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactsEntityToDTO
    implements ConnectorEntityToDTOMapper<AzureArtifactsConnectorDTO, AzureArtifactsConnector> {
  @Override
  public AzureArtifactsConnectorDTO createConnectorDTO(AzureArtifactsConnector connector) {
    if (connector == null) {
      throw new InvalidRequestException("Connector object not found");
    }

    AzureArtifactsAuthenticationDTO azureAuthenticationDTO =
        buildAzureArtifactsAuthentication(connector.getAuthenticationDetails());

    return AzureArtifactsConnectorDTO.builder()
        .auth(azureAuthenticationDTO)
        .azureArtifactsUrl(connector.getAzureArtifactsUrl())
        .build();
  }

  public AzureArtifactsAuthenticationDTO buildAzureArtifactsAuthentication(
      AzureArtifactsAuthentication azureArtifactsAuthentication) {
    final AzureArtifactsAuthenticationType type = azureArtifactsAuthentication.getType();

    final AzureArtifactsTokenCredentials auth = azureArtifactsAuthentication.getAuth();

    AzureArtifactsTokenDTO tokenDTO = getHttpCredentialsSpecDTO(type, auth);

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder().type(type).credentialsSpec(tokenDTO).build();

    return AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();
  }

  private AzureArtifactsTokenDTO getHttpCredentialsSpecDTO(AzureArtifactsAuthenticationType type, Object auth) {
    AzureArtifactsTokenDTO tokenDTO = null;

    if (type == null) {
      throw new InvalidRequestException("AzureArtifacts Auth Type not found");
    }

    switch (type) {
      case PERSONAL_ACCESS_TOKEN:
        final AzureArtifactsTokenCredentials tokenCredentials = (AzureArtifactsTokenCredentials) auth;

        tokenDTO = AzureArtifactsTokenDTO.builder()
                       .tokenRef(SecretRefHelper.createSecretRef(tokenCredentials.getTokenRef()))
                       .build();

        break;

      default:
        throw new UnknownEnumTypeException("AzureArtifacts Auth Type", type.getDisplayName());
    }

    return tokenDTO;
  }
}
