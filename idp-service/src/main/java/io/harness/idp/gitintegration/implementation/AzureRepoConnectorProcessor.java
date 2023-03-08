/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.gitintegration.implementation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.gitintegration.GitIntegrationConstants;
import io.harness.idp.gitintegration.GitIntegrationUtil;
import io.harness.idp.gitintegration.baseclass.ConnectorProcessor;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.IDP)
public class AzureRepoConnectorProcessor extends ConnectorProcessor {
  @Override
  public List<EnvironmentSecret> getConnectorSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
        connectorResourceClient.get(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    List<EnvironmentSecret> resultList = new ArrayList<>();

    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(
          String.format("AzureRepo Connector not found for identifier : [%s] ", connectorIdentifier));
    }

    ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.AZURE_REPO_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not AzureRepo connector for accountId: [%s]", connectorIdentifier,
              accountIdentifier));
    }

    AzureRepoConnectorDTO config = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    AzureRepoHttpCredentialsOutcomeDTO outcome =
        (AzureRepoHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().toString().equals(GitIntegrationConstants.USERNAME_TOKEN_AUTH_TYPE)) {
      throw new InvalidRequestException(String.format(
          " Authentication is not Username and Token for AzureRepo Connector with id - [%s], accountId: [%s]",
          connectorIdentifier, accountIdentifier));
    }

    AzureRepoUsernameTokenDTO spec = (AzureRepoUsernameTokenDTO) outcome.getSpec();
    String tokenSecretIdentifier = spec.getTokenRef().getIdentifier();
    if (tokenSecretIdentifier.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Secret identifier not found for connector: [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }
    resultList.add(GitIntegrationUtil.getEnvironmentSecret(ngSecretService, accountIdentifier, orgIdentifier,
        projectIdentifier, tokenSecretIdentifier, connectorIdentifier, GitIntegrationConstants.AZURE_REPO_TOKEN));
    return resultList;
  }
}
