/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.impl;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_PROXY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
public class AzureRepoConnectorProcessor extends ConnectorProcessor {
  @Override
  public String getInfraConnectorType(String accountIdentifier, String connectorIdentifier) {
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(accountIdentifier, connectorIdentifier);
    AzureRepoConnectorDTO config = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  @Override
  public Pair<ConnectorInfoDTO, Map<String, BackstageEnvVariable>> getConnectorAndSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(accountIdentifier, connectorIdentifier);
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

    Map<String, BackstageEnvVariable> secrets = new HashMap<>();

    secrets.put(GitIntegrationConstants.AZURE_REPO_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(
            tokenSecretIdentifier, GitIntegrationConstants.AZURE_REPO_TOKEN));
    return new Pair<>(connectorInfoDTO, secrets);
  }

  @Override
  public void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush) {
    Pair<ConnectorInfoDTO, Map<String, BackstageEnvVariable>> connectorSecretsInfo = getConnectorAndSecretsInfo(
        accountIdentifier, null, null, catalogConnectorInfo.getInfraConnector().getIdentifier());
    BackstageEnvSecretVariable envSecretVariable =
        (BackstageEnvSecretVariable) connectorSecretsInfo.getSecond().get(GitIntegrationConstants.AZURE_REPO_TOKEN);
    String azureRepoConnectorSecret = GitIntegrationUtils.decryptSecret(ngSecretService, accountIdentifier, null, null,
        envSecretVariable.getHarnessSecretIdentifier(), catalogConnectorInfo.getSourceConnector().getIdentifier());

    AzureRepoConnectorDTO config = (AzureRepoConnectorDTO) connectorSecretsInfo.getFirst().getConnectorConfig();
    AzureRepoHttpCredentialsOutcomeDTO outcome =
        (AzureRepoHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    AzureRepoUsernameTokenDTO spec = (AzureRepoUsernameTokenDTO) outcome.getSpec();

    performPushOperationInternal(accountIdentifier, catalogConnectorInfo, locationParentPath, filesToPush,
        spec.getUsername(), azureRepoConnectorSecret);
  }
}
