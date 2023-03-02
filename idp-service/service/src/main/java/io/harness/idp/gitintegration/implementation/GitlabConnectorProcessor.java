/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.implementation;

import io.harness.beans.DecryptedSecretValue;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.gitintegration.GitIntegrationConstants;
import io.harness.idp.gitintegration.baseclass.ConnectorProcessor;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GitlabConnectorProcessor extends ConnectorProcessor {
  @Override
  public List<EnvironmentSecret> getConnectorSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
        connectorResourceClient.get(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    List<EnvironmentSecret> resultList = new ArrayList<>();

    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Gitlab Connector not found for identifier: [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }

    ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.GITLAB_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not gitlab connector for accountId: [%s]", connectorIdentifier,
              accountIdentifier));
    }

    GitlabConnectorDTO config = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GitlabHttpCredentialsOutcomeDTO outcome =
        (GitlabHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().toString().equals(GitIntegrationConstants.USERNAME_TOKEN_AUTH_TYPE)) {
      throw new InvalidRequestException(String.format(
          " Authentication is not Username and Token for Gitlab Connector with id - [%s], accountId: [%s]",
          connectorIdentifier, accountIdentifier));
    }

    GitlabUsernameTokenDTO spec = (GitlabUsernameTokenDTO) outcome.getSpec();
    String tokenSecretIdentifier = spec.getTokenRef().getIdentifier();
    if (tokenSecretIdentifier.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Secret identifier not found for connector: [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }

    EnvironmentSecret tokenEnvironmentSecret = new EnvironmentSecret();
    tokenEnvironmentSecret.secretIdentifier(tokenSecretIdentifier);
    tokenEnvironmentSecret.setEnvName(GitIntegrationConstants.GITLAB_TOKEN);
    DecryptedSecretValue tokenDecryptedSecretValue = ngSecretService.getDecryptedSecretValue(
        accountIdentifier, orgIdentifier, projectIdentifier, tokenSecretIdentifier);
    if (tokenDecryptedSecretValue == null) {
      throw new InvalidRequestException(String.format(
          "Token Secret not found for identifier : [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }
    tokenEnvironmentSecret.setDecryptedValue(tokenDecryptedSecretValue.getDecryptedValue());
    resultList.add(tokenEnvironmentSecret);
    return resultList;
  }
}
