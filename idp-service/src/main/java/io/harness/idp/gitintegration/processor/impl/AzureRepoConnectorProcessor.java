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
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoHttpCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class AzureRepoConnectorProcessor extends ConnectorProcessor {
  @Override
  public String getInfraConnectorType(ConnectorInfoDTO connectorInfoDTO) {
    AzureRepoConnectorDTO config = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  @Override
  public Map<String, BackstageEnvVariable> getConnectorAndSecretsInfo(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    String connectorIdentifier = connectorInfoDTO.getIdentifier();
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

    secrets.put(Constants.AZURE_REPO_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(tokenSecretIdentifier, Constants.AZURE_REPO_TOKEN));
    return secrets;
  }

  @Override
  public void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush, boolean throughGrpc) {
    ConnectorInfoDTO connectorInfoDTO =
        getConnectorInfo(accountIdentifier, catalogConnectorInfo.getConnector().getIdentifier());
    Map<String, BackstageEnvVariable> connectorSecretsInfo =
        getConnectorAndSecretsInfo(accountIdentifier, connectorInfoDTO);
    BackstageEnvSecretVariable envSecretVariable =
        (BackstageEnvSecretVariable) connectorSecretsInfo.get(Constants.AZURE_REPO_TOKEN);
    String azureRepoConnectorSecret = GitIntegrationUtils.decryptSecret(ngSecretService, accountIdentifier, null, null,
        envSecretVariable.getHarnessSecretIdentifier(), catalogConnectorInfo.getConnector().getIdentifier());

    AzureRepoConnectorDTO config = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    AzureRepoHttpCredentialsOutcomeDTO outcome =
        (AzureRepoHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    AzureRepoUsernameTokenDTO spec = (AzureRepoUsernameTokenDTO) outcome.getSpec();

    config.setUrl(catalogConnectorInfo.getRepo());

    performPushOperationInternal(accountIdentifier, catalogConnectorInfo, locationParentPath, filesToPush,
        spec.getUsername(), azureRepoConnectorSecret, config, throughGrpc);
  }

  @Override
  public GitConfigDTO getGitConfigFromConnectorConfig(ConnectorConfigDTO connectorConfig) {
    return AzureRepoToGitMapper.mapToGitConfigDTO((AzureRepoConnectorDTO) connectorConfig);
  }

  @Override
  public String getLocationTarget(CatalogConnectorInfo catalogConnectorInfo, String path) {
    return catalogConnectorInfo.getRepo() + "?path=" + path + "&version=GB" + catalogConnectorInfo.getBranch();
  }
}
