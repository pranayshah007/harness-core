/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.utils.FilePathUtils.addStartingSlashIfMissing;

import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.common.dtos.RepoProviders;

import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class RepoProviderHelper {
  public static String getTheFilePathUrl(
      String repoUrl, String branch, RepoProviders repoProviders, String completeFilePath) {
    if (repoProviders == null) {
      throw new InvalidRequestException("Repo provider should not be null when finding the entity file path");
    }
    String completeFilePathWithSlash = addStartingSlashIfMissing(completeFilePath);
    switch (repoProviders) {
      case GITHUB:
        return String.format("%s/blob/%s%s", repoUrl, branch, completeFilePathWithSlash);
      case BITBUCKET:
        return String.format("%s/src/%s%s", repoUrl, branch, completeFilePathWithSlash);
      default:
        throw new UnsupportedOperationException(
            "The operation to get the file path url is not supported for " + repoProviders);
    }
  }

  public RepoProviders getRepoProviderFromConnectorType(ConnectorType connectorType) {
    switch (connectorType) {
      case GITHUB:
        return RepoProviders.GITHUB;
      case BITBUCKET:
        return RepoProviders.BITBUCKET;
      case GITLAB:
        return RepoProviders.GITLAB;
      case AZURE_REPO:
        return RepoProviders.AZURE;
      default:
        throw new InvalidRequestException("Unknown ConnectorDisconnectHandler type " + connectorType);
    }
  }

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
        throw new InvalidRequestException("Unknown ConnectorDisconnectHandler type " + connectorType);
    }
  }

  public static RepoProviders getRepoProviderFromTheUrl(String repoUrl) {
    if (repoUrl.contains("bitbucket.org")) {
      return RepoProviders.BITBUCKET;
    }
    return RepoProviders.GITHUB;
  }

  public static RepoProviders getRepoProviderType(List<YamlGitConfigDTO> yamlGitConfigs) {
    if (isEmpty(yamlGitConfigs)) {
      throw new UnexpectedException("The git sync configs cannot be null when figuring out the repo provider");
    }
    final YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigs.get(0);
    return RepoProviderHelper.getRepoProviderFromConnectorType(yamlGitConfigDTO.getGitConnectorType());
  }
}
