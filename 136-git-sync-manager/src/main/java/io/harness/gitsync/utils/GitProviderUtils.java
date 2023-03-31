/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.utils;

import static io.harness.delegate.beans.connector.ConnectorType.AZURE;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.gitsync.caching.beans.GitProvider.AZURE_SAAS;
import static io.harness.gitsync.caching.beans.GitProvider.BITBUCKET_ON_PREM;
import static io.harness.gitsync.caching.beans.GitProvider.BITBUCKET_SAAS;
import static io.harness.gitsync.caching.beans.GitProvider.GITHUB_SAAS;
import static io.harness.gitsync.caching.beans.GitProvider.UNKNOWN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.caching.beans.GitProvider;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class GitProviderUtils {
  public GitProvider getGitProvider(ScmConnector scmConnector) {
    if (isGithubSaas(scmConnector)) {
      return GITHUB_SAAS;
    }
    if (isBitbucketSaas(scmConnector)) {
      return BITBUCKET_SAAS;
    }
    if (isBitbucketOnPrem(scmConnector)) {
      return BITBUCKET_ON_PREM;
    }
    if (isAzureSaas(scmConnector)) {
      return AZURE_SAAS;
    }
    return UNKNOWN;
  }

  private boolean isGithubSaas(ScmConnector scmConnector) {
    return GITHUB.equals(scmConnector.getConnectorType());
  }

  private boolean isAzureSaas(ScmConnector scmConnector) {
    return AZURE.equals(scmConnector.getConnectorType());
  }

  private boolean isBitbucket(ScmConnector scmConnector) {
    return BITBUCKET.equals(scmConnector.getConnectorType());
  }

  private boolean isBitbucketOnPrem(ScmConnector scmConnector) {
    return isBitbucket(scmConnector) && !GitClientHelper.isBitBucketSAAS(scmConnector.getUrl());
  }

  private boolean isBitbucketSaas(ScmConnector scmConnector) {
    return isBitbucket(scmConnector) && GitClientHelper.isBitBucketSAAS(scmConnector.getUrl());
  }

  public String buildRepoForGitlab(String namespace, String repoName) {
    if (!namespace.contains("/")) {
      return repoName;
    }
    return namespace.substring(namespace.indexOf('/') + 1) + "/" + repoName;
  }
}
