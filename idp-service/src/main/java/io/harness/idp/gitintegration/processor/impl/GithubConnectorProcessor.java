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
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
public class GithubConnectorProcessor extends ConnectorProcessor {
  @Override
  public String getInfraConnectorType(String accountIdentifier, String connectorIdentifier) {
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(accountIdentifier, connectorIdentifier);
    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  public Pair<ConnectorInfoDTO, List<EnvironmentSecret>> getConnectorAndSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(accountIdentifier, connectorIdentifier);
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.GITHUB_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not github connector ", connectorIdentifier));
    }

    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubApiAccessDTO apiAccess = config.getApiAccess();

    List<EnvironmentSecret> resultList = new ArrayList<>();

    if (apiAccess != null && apiAccess.getType().toString().equals(GitIntegrationConstants.GITHUB_APP_CONNECTOR_TYPE)) {
      GithubAppSpecDTO apiAccessSpec = (GithubAppSpecDTO) apiAccess.getSpec();

      EnvironmentSecret appIDEnvironmentSecret = new EnvironmentSecret();
      appIDEnvironmentSecret.setEnvName(GitIntegrationConstants.GITHUB_APP_ID);
      appIDEnvironmentSecret.setDecryptedValue(apiAccessSpec.getApplicationId());
      resultList.add(appIDEnvironmentSecret);

      String privateRefKeySecretIdentifier = apiAccessSpec.getPrivateKeyRef().getIdentifier();
      resultList.add(
          GitIntegrationUtils.getEnvironmentSecret(ngSecretService, accountIdentifier, orgIdentifier, projectIdentifier,
              privateRefKeySecretIdentifier, connectorIdentifier, GitIntegrationConstants.GITHUB_APP_PRIVATE_KEY_REF));
    }

    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().toString().equals(GitIntegrationConstants.USERNAME_TOKEN_AUTH_TYPE)) {
      throw new InvalidRequestException(String.format(
          " Authentication is not Username and Token for Github Connector with id - [%s] ", connectorIdentifier));
    }
    GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();

    String tokenSecretIdentifier = spec.getTokenRef().getIdentifier();
    if (tokenSecretIdentifier.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Secret identifier not found for connector: [%s] ", connectorIdentifier));
    }

    resultList.add(GitIntegrationUtils.getEnvironmentSecret(ngSecretService, accountIdentifier, orgIdentifier,
        projectIdentifier, tokenSecretIdentifier, connectorIdentifier, GitIntegrationConstants.GITHUB_TOKEN));
    return new Pair<>(connectorInfoDTO, resultList);
  }

  public void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, String remoteFolder, List<String> filesToPush) {
    Pair<ConnectorInfoDTO, List<EnvironmentSecret>> connectorSecretsInfo = getConnectorAndSecretsInfo(
        accountIdentifier, null, null, catalogConnectorInfo.getSourceConnector().getIdentifier());
    String githubConnectorSecret = connectorSecretsInfo.getSecond().get(0).getDecryptedValue();

    GithubConnectorDTO config = (GithubConnectorDTO) connectorSecretsInfo.getFirst().getConnectorConfig();
    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();

    performPushOperationInternal(accountIdentifier, catalogConnectorInfo, locationParentPath, remoteFolder, filesToPush,
        spec.getUsername(), githubConnectorSecret);
  }
}
