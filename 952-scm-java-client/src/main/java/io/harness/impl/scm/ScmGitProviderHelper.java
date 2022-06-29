/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.git.GitClientHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(DX)
public class ScmGitProviderHelper {
  @Inject GitClientHelper gitClientHelper;
  private static final String azure_repo_name_separator = "/";

  public String getSlug(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return getSlugFromUrl(((GithubConnectorDTO) scmConnector).getUrl());
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return getSlugFromUrl(((GitlabConnectorDTO) scmConnector).getUrl());
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return getSlugFromUrlForBitbucket(((BitbucketConnectorDTO) scmConnector).getUrl());
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      return getSlugFromUrlForAzureRepo(((AzureRepoConnectorDTO) scmConnector).getUrl());
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }

  private String getSlugFromUrl(String url) {
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
}
