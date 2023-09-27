/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.impl;

import static io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType.GITHUB_APP;
import static io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType.USERNAME_AND_TOKEN;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;
import static io.harness.idp.common.Constants.SOURCE_FORMAT;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_PROXY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.adapter.GithubToGitMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class GithubConnectorProcessor extends ConnectorProcessor {
  private static final String SUFFIX_FOR_GITHUB_APP_CONNECTOR = "_App";
  private static final String TARGET_TO_REPLACE_IN_CONFIG_FOR_GITHUB_API_BASE_URL = "API_BASE_URL";
  @Override
  public String getInfraConnectorType(ConnectorInfoDTO connectorInfoDTO) {
    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  public Map<String, BackstageEnvVariable> getConnectorAndSecretsInfo(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    String connectorIdentifier = connectorInfoDTO.getIdentifier();
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.GITHUB_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not github connector ", connectorIdentifier));
    }

    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubApiAccessDTO apiAccess = config.getApiAccess();
    String authType = String.valueOf(config.getAuthentication().getAuthType());
    if (authType.equals("Ssh")) {
      throw new InvalidRequestException(
          String.format("Connector type Ssh not allowed for Github connector with Id - : [%s] ", connectorIdentifier));
    }

    Map<String, BackstageEnvVariable> secrets = new HashMap<>();

    if (apiAccess != null && apiAccess.getType().toString().equals(GitIntegrationConstants.GITHUB_APP_CONNECTOR_TYPE)) {
      GithubAppSpecDTO apiAccessSpec = (GithubAppSpecDTO) apiAccess.getSpec();

      if (apiAccessSpec.getApplicationIdRef() == null) {
        BackstageEnvConfigVariable appIDEnvironmentSecret = new BackstageEnvConfigVariable();
        appIDEnvironmentSecret.setEnvName(Constants.GITHUB_APP_ID);
        appIDEnvironmentSecret.setType(BackstageEnvVariable.TypeEnum.CONFIG);
        appIDEnvironmentSecret.value(apiAccessSpec.getApplicationId());
        secrets.put(Constants.GITHUB_APP_ID, appIDEnvironmentSecret);
      } else {
        String applicationIdSecretRefId = apiAccessSpec.getApplicationIdRef().getIdentifier();
        secrets.put(Constants.GITHUB_APP_ID,
            GitIntegrationUtils.getBackstageEnvSecretVariable(applicationIdSecretRefId, Constants.GITHUB_APP_ID));
      }

      String privateRefKeySecretIdentifier = apiAccessSpec.getPrivateKeyRef().getIdentifier();
      secrets.put(Constants.GITHUB_APP_PRIVATE_KEY_REF,
          GitIntegrationUtils.getBackstageEnvSecretVariable(
              privateRefKeySecretIdentifier, Constants.GITHUB_APP_PRIVATE_KEY_REF));
    }

    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().equals(USERNAME_AND_TOKEN) && !outcome.getType().equals(GITHUB_APP)) {
      throw new InvalidRequestException(String.format(
          "AuthenticationType should be Username + Token / GithubApp for Github Connector with id - [%s] ",
          connectorIdentifier));
    }

    String tokenSecretIdentifier = "";
    if (outcome.getType().equals(USERNAME_AND_TOKEN)) {
      GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();
      tokenSecretIdentifier = spec.getTokenRef().getIdentifier();
    } else {
      GithubAppDTO spec = (GithubAppDTO) outcome.getSpec();
      tokenSecretIdentifier = spec.getPrivateKeyRef().getIdentifier();
    }

    if (tokenSecretIdentifier.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Secret identifier not found for connector: [%s] ", connectorIdentifier));
    }

    secrets.put(Constants.GITHUB_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(tokenSecretIdentifier, Constants.GITHUB_TOKEN));

    return secrets;
  }

  @Override
  public void createOrUpdateIntegrationConfig(String accountIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubApiAccessDTO apiAccess = config.getApiAccess();
    String connectorTypeAsString = connectorInfoDTO.getConnectorType().toString();

    if (apiAccess != null && apiAccess.getType().toString().equals(GitIntegrationConstants.GITHUB_APP_CONNECTOR_TYPE)) {
      connectorTypeAsString = connectorTypeAsString + SUFFIX_FOR_GITHUB_APP_CONNECTOR;
    }

    // config for github connector git integration
    String integrationConfigs = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(connectorTypeAsString);
    integrationConfigs = integrationConfigs.replace(TARGET_TO_REPLACE_IN_CONFIG_FOR_GITHUB_API_BASE_URL,
        getGithubApiBaseUrlFromHost(GitIntegrationUtils.getHostForConnector(connectorInfoDTO)));
    configManagerService.createOrUpdateAppConfigForGitIntegrations(
        accountIdentifier, connectorInfoDTO, integrationConfigs, connectorTypeAsString);
  }

  public void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush, boolean throughGrpc) {
    ConnectorInfoDTO connectorInfoDTO =
        getConnectorInfo(accountIdentifier, catalogConnectorInfo.getConnector().getIdentifier());
    Map<String, BackstageEnvVariable> connectorSecretsInfo =
        getConnectorAndSecretsInfo(accountIdentifier, connectorInfoDTO);
    BackstageEnvSecretVariable envSecretVariable =
        (BackstageEnvSecretVariable) connectorSecretsInfo.get(Constants.GITHUB_TOKEN);
    String githubConnectorSecret = GitIntegrationUtils.decryptSecret(ngSecretService, accountIdentifier, null, null,
        envSecretVariable.getHarnessSecretIdentifier(), catalogConnectorInfo.getConnector().getIdentifier());

    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubHttpCredentialsOutcomeDTO outcome =
        (GithubHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();

    String username = "HarnessIdp";
    if (outcome.getType().equals(USERNAME_AND_TOKEN)) {
      GithubUsernameTokenDTO spec = (GithubUsernameTokenDTO) outcome.getSpec();
      username = StringUtils.isEmpty(spec.getUsername()) ? username : spec.getUsername();
    } else if (outcome.getType().equals(GITHUB_APP)) {
      GithubAppDTO spec = (GithubAppDTO) outcome.getSpec();
      username = StringUtils.isEmpty(spec.getApplicationId()) && StringUtils.isEmpty(spec.getInstallationId())
          ? username + "-GithubApp"
          : username + "-GithubApp-" + spec.getApplicationId() + "-" + spec.getInstallationId();
    }

    config.setUrl(catalogConnectorInfo.getRepo());

    performPushOperationInternal(accountIdentifier, catalogConnectorInfo, locationParentPath, filesToPush, username,
        githubConnectorSecret, config, throughGrpc);
  }

  @Override
  public GitConfigDTO getGitConfigFromConnectorConfig(ConnectorConfigDTO connectorConfig) {
    return GithubToGitMapper.mapToGitConfigDTO((GithubConnectorDTO) connectorConfig);
  }

  @Override
  public String getLocationTarget(CatalogConnectorInfo catalogConnectorInfo, String path) {
    return catalogConnectorInfo.getRepo() + SLASH_DELIMITER + SOURCE_FORMAT + SLASH_DELIMITER
        + catalogConnectorInfo.getBranch() + path;
  }

  private String getGithubApiBaseUrlFromHost(String host) {
    return (host.equals("github.com")) ? String.format("https://api.%s", host)
                                       : String.format("https://%s/api/v3", host);
  }
}
