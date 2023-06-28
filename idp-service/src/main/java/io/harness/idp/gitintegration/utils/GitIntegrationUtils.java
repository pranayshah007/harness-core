/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.gitintegration.utils;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.AZURE_HOST;

import io.harness.beans.DecryptedSecretValue;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GitIntegrationUtils {
  private static final String PATH_SEPARATOR_FOR_URL = "/";

  public BackstageEnvSecretVariable getBackstageEnvSecretVariable(String tokenSecretIdentifier, String tokenType) {
    BackstageEnvSecretVariable environmentSecret = new BackstageEnvSecretVariable();
    environmentSecret.harnessSecretIdentifier(tokenSecretIdentifier);
    environmentSecret.setEnvName(tokenType);
    environmentSecret.setType(BackstageEnvVariable.TypeEnum.SECRET);
    return environmentSecret;
  }

  public String decryptSecret(SecretManagerClientService ngSecretService, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String tokenSecretIdentifier, String connectorIdentifier) {
    DecryptedSecretValue decryptedSecretValue = ngSecretService.getDecryptedSecretValue(
        accountIdentifier, orgIdentifier, projectIdentifier, tokenSecretIdentifier);
    if (decryptedSecretValue == null) {
      throw new InvalidRequestException(String.format(
          "Secret not found for identifier : [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }
    return decryptedSecretValue.getDecryptedValue();
  }

  public String getHostForConnector(ConnectorInfoDTO connectorInfoDTO) {
    switch (connectorInfoDTO.getConnectorType()) {
      case GITHUB:
        GithubConnectorDTO configGithub = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
        return getHostFromURL(configGithub.getUrl());
      case GITLAB:
        GitlabConnectorDTO configGitlab = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
        return getHostFromURL(configGitlab.getUrl());
      case BITBUCKET:
        BitbucketConnectorDTO configBitbucket = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
        return getHostFromURL(configBitbucket.getUrl());
      case AZURE_REPO:
        return AZURE_HOST;
      default:
        return null;
    }
  }

  private String getHostFromURL(String url) {
    String[] splitURL = url.split(PATH_SEPARATOR_FOR_URL);
    return splitURL[2];
  }

  public boolean checkIfGithubAppConnector(ConnectorInfoDTO connectorInfoDTO) {
    GithubConnectorDTO config = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubApiAccessDTO apiAccess = config.getApiAccess();
    return (apiAccess != null
               && apiAccess.getType().toString().equals(GitIntegrationConstants.GITHUB_APP_CONNECTOR_TYPE))
        ? true
        : false;
  }

  public boolean checkIfApiAccessEnabledForBitbucketConnector(ConnectorInfoDTO connectorInfoDTO) {
    BitbucketConnectorDTO config = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BitbucketApiAccessDTO apiAccess = config.getApiAccess();
    return (apiAccess != null) ? true : false;
  }

  public String replaceAccountScopeFromConnectorId(String connectorIdentifier) {
    return connectorIdentifier.replace(GitIntegrationConstants.ACCOUNT_SCOPED, "");
  }
}
