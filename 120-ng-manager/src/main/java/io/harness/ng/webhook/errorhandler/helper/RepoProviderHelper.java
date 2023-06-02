/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler.helper;

import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.common.dtos.RepoProviders;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class RepoProviderHelper {
  public RepoProviders getRepoProviderType(ConnectorType connectorType, String url) {
    switch (connectorType) {
      case GITHUB:
        return RepoProviders.GITHUB;
      case BITBUCKET:
        if (GitClientHelper.isBitBucketSAAS(url)) {
          return RepoProviders.BITBUCKET;
        }
        return RepoProviders.BITBUCKET_SERVER;
      case GITLAB:
        return RepoProviders.GITLAB;
      case AZURE_REPO:
        return RepoProviders.AZURE;
      default:
        throw new InvalidRequestException("Unknown connector type " + connectorType);
    }
  }
}
