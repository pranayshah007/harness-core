/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static software.wings.utils.RepositoryFormat.generic;

import io.harness.NGConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudSourceArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudStorageArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.common.NGExpressionUtils;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.BaseNGAccess;
import io.harness.pms.yaml.ParameterField;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

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
        return String.format("\ntype: %s \nbucketName: %s \nfilePath: %s \nfilePathRegex: %s \nconnectorRef: %s\n",
            sourceType, amazonS3ArtifactConfig.getBucketName().getValue(),
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
        return String.format(placeholder, sourceType, nexusRegistryArtifactConfig.getRepository().getValue(),
            ParameterField.isNull(nexusRegistryArtifactConfig.getTag())
                ? nexusRegistryArtifactConfig.getTagRegex().getValue()
                : nexusRegistryArtifactConfig.getTag().getValue(),
            nexusRegistryArtifactConfig.getConnectorRef().getValue());
      case NEXUS2_REGISTRY:
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig = (Nexus2RegistryArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, nexus2RegistryArtifactConfig.getRepository().getValue(),
            ParameterField.isNull(nexus2RegistryArtifactConfig.getTag())
                ? nexus2RegistryArtifactConfig.getTagRegex().getValue()
                : nexus2RegistryArtifactConfig.getTag().getValue(),
            nexus2RegistryArtifactConfig.getConnectorRef().getValue());
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
        return String.format("\ntype: %s \nJobName: %s \nArtifactPath: %s \nBuild: %s \nConnectorRef: %s\n", sourceType,
            jenkinsArtifactConfig.getJobName().getValue(), jenkinsArtifactConfig.getArtifactPath().getValue(),
            jenkinsArtifactConfig.getBuild().getValue(), jenkinsArtifactConfig.getConnectorRef().getValue());
      case GITHUB_PACKAGES:
        GithubPackagesArtifactConfig githubPackagesArtifactConfig = (GithubPackagesArtifactConfig) artifactConfig;
        return String.format(
            "\ntype: %s \nconnectorRef: %s \norg: %s \npackageName: %s \npackageType: %s \nversion: %s \nversionRegex: %s\n",
            sourceType, githubPackagesArtifactConfig.getConnectorRef().getValue(),
            githubPackagesArtifactConfig.getOrg().getValue(), githubPackagesArtifactConfig.getPackageName().getValue(),
            githubPackagesArtifactConfig.getPackageType().getValue(),
            githubPackagesArtifactConfig.getVersion().getValue(),
            githubPackagesArtifactConfig.getVersionRegex().getValue());
      case AZURE_ARTIFACTS:
        AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactConfig;

        return String.format(
            "\ntype: %s \nscope: %s \nproject: %s \nfeed: %s \npackageType: %s \npackageName: %s \nversion: %s \nversionRegex: %s \nconnectorRef: %s\n",
            sourceType, azureArtifactsConfig.getScope().getValue(), azureArtifactsConfig.getProject().getValue(),
            azureArtifactsConfig.getFeed().getValue(), azureArtifactsConfig.getPackageType().getValue(),
            azureArtifactsConfig.getPackageName().getValue(), azureArtifactsConfig.getVersion().getValue(),
            azureArtifactsConfig.getVersionRegex().getValue(), azureArtifactsConfig.getConnectorRef().getValue());
      case AMI:
        AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactConfig;

        return String.format("\ntype: %s \nversion: %s \nversionRegex: %s \nconnectorRef: %s\n", sourceType,
            amiArtifactConfig.getVersion().getValue(), amiArtifactConfig.getVersionRegex().getValue(),
            amiArtifactConfig.getConnectorRef().getValue());
      case GOOGLE_ARTIFACT_REGISTRY:
        GoogleArtifactRegistryConfig googleArtifactRegistryConfig = (GoogleArtifactRegistryConfig) artifactConfig;
        String version = googleArtifactRegistryConfig.getVersion().getValue() != null
            ? googleArtifactRegistryConfig.getVersion().getValue()
            : googleArtifactRegistryConfig.getVersionRegex().getValue();
        return String.format(
            "\ntype: %s \nregion: %s \nproject: %s \nrepositoryName: %s \npackage: %s \nversion/versionRegex: %s \nconnectorRef: %s \nregistryType: %s\n",
            sourceType, googleArtifactRegistryConfig.getRegion().getValue(),
            googleArtifactRegistryConfig.getProject().getValue(),
            googleArtifactRegistryConfig.getRepositoryName().getValue(),
            googleArtifactRegistryConfig.getPkg().getValue(), version,
            googleArtifactRegistryConfig.getConnectorRef().getValue(),
            googleArtifactRegistryConfig.getGoogleArtifactRegistryType().getValue());
      case BAMBOO:
        BambooArtifactConfig bambooArtifactConfig = (BambooArtifactConfig) artifactConfig;
        return String.format("\ntype: %s \nJobName: %s \nArtifactPath: %s \nBuild: %s \nConnectorRef: %s\n", sourceType,
            bambooArtifactConfig.getPlanKey().getValue(), bambooArtifactConfig.getArtifactPaths().getValue(),
            bambooArtifactConfig.getBuild().getValue(), bambooArtifactConfig.getConnectorRef().getValue());
      case GOOGLE_CLOUD_STORAGE_ARTIFACT:
        GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
            (GoogleCloudStorageArtifactConfig) artifactConfig;
        return String.format("\ntype: %s \nconnectorRef: %s \nproject: %s \nbucket: %s \nartifactPath: %s",
            artifactConfig.getSourceType(), googleCloudStorageArtifactConfig.getConnectorRef().getValue(),
            googleCloudStorageArtifactConfig.getProject().getValue(),
            googleCloudStorageArtifactConfig.getBucket().getValue(),
            googleCloudStorageArtifactConfig.getArtifactPath().getValue());
      case GOOGLE_CLOUD_SOURCE_ARTIFACT:
        GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
            (GoogleCloudSourceArtifactConfig) artifactConfig;
        String branchFormat = "";
        String branchValue = "";
        if (ParameterFieldHelper.getParameterFieldValue(googleCloudSourceArtifactConfig.getBranch()) != null) {
          branchFormat = "\nbranch: %s";
          branchValue = googleCloudSourceArtifactConfig.getBranch().getValue();
        } else if (ParameterFieldHelper.getParameterFieldValue(googleCloudSourceArtifactConfig.getCommitId()) != null) {
          branchFormat = "\ncommitId: %s";
          branchValue = googleCloudSourceArtifactConfig.getCommitId().getValue();
        } else if (ParameterFieldHelper.getParameterFieldValue(googleCloudSourceArtifactConfig.getTag()) != null) {
          branchFormat = "\ntag: %s";
          branchValue = googleCloudSourceArtifactConfig.getTag().getValue();
        }
        return String.format("\ntype: %s \nconnectorRef: %s \nproject: %s \nrepository: %s " + branchFormat + " "
                + "\nsourceDirectory: %s",
            artifactConfig.getSourceType(), googleCloudSourceArtifactConfig.getConnectorRef().getValue(),
            googleCloudSourceArtifactConfig.getProject().getValue(),
            googleCloudSourceArtifactConfig.getRepository().getValue(), branchValue,
            googleCloudSourceArtifactConfig.getSourceDirectory().getValue());

      default:
        throw new UnsupportedOperationException(String.format("Unknown Artifact Config type: [%s]", sourceType));
    }
  }

  public static Map<String, String> getTaskSetupAbstractions(BaseNGAccess ngAccess) {
    Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Map<String, String> abstractions = new HashMap<>(owner);
    abstractions.put(SetupAbstractionKeys.ng, "true");
    if (ngAccess.getOrgIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier());
    }
    if (ngAccess.getProjectIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier());
    }
    return abstractions;
  }

  public void validateIfAnyValueAssigned(Pair<String, String>... value) {
    int size = value.length;
    for (int i = 0; i < size; i++) {
      if (!checkIfNullOrRuntime(value[i].getValue())) {
        return;
      }
    }
    throw new InvalidRequestException(constructErrorMessage(value));
  }

  public void validateIfAllValuesAssigned(Pair<String, String>... value) {
    int size = value.length;
    for (int i = 0; i < size; i++) {
      if (checkIfNullOrRuntime(value[i].getValue())) {
        throw new InvalidRequestException(constructErrorMessage(value[i]));
      }
    }
  }

  private boolean checkIfNullOrRuntime(String value) {
    if (EmptyPredicate.isEmpty(value) || NGExpressionUtils.matchesInputSetPattern(value)) {
      return true;
    }
    return false;
  }

  private String constructErrorMessage(Pair<String, String>... value) {
    int size = value.length;
    StringBuilder stringBuilder = new StringBuilder(String.format("value for %s", value[0].getKey()));
    for (int i = 1; i < size; i++) {
      stringBuilder.append(String.format(", %s", value[i].getKey()));
    }
    stringBuilder.append(" is empty or not provided");
    return stringBuilder.toString();
  }
}
