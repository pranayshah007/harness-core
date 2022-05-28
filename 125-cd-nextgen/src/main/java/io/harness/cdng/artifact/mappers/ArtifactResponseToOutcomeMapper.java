/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.pms.yaml.ParameterField;

import software.wings.utils.RepositoryFormat;

import java.nio.file.Paths;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDC)
public class ArtifactResponseToOutcomeMapper {
  private final String IMAGE_PULL_SECRET_START = "<+imagePullSecret.";
  private final String IMAGE_PULL_SECRET_END = ">";

  public ArtifactOutcome toArtifactOutcome(
      ArtifactConfig artifactConfig, ArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    switch (artifactConfig.getSourceType()) {
      case DOCKER_REGISTRY:
        DockerHubArtifactConfig dockerConfig = (DockerHubArtifactConfig) artifactConfig;
        DockerArtifactDelegateResponse dockerDelegateResponse =
            (DockerArtifactDelegateResponse) artifactDelegateResponse;
        return getDockerArtifactOutcome(dockerConfig, dockerDelegateResponse, useDelegateResponse);
      case GCR:
        GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactConfig;
        GcrArtifactDelegateResponse gcrArtifactDelegateResponse =
            (GcrArtifactDelegateResponse) artifactDelegateResponse;
        return getGcrArtifactOutcome(gcrArtifactConfig, gcrArtifactDelegateResponse, useDelegateResponse);
      case ECR:
        EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactConfig;
        EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
            (EcrArtifactDelegateResponse) artifactDelegateResponse;
        return getEcrArtifactOutcome(ecrArtifactConfig, ecrArtifactDelegateResponse, useDelegateResponse);
      case NEXUS3_REGISTRY:
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactConfig;
        NexusArtifactDelegateResponse nexusDelegateResponse = (NexusArtifactDelegateResponse) artifactDelegateResponse;
        return getNexusArtifactOutcome(nexusRegistryArtifactConfig, nexusDelegateResponse, useDelegateResponse);
      case ARTIFACTORY_REGISTRY:
        ArtifactOutcome artifactOutcome = null;
        ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
            (ArtifactoryRegistryArtifactConfig) artifactConfig;
        RepositoryFormat repositoryType =
            RepositoryFormat.valueOf(artifactoryRegistryArtifactConfig.getRepositoryFormat().getValue());
        switch (repositoryType) {
          case docker:
            ArtifactoryArtifactDelegateResponse artifactoryDelegateResponse =
                (ArtifactoryArtifactDelegateResponse) artifactDelegateResponse;
            artifactOutcome = getArtifactoryArtifactOutcome(
                artifactoryRegistryArtifactConfig, artifactoryDelegateResponse, useDelegateResponse);
            return artifactOutcome;
          case generic:
            ArtifactoryGenericArtifactDelegateResponse artifactoryGenericDelegateResponse =
                (ArtifactoryGenericArtifactDelegateResponse) artifactDelegateResponse;
            artifactOutcome = getArtifactoryGenericArtifactOutcome(
                artifactoryRegistryArtifactConfig, artifactoryGenericDelegateResponse, useDelegateResponse);
            return artifactOutcome;
          default:
            throw new UnsupportedOperationException(
                String.format("Repository Format [%s] for Artifactory Not Supported", repositoryType));
        }
      case ACR:
        AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactConfig;
        AcrArtifactDelegateResponse acrArtifactDelegateResponse =
            (AcrArtifactDelegateResponse) artifactDelegateResponse;
        return getAcrArtifactOutcome(acrArtifactConfig, acrArtifactDelegateResponse, useDelegateResponse);
      case CUSTOM_ARTIFACT:
        CustomArtifactConfig customArtifactConfig = (CustomArtifactConfig) artifactConfig;
        return getCustomArtifactOutcome(customArtifactConfig);
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactConfig.getSourceType()));
    }
  }

  private DockerArtifactOutcome getDockerArtifactOutcome(DockerHubArtifactConfig dockerConfig,
      DockerArtifactDelegateResponse dockerDelegateResponse, boolean useDelegateResponse) {
    return DockerArtifactOutcome.builder()
        .image(getImageValue(dockerDelegateResponse))
        .connectorRef(dockerConfig.getConnectorRef().getValue())
        .imagePath(dockerConfig.getImagePath().getValue())
        .tag(useDelegateResponse ? dockerDelegateResponse.getTag()
                                 : (dockerConfig.getTag() != null ? dockerConfig.getTag().getValue() : null))
        .tagRegex(dockerConfig.getTagRegex() != null ? dockerConfig.getTagRegex().getValue() : null)
        .identifier(dockerConfig.getIdentifier())
        .type(ArtifactSourceType.DOCKER_REGISTRY.getDisplayName())
        .primaryArtifact(dockerConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(dockerConfig)))
        .build();
  }

  private GcrArtifactOutcome getGcrArtifactOutcome(GcrArtifactConfig gcrArtifactConfig,
      GcrArtifactDelegateResponse gcrArtifactDelegateResponse, boolean useDelegateResponse) {
    return GcrArtifactOutcome.builder()
        .image(getImageValue(gcrArtifactDelegateResponse))
        .connectorRef(gcrArtifactConfig.getConnectorRef().getValue())
        .imagePath(gcrArtifactConfig.getImagePath().getValue())
        .registryHostname(gcrArtifactConfig.getRegistryHostname().getValue())
        .tag(useDelegateResponse ? gcrArtifactDelegateResponse.getTag()
                                 : (gcrArtifactConfig.getTag() != null ? gcrArtifactConfig.getTag().getValue() : null))
        .tagRegex(gcrArtifactConfig.getTagRegex() != null ? gcrArtifactConfig.getTagRegex().getValue() : null)
        .identifier(gcrArtifactConfig.getIdentifier())
        .type(ArtifactSourceType.GCR.getDisplayName())
        .primaryArtifact(gcrArtifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(gcrArtifactConfig)))
        .build();
  }

  private EcrArtifactOutcome getEcrArtifactOutcome(EcrArtifactConfig ecrArtifactConfig,
      EcrArtifactDelegateResponse ecrArtifactDelegateResponse, boolean useDelegateResponse) {
    return EcrArtifactOutcome.builder()
        .image(getImageValue(ecrArtifactDelegateResponse))
        .connectorRef(ecrArtifactConfig.getConnectorRef().getValue())
        .imagePath(ecrArtifactConfig.getImagePath().getValue())
        .region(ecrArtifactConfig.getRegion().getValue())
        .tag(useDelegateResponse ? ecrArtifactDelegateResponse.getTag()
                                 : (ecrArtifactConfig.getTag() != null ? ecrArtifactConfig.getTag().getValue() : null))
        .tagRegex(ecrArtifactConfig.getTagRegex() != null ? ecrArtifactConfig.getTagRegex().getValue() : null)
        .identifier(ecrArtifactConfig.getIdentifier())
        .type(ArtifactSourceType.ECR.getDisplayName())
        .primaryArtifact(ecrArtifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(ecrArtifactConfig)))
        .build();
  }

  private NexusArtifactOutcome getNexusArtifactOutcome(NexusRegistryArtifactConfig artifactConfig,
      NexusArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    return NexusArtifactOutcome.builder()
        .repositoryName(artifactConfig.getRepository().getValue())
        .image(getImageValue(artifactDelegateResponse))
        .connectorRef(artifactConfig.getConnectorRef().getValue())
        .artifactPath(artifactConfig.getArtifactPath().getValue())
        .repositoryFormat(artifactConfig.getRepositoryFormat().getValue())
        .tag(useDelegateResponse ? artifactDelegateResponse.getTag()
                                 : (artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : null))
        .tagRegex(artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : null)
        .identifier(artifactConfig.getIdentifier())
        .type(ArtifactSourceType.NEXUS3_REGISTRY.getDisplayName())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(artifactConfig)))
        .registryHostname(getRegistryHostnameValue(artifactDelegateResponse))
        .build();
  }

  private ArtifactoryArtifactOutcome getArtifactoryArtifactOutcome(ArtifactoryRegistryArtifactConfig artifactConfig,
      ArtifactoryArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    return ArtifactoryArtifactOutcome.builder()
        .repositoryName(artifactConfig.getRepository().getValue())
        .image(getImageValue(artifactDelegateResponse))
        .connectorRef(artifactConfig.getConnectorRef().getValue())
        .artifactPath(artifactConfig.getArtifactPath().getValue())
        .repositoryFormat(artifactConfig.getRepositoryFormat().getValue())
        .tag(useDelegateResponse ? artifactDelegateResponse.getTag()
                                 : (artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : null))
        .tagRegex(artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : null)
        .identifier(artifactConfig.getIdentifier())
        .type(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(artifactConfig)))
        .registryHostname(getRegistryHostnameValue(artifactDelegateResponse))
        .build();
  }

  private ArtifactoryGenericArtifactOutcome getArtifactoryGenericArtifactOutcome(
      ArtifactoryRegistryArtifactConfig artifactConfig,
      ArtifactoryGenericArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    String artifactPath = useDelegateResponse ? ParameterField.isBlank(artifactConfig.getArtifactPathFilter())
            ? Paths.get(artifactConfig.getArtifactDirectory().getValue(), artifactDelegateResponse.getArtifactPath())
                  .toString()
            : artifactDelegateResponse.getArtifactPath()
                                              : (ParameterField.isNull(artifactConfig.getArtifactPath()) ? null
                                                      : ParameterField.isBlank(artifactConfig.getArtifactPathFilter())
                                                      ? Paths
                                                            .get(artifactConfig.getArtifactDirectory().getValue(),
                                                                artifactConfig.getArtifactPath().getValue())
                                                            .toString()
                                                      : artifactConfig.getArtifactPath().getValue());

    return ArtifactoryGenericArtifactOutcome.builder()
        .repositoryName(artifactConfig.getRepository().getValue())
        .connectorRef(artifactConfig.getConnectorRef().getValue())
        .artifactDirectory(artifactConfig.getArtifactDirectory().getValue())
        .repositoryFormat(artifactConfig.getRepositoryFormat().getValue())
        .artifactPath(artifactPath)
        // As tag is common field in all artifact outcomes, this need to be populated
        .tag(artifactPath)
        .artifactPathFilter(ParameterField.isNull(artifactConfig.getArtifactPathFilter())
                ? null
                : artifactConfig.getArtifactPathFilter().getValue())
        .identifier(artifactConfig.getIdentifier())
        .type(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .build();
  }

  private CustomArtifactOutcome getCustomArtifactOutcome(CustomArtifactConfig artifactConfig) {
    return CustomArtifactOutcome.builder()
        .identifier(artifactConfig.getIdentifier())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .version(artifactConfig.getVersion().getValue())
        .build();
  }

  private AcrArtifactOutcome getAcrArtifactOutcome(AcrArtifactConfig acrArtifactConfig,
      AcrArtifactDelegateResponse acrArtifactDelegateResponse, boolean useDelegateResponse) {
    return AcrArtifactOutcome.builder()
        .subscription(acrArtifactConfig.getSubscriptionId().getValue())
        .registry(getRegistryHostnameValue(acrArtifactDelegateResponse))
        .repository(acrArtifactConfig.getRepository().getValue())
        .image(getImageValue(acrArtifactDelegateResponse))
        .connectorRef(acrArtifactConfig.getConnectorRef().getValue())
        .tag(getAcrTag(useDelegateResponse, acrArtifactDelegateResponse.getTag(), acrArtifactConfig.getTag()))
        .tagRegex(acrArtifactConfig.getTagRegex() != null ? acrArtifactConfig.getTagRegex().getValue() : null)
        .identifier(acrArtifactConfig.getIdentifier())
        .type(ArtifactSourceType.ACR.getDisplayName())
        .primaryArtifact(acrArtifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(acrArtifactConfig)))
        .build();
  }

  private String getAcrTag(boolean useDelegateResponse, String delegateResponseTag, ParameterField<String> configTag) {
    return useDelegateResponse ? delegateResponseTag : !ParameterField.isNull(configTag) ? configTag.getValue() : null;
  }

  private String getImageValue(ArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || artifactDelegateResponse.getBuildDetails() == null) {
      return null;
    }
    return EmptyPredicate.isNotEmpty(artifactDelegateResponse.getBuildDetails().getMetadata())
        ? artifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
        : null;
  }

  private String getRegistryHostnameValue(ArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || artifactDelegateResponse.getBuildDetails() == null) {
      return null;
    }
    return EmptyPredicate.isNotEmpty(artifactDelegateResponse.getBuildDetails().getMetadata())
        ? artifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.REGISTRY_HOSTNAME)
        : null;
  }

  private String createImagePullSecret(String artifactKey) {
    return String.format("%s%s%s", IMAGE_PULL_SECRET_START, artifactKey, IMAGE_PULL_SECRET_END);
  }
}
