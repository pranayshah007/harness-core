/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.artifact;

import static java.lang.String.format;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.exception.InvalidRequestException;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.states.azure.artifact.container.ACRArtifactConnectorMapper;
import software.wings.sm.states.azure.artifact.container.ArtifactoryArtifactConnectorMapper;
import software.wings.sm.states.azure.artifact.container.DockerArtifactConnectorMapper;
import software.wings.utils.ArtifactType;

import java.util.Optional;

public abstract class ArtifactConnectorMapper {
  protected ArtifactStreamAttributes artifactStreamAttributes;
  protected Artifact artifact;

  protected ArtifactConnectorMapper(Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    this.artifactStreamAttributes = artifactStreamAttributes;
    this.artifact = artifact;
  }

  public static ArtifactConnectorMapper getArtifactConnectorMapper(
      Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes) {
    ArtifactStreamType artifactStreamType =
        ArtifactStreamType.valueOf(artifactStreamAttributes.getArtifactStreamType());
    ArtifactType artifactType = artifactStreamAttributes.getArtifactType();

    if (isDockerArtifactType(artifactType)) {
      return handleDockerArtifactTypes(artifact, artifactStreamAttributes, artifactStreamType);
    } else if (isArtifactStreamTypeSupportedByAzure(artifactStreamType)) {
      return new ArtifactStreamAttributesMapper(artifact, artifactStreamAttributes);
    } else {
      throw new InvalidRequestException(format(
          "Unsupported artifact stream type for non docker artifacts,  artifactStreamType: %s", artifactStreamType));
    }
  }

  private static ArtifactConnectorMapper handleDockerArtifactTypes(
      Artifact artifact, ArtifactStreamAttributes artifactStreamAttributes, ArtifactStreamType artifactStreamType) {
    if (ArtifactStreamType.DOCKER == artifactStreamType) {
      return new DockerArtifactConnectorMapper(artifact, artifactStreamAttributes);
    } else if (ArtifactStreamType.ARTIFACTORY == artifactStreamType) {
      return new ArtifactoryArtifactConnectorMapper(artifact, artifactStreamAttributes);
    } else if (ArtifactStreamType.ACR == artifactStreamType) {
      return new ACRArtifactConnectorMapper(artifact, artifactStreamAttributes);
    } else {
      throw new InvalidRequestException(
          format("Unsupported artifact stream type for docker artifacts,  artifactStreamType: %s", artifactStreamType));
    }
  }

  private static boolean isDockerArtifactType(ArtifactType artifactType) {
    return ArtifactType.DOCKER == artifactType;
  }

  private static boolean isArtifactStreamTypeSupportedByAzure(ArtifactStreamType streamType) {
    return ArtifactStreamType.ARTIFACTORY == streamType || ArtifactStreamType.NEXUS == streamType
        || ArtifactStreamType.JENKINS == streamType || ArtifactStreamType.BAMBOO == streamType
        || ArtifactStreamType.AMAZON_S3 == streamType || ArtifactStreamType.AZURE_ARTIFACTS == streamType;
  }

  public abstract ConnectorConfigDTO getConnectorDTO();
  public abstract AzureRegistryType getAzureRegistryType();
  public abstract boolean isDockerArtifactType();
  public abstract Optional<DecryptableEntity> getConnectorDTOAuthCredentials(ConnectorConfigDTO connectorConfigDTO);
  public abstract Optional<EncryptableSetting> getEncryptableSetting();

  public String getFullImageName() {
    return artifact.getMetadata().get("image");
  }

  public String getImageTag() {
    return artifact.getMetadata().get("tag");
  }

  public ArtifactStreamAttributes artifactStreamAttributes() {
    return artifactStreamAttributes;
  }
}
