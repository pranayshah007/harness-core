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
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabHttpCredentialsOutcomeDTO;
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
public class GitlabConnectorProcessor extends ConnectorProcessor {
  @Override
  public String getInfraConnectorType(ConnectorInfoDTO connectorInfoDTO) {
    GitlabConnectorDTO config = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  @Override
  public Map<String, BackstageEnvVariable> getConnectorAndSecretsInfo(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    String connectorIdentifier = connectorInfoDTO.getIdentifier();
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

    Map<String, BackstageEnvVariable> secrets = new HashMap<>();

    secrets.put(Constants.GITLAB_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(tokenSecretIdentifier, Constants.GITLAB_TOKEN));
    return secrets;
  }

  @Override
  public void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush) {
    ConnectorInfoDTO connectorInfoDTO =
        getConnectorInfo(accountIdentifier, catalogConnectorInfo.getConnector().getIdentifier());
    Map<String, BackstageEnvVariable> connectorSecretsInfo =
        getConnectorAndSecretsInfo(accountIdentifier, connectorInfoDTO);
    BackstageEnvSecretVariable envSecretVariable =
        (BackstageEnvSecretVariable) connectorSecretsInfo.get(Constants.GITLAB_TOKEN);
    String gitlabConnectorSecret = GitIntegrationUtils.decryptSecret(ngSecretService, accountIdentifier, null, null,
        envSecretVariable.getHarnessSecretIdentifier(), catalogConnectorInfo.getConnector().getIdentifier());

    GitlabConnectorDTO config = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GitlabHttpCredentialsOutcomeDTO outcome =
        (GitlabHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    GitlabUsernameTokenDTO spec = (GitlabUsernameTokenDTO) outcome.getSpec();

    performPushOperationInternal(accountIdentifier, catalogConnectorInfo, locationParentPath, filesToPush,
        spec.getUsername(), gitlabConnectorSecret);
  }
}
