/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.git.GitClientHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(DX)
public class ScmGitProviderHelper {
  @Inject(optional = true) GithubService githubService;

  @Inject GitClientHelper gitClientHelper;
  private static final String azure_repo_name_separator = "/";

  public String getSlug(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return getSlugFromUrl(((GithubConnectorDTO) scmConnector).getUrl());
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return getSlugFromUrlForGitlab((GitlabConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return getSlugFromUrlForBitbucket(((BitbucketConnectorDTO) scmConnector).getUrl());
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      return getSlugFromUrlForAzureRepo(((AzureRepoConnectorDTO) scmConnector).getUrl());
    } else if (scmConnector instanceof HarnessConnectorDTO) {
      return getSlugFromHarnessUrl(((HarnessConnectorDTO) scmConnector).getUrl());
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }

  public String getRepoName(ScmConnector scmConnector) {
    return GitClientHelper.getGitRepo(scmConnector.getUrl());
  }

  private String getSlugFromUrl(String url) {
    String repoName = gitClientHelper.getGitRepo(url);
    String ownerName = gitClientHelper.getGitOwner(url, false);
    return ownerName + "/" + repoName;
  }

  // An unclean method which might change/mature with time but we need to maintain bg compatibilty.
  private String getSlugFromHarnessUrl(String url) {
    return gitClientHelper.getHarnessRepoName(url);
  }

  private String getSlugFromUrlForGitlab(GitlabConnectorDTO gitlabConnectorDTO) {
    String url = gitlabConnectorDTO.getUrl();
    String apiUrl = ScmGitProviderMapper.getGitlabApiUrl(gitlabConnectorDTO);
    if (!StringUtils.isBlank(apiUrl) && gitlabConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      apiUrl = StringUtils.stripEnd(apiUrl, "/") + "/";
      String slug = StringUtils.removeStart(url, apiUrl);
      return StringUtils.removeEnd(slug, ".git");
    }
    String repoName = gitClientHelper.getGitRepo(url);
    String ownerName = gitClientHelper.getGitOwner(url, false);
    return ownerName + "/" + repoName;
  }

  private String getSlugFromUrlForBitbucket(String url) {
    String repoName = gitClientHelper.getGitRepo(url);
    String ownerName = gitClientHelper.getGitOwner(url, false);
    if (!GitClientHelper.isBitBucketSAAS(url)) {
      if (ownerName.equals("scm")) {
        return repoName;
      }
      if (repoName.startsWith("scm/")) {
        return StringUtils.removeStart(repoName, "scm/");
      }
    }
    return ownerName + "/" + repoName;
  }

  private String getSlugFromUrlForAzureRepo(String url) {
    // url if of type https://dev.azure.com/satyamgoel/scmapitest/_git/scmapitest-renamed
    // slug the whole string after last '/'
    String repoName = gitClientHelper.getGitRepo(url);
    return StringUtils.substringAfterLast(repoName, azure_repo_name_separator);
  }

  public String getAccessTokenFromGithubApp(String applicationId, SecretRefData applicationIdRef, String installationId,
      SecretRefData installationIdRef, SecretRefData privateKeyRef, String url) {
    if (githubService == null) {
      throw new NotImplementedException("Token for Github App is only supported on delegate");
    }
    try {
      return githubService.getToken(
          GithubAppConfig.builder()
              .appId(getSecretAsStringFromPlainTextOrSecretRef(applicationId, applicationIdRef))
              .installationId(getSecretAsStringFromPlainTextOrSecretRef(installationId, installationIdRef))
              .privateKey(String.valueOf(privateKeyRef.getDecryptedValue()))
              .githubUrl(GitClientHelper.getGithubApiURL(url))
              .build());
    } catch (Exception ex) {
      throw new InvalidArgumentsException(ex.getMessage());
    }
  }

  public String getToken(SecretRefData tokenRef) {
    if (tokenRef == null || tokenRef.getDecryptedValue() == null) {
      throw new InvalidArgumentsException(
          "The Personal Access Token is not set. Please set the Personal Access Token in the Git Connector which has permissions to use providers API's");
    }
    return String.valueOf(tokenRef.getDecryptedValue());
  }
}
