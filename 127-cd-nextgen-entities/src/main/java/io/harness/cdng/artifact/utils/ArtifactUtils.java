/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.utils.RepositoryFormat.generic;

import io.harness.NGConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.yaml.ParameterField;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ArtifactUtils {
  public final String PRIMARY_ARTIFACT = "primary";
  public final String SIDECAR_ARTIFACT = "sidecars";
  public final String GENERIC_PLACEHOLDER = "type: %s, artifactDirectory: %s, artifactPath/artifactPathFilter: %s,"
      + " connectorRef: %s%n";

  public String getArtifactKey(ArtifactConfig artifactConfig) {
    return artifactConfig.isPrimaryArtifact() ? artifactConfig.getIdentifier()
                                              : SIDECAR_ARTIFACT + "." + artifactConfig.getIdentifier();
  }

  public List<ArtifactConfig> convertArtifactListIntoArtifacts(
      ArtifactListConfig artifactListConfig, NGLogCallback ngManagerLogCallback) {
    List<ArtifactConfig> artifacts = new LinkedList<>();
    if (artifactListConfig == null) {
      return artifacts;
    }
    if (artifactListConfig.getPrimary() != null) {
      artifacts.add(artifactListConfig.getPrimary().getSpec());
      saveLogs(ngManagerLogCallback,
          "Primary artifact details: \n"
              + getLogInfo(artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType()));
    }
    if (EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars())) {
      artifacts.addAll(
          artifactListConfig.getSidecars().stream().map(s -> s.getSidecar().getSpec()).collect(Collectors.toList()));
      saveLogs(ngManagerLogCallback,
          "Sidecars details: \n"
              + artifactListConfig.getSidecars()
                    .stream()
                    .map(s -> getLogInfo(s.getSidecar().getSpec(), s.getSidecar().getSourceType()))
                    .collect(Collectors.joining()));
    }
    return artifacts;
  }

  public void appendIfNecessary(StringBuilder keyBuilder, String value) {
    if (keyBuilder == null) {
      throw new InvalidRequestException("Key string builder cannot be null");
    }
    if (isNotEmpty(value)) {
      keyBuilder.append(NGConstants.STRING_CONNECTOR).append(value);
    }
  }

  // TODO(archit): Check whether string should be case sensitive or not.
  public String generateUniqueHashFromStringList(List<String> valuesList) {
    valuesList.sort(Comparator.nullsLast(String::compareTo));
    StringBuilder keyBuilder = new StringBuilder();
    valuesList.forEach(s -> appendIfNecessary(keyBuilder, s));
    return Hashing.sha256().hashString(keyBuilder.toString(), StandardCharsets.UTF_8).toString();
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  public String getLogInfo(ArtifactConfig artifactConfig, ArtifactSourceType sourceType) {
    if (sourceType == null) {
      return "";
    }

    String placeholder = " type: %s, image: %s, tag/tagRegex: %s, connectorRef: %s\n";
    switch (sourceType) {
      case DOCKER_REGISTRY:
        DockerHubArtifactConfig dockerHubArtifactConfig = (DockerHubArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, dockerHubArtifactConfig.getImagePath().getValue(),
            dockerHubArtifactConfig.getTag().getValue() != null ? dockerHubArtifactConfig.getTag().getValue()
                                                                : dockerHubArtifactConfig.getTagRegex().getValue(),
            dockerHubArtifactConfig.getConnectorRef().getValue());
      case AMAZONS3:
        AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, amazonS3ArtifactConfig.getBucketName().getValue(),
            amazonS3ArtifactConfig.getFilePath().getValue(), amazonS3ArtifactConfig.getFilePathRegex().getValue(),
            amazonS3ArtifactConfig.getConnectorRef().getValue());
      case GCR:
        GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, gcrArtifactConfig.getImagePath().getValue(),
            gcrArtifactConfig.getTag().getValue() != null ? gcrArtifactConfig.getTag().getValue()
                                                          : gcrArtifactConfig.getTagRegex().getValue(),
            gcrArtifactConfig.getConnectorRef().getValue());
      case ECR:
        EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, ecrArtifactConfig.getImagePath().getValue(),
            ecrArtifactConfig.getTag().getValue() != null ? ecrArtifactConfig.getTag().getValue()
                                                          : ecrArtifactConfig.getTagRegex().getValue(),
            ecrArtifactConfig.getConnectorRef().getValue());
      case NEXUS3_REGISTRY:
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, nexusRegistryArtifactConfig.getArtifactPath().getValue(),
            ParameterField.isNull(nexusRegistryArtifactConfig.getTag())
                ? nexusRegistryArtifactConfig.getTagRegex().getValue()
                : nexusRegistryArtifactConfig.getTag().getValue(),
            nexusRegistryArtifactConfig.getConnectorRef().getValue());
      case ARTIFACTORY_REGISTRY:
        ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
            (ArtifactoryRegistryArtifactConfig) artifactConfig;
        if (generic.name().equals(artifactoryRegistryArtifactConfig.getRepositoryFormat().getValue())) {
          return String.format(GENERIC_PLACEHOLDER, sourceType,
              artifactoryRegistryArtifactConfig.getArtifactDirectory().getValue(),
              ParameterField.isNull(artifactoryRegistryArtifactConfig.getArtifactPath())
                  ? artifactoryRegistryArtifactConfig.getArtifactPathFilter().getValue()
                  : artifactoryRegistryArtifactConfig.getArtifactPath().getValue(),
              artifactoryRegistryArtifactConfig.getConnectorRef().getValue());
        }
        return String.format(placeholder, sourceType, artifactoryRegistryArtifactConfig.getArtifactPath().getValue(),
            ParameterField.isNull(artifactoryRegistryArtifactConfig.getTag())
                ? artifactoryRegistryArtifactConfig.getTagRegex().getValue()
                : artifactoryRegistryArtifactConfig.getTag().getValue(),
            artifactoryRegistryArtifactConfig.getConnectorRef().getValue());
      case ACR:
        AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, acrArtifactConfig.getRepository().getValue(),
            acrArtifactConfig.getTag().getValue() != null ? acrArtifactConfig.getTag().getValue()
                                                          : acrArtifactConfig.getTagRegex().getValue(),
            acrArtifactConfig.getConnectorRef().getValue());
      case CUSTOM_ARTIFACT:
        CustomArtifactConfig customArtifactConfig = (CustomArtifactConfig) artifactConfig;
        return String.format("type: %s, build: %s", sourceType, customArtifactConfig.getVersion().getValue());
      case JENKINS:
        JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, jenkinsArtifactConfig.getJobName().getValue(),
            jenkinsArtifactConfig.getArtifactPath().getValue(), jenkinsArtifactConfig.getBuild().getValue(),
            jenkinsArtifactConfig.getConnectorRef().getValue());
      case GOOGLE_ARTIFACT_REGISTRY:
        GoogleArtifactRegistryConfig googleArtifactRegistryConfig = (GoogleArtifactRegistryConfig) artifactConfig;
        return String.format(placeholder, sourceType, googleArtifactRegistryConfig.getRegion().getValue(),
            googleArtifactRegistryConfig.getPkg().getValue(), googleArtifactRegistryConfig.getConnectorRef().getValue(),
            googleArtifactRegistryConfig.getProject().getValue(),
            googleArtifactRegistryConfig.getRepositoryName().getValue(),
            googleArtifactRegistryConfig.getGoogleArtifactRegistryType().getValue());
      default:
        throw new UnsupportedOperationException(String.format("Unknown Artifact Config type: [%s]", sourceType));
    }
  }
}
