/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.implementation;

import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.gitintegration.GitIntegrationConstants;
import io.harness.idp.gitintegration.baseclass.ConnectorProcessor;
import io.harness.idp.secret.beans.dto.EnvironmentSecretDTO;
import io.harness.idp.secret.beans.dto.EnvironmentSecretValueDTO;
import io.harness.remote.client.NGRestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GithubConnectorProcessor extends ConnectorProcessor {
  public List<EnvironmentSecretValueDTO> getConnectorSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
        connectorResourceClient.get(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    List<EnvironmentSecretValueDTO> resultList = new ArrayList<>();

    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(
          String.format("Github Connector not found for identifier : [%s] ", connectorIdentifier));
    }

    ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.GITHUB_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not github connector ", connectorIdentifier));
    }

    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubApiAccessDTO apiAccess = config.getApiAccess();
    if (apiAccess.getType().toString().equals(GitIntegrationConstants.GITHUB_APP_CONNECTOR_TYPE)) {
      GithubAppSpecDTO apiAccessSpec = (GithubAppSpecDTO) apiAccess.getSpec();

      EnvironmentSecretDTO appIdEnvironmentSecretDTO =
          EnvironmentSecretDTO.builder().envName(GitIntegrationConstants.GITHUB_APP_ID).build();
      EnvironmentSecretValueDTO appIdEnvironmentSecretValueDTO = EnvironmentSecretValueDTO.builder()
                                                                     .environmentSecretDTO(appIdEnvironmentSecretDTO)
                                                                     .decryptedValue(apiAccessSpec.getApplicationId())
                                                                     .build();
      resultList.add(appIdEnvironmentSecretValueDTO);

      EnvironmentSecretDTO privateRefEnvironmentSecretDTO =
          EnvironmentSecretDTO.builder()
              .secretIdentifier(apiAccessSpec.getPrivateKeyRef().getIdentifier())
              .envName(GitIntegrationConstants.GITHUB_APP_PRIVATE_KEY_REF)
              .build();
      EnvironmentSecretValueDTO privateRefEnvironmentSecretValueDTO =
          EnvironmentSecretValueDTO.builder().environmentSecretDTO(privateRefEnvironmentSecretDTO).build();
      resultList.add(privateRefEnvironmentSecretValueDTO);
    }

    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().toString().equals(GitIntegrationConstants.GITHUB_CONNECTOR_AUTH_TYPE)) {
      throw new InvalidRequestException(String.format(
          " Authentication is not Username and Token for Github Connector with id - [%s] ", connectorIdentifier));
    }
    GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();
    EnvironmentSecretDTO tokenEnvironmentSecretDTO = EnvironmentSecretDTO.builder()
                                                         .secretIdentifier(spec.getTokenRef().getIdentifier())
                                                         .envName(GitIntegrationConstants.GITHUB_TOKEN)
                                                         .build();
    EnvironmentSecretValueDTO tokenEnvironmentSecretValueDTO =
        EnvironmentSecretValueDTO.builder().environmentSecretDTO(tokenEnvironmentSecretDTO).build();
    resultList.add(tokenEnvironmentSecretValueDTO);
    return resultList;
  }
}
