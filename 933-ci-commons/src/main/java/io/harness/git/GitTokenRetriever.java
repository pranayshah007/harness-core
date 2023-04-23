/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git;

import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.GITHUB_APP;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.TOKEN;
import static io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType.OAUTH;

import static java.lang.String.format;

import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GitTokenRetriever {
  private static final String GITHUB_API_URL = "https://api.github.com/";

  @Inject private GithubService githubService;
  @Inject private SecretDecryptor secretDecryptor;

  public String retrieveAuthToken(GitSCMType gitSCMType, ConnectorDetails gitConnector) {
    switch (gitSCMType) {
      case GITHUB:
        return retrieveGithubAuthToken(gitConnector);
      case GITLAB:
        return retrieveGitlabAuthToken(gitConnector);
      case BITBUCKET:
        return retrieveBitbucketAuthToken(gitConnector);
      case AZURE_REPO:
        return retrieveAzureRepoAuthToken(gitConnector);
      default:
        throw new CIStageExecutionException(format("Unsupported scm type %s for git status", gitSCMType));
    }
  }

  private String retrieveGithubAuthToken(ConnectorDetails gitConnector) {
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
    if (gitConfigDTO.getApiAccess() == null || gitConfigDTO.getApiAccess().getType() == null) {
      throw new InvalidRequestException(format("Failed to retrieve token info for github connector: %s", gitConfigDTO));
    }

    GithubApiAccessType apiAccessType = gitConfigDTO.getApiAccess().getType();
    GithubApiAccessSpecDTO githubApiAccessSpecDTO = gitConfigDTO.getApiAccess().getSpec();
    DecryptableEntity decryptableEntity =
        secretDecryptor.decrypt(githubApiAccessSpecDTO, gitConnector.getEncryptedDataDetails());

    String token = null;
    if (apiAccessType == TOKEN) {
      token = new String(((GithubTokenSpecDTO) decryptableEntity).getTokenRef().getDecryptedValue());
    } else if (gitConfigDTO.getApiAccess().getType() == GITHUB_APP) {
      GithubAppSpecDTO githubAppSpecDTO = (GithubAppSpecDTO) decryptableEntity;
      GithubAppConfig githubAppConfig =
          GithubAppConfig.builder()
              .installationId(FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
                  githubAppSpecDTO.getInstallationId(), githubAppSpecDTO.getInstallationIdRef()))
              .appId(FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
                  githubAppSpecDTO.getApplicationId(), githubAppSpecDTO.getInstallationIdRef()))
              .privateKey(new String(githubAppSpecDTO.getPrivateKeyRef().getDecryptedValue()))
              .githubUrl(getGitApiURL(gitConfigDTO.getUrl()))
              .build();
      token = githubService.getToken(githubAppConfig);
    } else if (apiAccessType == GithubApiAccessType.OAUTH) {
      token = String.valueOf(((GithubOauthDTO) decryptableEntity).getTokenRef().getDecryptedValue());
    } else {
      throw new CIStageExecutionException(
          format("Unsupported access type %s for github accessType. Use Token Access", apiAccessType));
    }

    return token;
  }

  public String retrieveGitlabAuthToken(ConnectorDetails gitConnector) {
    GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
    if (gitConfigDTO.getApiAccess() == null) {
      throw new CIStageExecutionException(
          format("Failed to retrieve token info for gitlab connector: %s", gitConnector.getIdentifier()));
    }
    if (gitConfigDTO.getApiAccess().getType() == GitlabApiAccessType.TOKEN) {
      GitlabApiAccessSpecDTO gitlabApiAccessSpecDTO = gitConfigDTO.getApiAccess().getSpec();
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(gitlabApiAccessSpecDTO, gitConnector.getEncryptedDataDetails());
      return new String(((GitlabTokenSpecDTO) decryptableEntity).getTokenRef().getDecryptedValue());
    } else if (gitConfigDTO.getApiAccess().getType() == OAUTH) {
      GitlabOauthDTO gitlabOauthDTO = (GitlabOauthDTO) gitConfigDTO.getApiAccess().getSpec();
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(gitlabOauthDTO, gitConnector.getEncryptedDataDetails());
      gitConfigDTO.getApiAccess().setSpec((GitlabOauthDTO) decryptableEntity);
      return new String(((GitlabOauthDTO) gitConfigDTO.getApiAccess().getSpec()).getTokenRef().getDecryptedValue());
    } else {
      throw new CIStageExecutionException(
          format("Unsupported access type %s for gitlab status", gitConfigDTO.getApiAccess().getType()));
    }
  }

  public String retrieveBitbucketUsernameFromAPIAccess(
      BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO,
      List<EncryptedDataDetail> encryptedDataDetails) {
    DecryptableEntity decryptableEntity =
        secretDecryptor.decrypt(bitbucketUsernameTokenApiAccessDTO, encryptedDataDetails);
    return new String(((BitbucketUsernameTokenApiAccessDTO) decryptableEntity).getUsernameRef().getDecryptedValue());
  }

  private String retrieveBitbucketAuthToken(ConnectorDetails gitConnector) {
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
    if (bitbucketConnectorDTO.getApiAccess() == null) {
      throw new CIStageExecutionException(
          format("Failed to retrieve token info for Bitbucket connector: %s", gitConnector.getIdentifier()));
    }
    if (bitbucketConnectorDTO.getApiAccess().getType() == BitbucketApiAccessType.USERNAME_AND_TOKEN) {
      BitbucketUsernameTokenApiAccessDTO bitbucketTokenSpecDTO =
          (BitbucketUsernameTokenApiAccessDTO) bitbucketConnectorDTO.getApiAccess().getSpec();
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(bitbucketTokenSpecDTO, gitConnector.getEncryptedDataDetails());
      return new String(((BitbucketUsernameTokenApiAccessDTO) decryptableEntity).getTokenRef().getDecryptedValue());
    } else {
      throw new CIStageExecutionException(
          format("Unsupported access type %s for gitlab status", bitbucketConnectorDTO.getApiAccess().getType()));
    }
  }

  private String retrieveAzureRepoAuthToken(ConnectorDetails gitConnector) {
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
    if (azureRepoConnectorDTO.getApiAccess() == null) {
      throw new CIStageExecutionException(
          format("Failed to retrieve token info for Azure repo connector: %s", gitConnector.getIdentifier()));
    }
    if (azureRepoConnectorDTO.getApiAccess().getType() == AzureRepoApiAccessType.TOKEN) {
      AzureRepoTokenSpecDTO azureRepoTokenSpecDTO =
          (AzureRepoTokenSpecDTO) azureRepoConnectorDTO.getApiAccess().getSpec();
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(azureRepoTokenSpecDTO, gitConnector.getEncryptedDataDetails());
      azureRepoConnectorDTO.getApiAccess().setSpec((AzureRepoApiAccessSpecDTO) decryptableEntity);

      return new String(((AzureRepoTokenSpecDTO) decryptableEntity).getTokenRef().getDecryptedValue());
    } else {
      throw new CIStageExecutionException(
          format("Unsupported access type %s for Azure repo status", azureRepoConnectorDTO.getApiAccess().getType()));
    }
  }

  private String getGitApiURL(String url) {
    if (GitClientHelper.isGithubSAAS(url)) {
      return GITHUB_API_URL;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return "https://" + domain + "/api/v3/";
    }
  }
}
