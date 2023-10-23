/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepImageUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ContainerExecutionConfig containerExecutionConfig;
  public ConnectorDetails getHarnessImageConnectorDetailsForK8(NGAccess ngAccess, ContainerK8sInfra infrastructure) {
    ConnectorDetails harnessInternalImageConnector = null;
    Optional<ParameterField<String>> optionalHarnessImageConnector =
        Optional.ofNullable(infrastructure.getSpec().getHarnessImageConnectorRef());
    if (optionalHarnessImageConnector.isPresent() && isNotEmpty(optionalHarnessImageConnector.get().getValue())) {
      harnessInternalImageConnector =
          connectorUtils.getConnectorDetails(ngAccess, optionalHarnessImageConnector.get().getValue());
    } else if (isNotEmpty(containerExecutionConfig.getDefaultInternalImageConnector())) {
      harnessInternalImageConnector = connectorUtils.getDefaultInternalConnector(ngAccess);
    }
    return harnessInternalImageConnector;
  }

  public String getImageWithRegistryPath(String imageName, String registryUrl, String connectorId) {
    URL url = null;
    try {
      url = new URL(registryUrl);
    } catch (MalformedURLException e) {
      throw new CIStageExecutionException(
          format("Malformed registryUrl %s in docker ConnectorDisconnectHandler id: %s", registryUrl, connectorId));
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

  public String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
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
}
