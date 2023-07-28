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
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.git.GitClientHelper;

import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.lang3.StringUtils;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
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
    String gitUrl;
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

  public static String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
    if (connectorDetails == null) {
      return imageName;
    }

    ConnectorType connectorType = connectorDetails.getConnectorType();
    if (connectorType != DOCKER) {
      return imageName;
    }

    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    String dockerRegistryUrl = dockerConnectorDTO.getDockerRegistryUrl();
    return getImageWithRegistryPath(imageName, dockerRegistryUrl, connectorDetails.getIdentifier());
  }

  private static String getImageWithRegistryPath(String imageName, String registryUrl, String connectorId) {
    URL url;
    try {
      url = new URL(registryUrl);
    } catch (MalformedURLException e) {
      throw new InvalidArgumentsException(
          format("Malformed registryUrl %s in docker connector id: %s", registryUrl, connectorId));
    }

    String registryHostName = url.getHost();
    if (url.getPort() != -1) {
      registryHostName = url.getHost() + ":" + url.getPort();
    }

    if (imageName.contains(registryHostName) || registryHostName.equals("index.docker.io")
        || registryHostName.equals("registry.hub.docker.com")) {
      return imageName;
    }

    String prefixRegistryPath = registryHostName + url.getPath();
    return trimTrailingCharacter(prefixRegistryPath, '/') + '/' + trimLeadingCharacter(imageName, '/');
  }
}
