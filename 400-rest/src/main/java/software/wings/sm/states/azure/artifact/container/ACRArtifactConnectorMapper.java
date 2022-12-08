/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.artifact.container;

import static io.harness.azure.model.AzureConstants.HTTPS_OR_HTTP_PREFIX_REGEX;

import static java.lang.String.format;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureContainerRegistryConnectorDTO;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;

import java.util.Optional;

public final class ACRArtifactConnectorMapper extends ArtifactConnectorMapper {
  public ACRArtifactConnectorMapper(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    super(artifact, artifactStreamAttributes);
  }

  @Override
  public ConnectorConfigDTO getConnectorDTO() {
    String registryName = artifactStreamAttributes.getRegistryName();
    String registryHostName = artifactStreamAttributes.getRegistryHostName();
    String azureResourceGroup = artifactStreamAttributes.getAzureResourceGroup();
    String subscriptionId = artifactStreamAttributes.getSubscriptionId();

    registryHostName = fixAzureRegistryLoginServer(registryHostName);

    return AzureContainerRegistryConnectorDTO.builder()
        .azureRegistryLoginServer(registryHostName)
        .azureRegistryName(registryName)
        .resourceGroupName(azureResourceGroup)
        .subscriptionId(subscriptionId)
        .build();
  }

  @Override
  public AzureRegistryType getAzureRegistryType() {
    return AzureRegistryType.ACR;
  }

  @Override
  public boolean isDockerArtifactType() {
    return true;
  }

  @Override
  public Optional<DecryptableEntity> getConnectorDTOAuthCredentials(ConnectorConfigDTO connectorConfigDTO) {
    return Optional.empty();
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.empty();
  }

  private String fixAzureRegistryLoginServer(String azureRegistryLoginServer) {
    if (!HTTPS_OR_HTTP_PREFIX_REGEX.asPredicate().test(azureRegistryLoginServer)) {
      return format("%s://%s", "https", azureRegistryLoginServer);
    }
    return azureRegistryLoginServer;
  }
}
