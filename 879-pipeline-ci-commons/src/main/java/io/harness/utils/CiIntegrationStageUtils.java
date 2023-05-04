/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.ci.commonconstants.CIExecutionConstants.AZURE_REPO_BASE_URL;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_URL_SUFFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.git.GitClientHelper;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class CiIntegrationStageUtils {
  public static String getGitURL(String repoName, GitConnectionType connectionType, String url) {
    String gitUrl = retrieveGenericGitConnectorURL(repoName, connectionType, url);

    if (!gitUrl.endsWith(GIT_URL_SUFFIX) && !gitUrl.contains(AZURE_REPO_BASE_URL)) {
      gitUrl += GIT_URL_SUFFIX;
    }
    return gitUrl;
  }

  public static String retrieveGenericGitConnectorURL(String repoName, GitConnectionType connectionType, String url) {
    String gitUrl = "";
    if (connectionType == GitConnectionType.REPO) {
      gitUrl = url;
    } else if (connectionType == GitConnectionType.PROJECT || connectionType == GitConnectionType.ACCOUNT) {
      if (isEmpty(repoName)) {
        throw new IllegalArgumentException("Repo name is not set in CI codebase spec");
      }
      if (connectionType == GitConnectionType.PROJECT) {
        gitUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(url, repoName);
      } else {
        gitUrl = StringUtils.join(StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR,
            StringUtils.stripStart(repoName, PATH_SEPARATOR));
      }
    } else {
      throw new InvalidArgumentsException(
          format("Invalid connection type for git connector: %s", connectionType.toString()), WingsException.USER);
    }

    return gitUrl;
  }
}
