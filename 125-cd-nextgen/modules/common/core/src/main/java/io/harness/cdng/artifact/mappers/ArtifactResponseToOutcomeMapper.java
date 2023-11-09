/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactConfigHelper;
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
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.BambooArtifactOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GithubPackagesArtifactOutcome;
import io.harness.cdng.artifact.outcome.GoogleCloudSourceArtifactOutcome;
import io.harness.cdng.artifact.outcome.GoogleCloudStorageArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.S3ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateResponse;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.pms.yaml.ParameterField;

import software.wings.utils.RepositoryFormat;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
@OwnedBy(CDC)
public class ArtifactResponseToOutcomeMapper {
  private final String IMAGE_PULL_SECRET_START = "<+imagePullSecret.";
  private final String DOCKER_CONFIG_JSON_START = "<+dockerConfigJsonSecret.";

  private final String IMAGE_PULL_SECRET_END = ">";

  static final String ARTIFACT_ID = "artifactId";
  static final String ARTIFACT_FILE_NAME = "fileName";
  static final String PACKAGE_NAME = "package";

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
      case NEXUS2_REGISTRY:
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig = (Nexus2RegistryArtifactConfig) artifactConfig;
        NexusArtifactDelegateResponse nexus2DelegateResponse = (NexusArtifactDelegateResponse) artifactDelegateResponse;
        return getNexus2ArtifactOutcome(nexus2RegistryArtifactConfig, nexus2DelegateResponse, useDelegateResponse);
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
        if (customArtifactConfig.getScripts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource()
                != null) {
          CustomScriptInlineSource customScriptInlineSource =
              (CustomScriptInlineSource) customArtifactConfig.getScripts()
                  .getFetchAllArtifacts()
                  .getShellScriptBaseStepInfo()
                  .getSource()
                  .getSpec();
          if (customScriptInlineSource != null && customScriptInlineSource.getScript() != null
              && isNotEmpty(customScriptInlineSource.getScript().getValue())) {
            CustomArtifactDelegateResponse customArtifactDelegateResponse =
                (CustomArtifactDelegateResponse) artifactDelegateResponse;
            return getCustomArtifactOutcome(customArtifactConfig, customArtifactDelegateResponse, useDelegateResponse);
          }
        }
        return getCustomArtifactOutcome(customArtifactConfig);
      case AMAZONS3:
        AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactConfig;
        S3ArtifactDelegateResponse s3ArtifactDelegateResponse = (S3ArtifactDelegateResponse) artifactDelegateResponse;
        return getS3ArtifactOutcome(amazonS3ArtifactConfig, s3ArtifactDelegateResponse, useDelegateResponse);
      case JENKINS:
        JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactConfig;
        JenkinsArtifactDelegateResponse jenkinsArtifactDelegateResponse =
            (JenkinsArtifactDelegateResponse) artifactDelegateResponse;
        return getJenkinsArtifactOutcome(jenkinsArtifactConfig, jenkinsArtifactDelegateResponse, useDelegateResponse);
      case BAMBOO:
        BambooArtifactConfig bambooArtifactConfig = (BambooArtifactConfig) artifactConfig;
        BambooArtifactDelegateResponse bambooArtifactDelegateResponse =
            (BambooArtifactDelegateResponse) artifactDelegateResponse;
        return getBambooArtifactOutcome(bambooArtifactConfig, bambooArtifactDelegateResponse, useDelegateResponse);
      case GITHUB_PACKAGES:
        GithubPackagesArtifactConfig githubPackagesArtifactConfig = (GithubPackagesArtifactConfig) artifactConfig;
        GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse =
            (GithubPackagesArtifactDelegateResponse) artifactDelegateResponse;
        return getGithubPackagesArtifactOutcome(
            githubPackagesArtifactConfig, githubPackagesArtifactDelegateResponse, useDelegateResponse);
      case GOOGLE_ARTIFACT_REGISTRY:
        GoogleArtifactRegistryConfig googleArtifactRegistryConfig = (GoogleArtifactRegistryConfig) artifactConfig;
        GarDelegateResponse garDelegateResponse = (GarDelegateResponse) artifactDelegateResponse;
        return getGarArtifactOutcome(googleArtifactRegistryConfig, garDelegateResponse, useDelegateResponse);
      case AZURE_ARTIFACTS:
        AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactConfig;
        AzureArtifactsDelegateResponse azureArtifactsDelegateResponse =
            (AzureArtifactsDelegateResponse) artifactDelegateResponse;
        return getAzureArtifactsOutcome(azureArtifactsConfig, azureArtifactsDelegateResponse, useDelegateResponse);
      case AMI:
        AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactConfig;
        AMIArtifactDelegateResponse amiArtifactDelegateResponse =
            (AMIArtifactDelegateResponse) artifactDelegateResponse;
        return getAMIArtifactOutcome(amiArtifactConfig, amiArtifactDelegateResponse, useDelegateResponse);
      case GOOGLE_CLOUD_STORAGE_ARTIFACT:
        GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
            (GoogleCloudStorageArtifactConfig) artifactConfig;
        GoogleCloudStorageArtifactDelegateResponse googleCloudStorageArtifactDelegateResponse =
            (GoogleCloudStorageArtifactDelegateResponse) artifactDelegateResponse;
        return getGoogleCloudStorageArtifactOutcome(
            googleCloudStorageArtifactConfig, googleCloudStorageArtifactDelegateResponse, useDelegateResponse);

      case GOOGLE_CLOUD_SOURCE_ARTIFACT:
        GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
            (GoogleCloudSourceArtifactConfig) artifactConfig;
        GoogleCloudSourceArtifactDelegateResponse googleCloudSourceArtifactDelegateResponse =
            (GoogleCloudSourceArtifactDelegateResponse) artifactDelegateResponse;
        return getGoogleCloudSourceArtifactOutcome(
            googleCloudSourceArtifactConfig, googleCloudSourceArtifactDelegateResponse, true);
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactConfig.getSourceType()));
    }
  }

  private static AzureArtifactsOutcome getAzureArtifactsOutcome(AzureArtifactsConfig azureArtifactsConfig,
      AzureArtifactsDelegateResponse azureArtifactsDelegateResponse, boolean useDelegateResponse) {
    return AzureArtifactsOutcome.builder()
        .image(useDelegateResponse ? azureArtifactsDelegateResponse.getPackageUrl() : "")
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(azureArtifactsConfig)))
        .packageName(azureArtifactsConfig.getPackageName().getValue())
        .version(useDelegateResponse ? azureArtifactsDelegateResponse.getVersion() : "")
        .connectorRef(azureArtifactsConfig.getConnectorRef().getValue())
        .type(ArtifactSourceType.AZURE_ARTIFACTS.getDisplayName())
        .identifier(azureArtifactsConfig.getIdentifier())
        .primaryArtifact(azureArtifactsConfig.isPrimaryArtifact())
        .versionRegex(azureArtifactsConfig.getVersionRegex().getValue())
        .feed(azureArtifactsConfig.getFeed().getValue())
        .project(azureArtifactsConfig.getProject().getValue())
        .packageType(azureArtifactsConfig.getPackageType().getValue())
        .scope(azureArtifactsConfig.getScope().getValue())
        .metadata(useDelegateResponse ? getMetadata(azureArtifactsDelegateResponse) : null)
        .build();
  }

  private static AMIArtifactOutcome getAMIArtifactOutcome(AMIArtifactConfig amiArtifactConfig,
      AMIArtifactDelegateResponse amiArtifactDelegateResponse, boolean useDelegateResponse) {
    return AMIArtifactOutcome.builder()
        .amiId(useDelegateResponse ? amiArtifactDelegateResponse.getAmiId() : "")
        .metadata(useDelegateResponse ? amiArtifactDelegateResponse.getMetadata() : null)
        .version(useDelegateResponse ? amiArtifactDelegateResponse.getVersion() : "")
        .tag(useDelegateResponse ? amiArtifactDelegateResponse.getVersion() : "") // Use the version field for tag.
        .connectorRef(amiArtifactConfig.getConnectorRef().getValue())
        .type(ArtifactSourceType.AMI.getDisplayName())
        .identifier(amiArtifactConfig.getIdentifier())
        .primaryArtifact(amiArtifactConfig.isPrimaryArtifact())
        .versionRegex(amiArtifactConfig.getVersionRegex().getValue())
        .build();
  }

  private static GithubPackagesArtifactOutcome getGithubPackagesArtifactOutcome(
      GithubPackagesArtifactConfig githubPackagesArtifactConfig,
      GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse, boolean useDelegateResponse) {
    checkSHAEquality(
        githubPackagesArtifactDelegateResponse, githubPackagesArtifactConfig.getDigest(), useDelegateResponse);
    return GithubPackagesArtifactOutcome.builder()
        .image(useDelegateResponse ? githubPackagesArtifactDelegateResponse.getPackageUrl() : "")
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(githubPackagesArtifactConfig)))
        .dockerConfigJsonSecret(
            createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(githubPackagesArtifactConfig)))
        .packageName(githubPackagesArtifactConfig.getPackageName().getValue())
        .version(useDelegateResponse ? githubPackagesArtifactDelegateResponse.getVersion() : "")
        .connectorRef(githubPackagesArtifactConfig.getConnectorRef().getValue())
        .type(ArtifactSourceType.GITHUB_PACKAGES.getDisplayName())
        .identifier(githubPackagesArtifactConfig.getIdentifier())
        .primaryArtifact(githubPackagesArtifactConfig.isPrimaryArtifact())
        .versionRegex(githubPackagesArtifactConfig.getVersionRegex().getValue())
        .metadata(useDelegateResponse ? getMetadata(githubPackagesArtifactDelegateResponse) : null)
        .label(getLabels(githubPackagesArtifactDelegateResponse))
        .digest(githubPackagesArtifactConfig.getDigest() != null ? githubPackagesArtifactConfig.getDigest().getValue()
                                                                 : null)
        .packageType(githubPackagesArtifactConfig.getPackageType().getValue())
        .build();
  }

  public GoogleCloudSourceArtifactOutcome getGoogleCloudSourceArtifactOutcome(
      GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig,
      GoogleCloudSourceArtifactDelegateResponse googleCloudSourceArtifactDelegateResponse,
      boolean useDelegateResponse) {
    return GoogleCloudSourceArtifactOutcome.builder()
        .connectorRef(googleCloudSourceArtifactConfig.getConnectorRef().getValue())
        .identifier(googleCloudSourceArtifactConfig.getIdentifier())
        .type(ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT.getDisplayName())
        .project(googleCloudSourceArtifactConfig.getProject().getValue())
        .repository(googleCloudSourceArtifactConfig.getRepository().getValue())
        .sourceDirectory(googleCloudSourceArtifactConfig.getSourceDirectory().getValue())
        .primaryArtifact(googleCloudSourceArtifactConfig.isPrimaryArtifact())
        .branch(ParameterFieldHelper.getParameterFieldValue(googleCloudSourceArtifactConfig.getBranch()))
        .gitTag(ParameterFieldHelper.getParameterFieldValue(googleCloudSourceArtifactConfig.getTag()))
        .commitId(ParameterFieldHelper.getParameterFieldValue(googleCloudSourceArtifactConfig.getCommitId()))
        .fetchType(googleCloudSourceArtifactConfig.getFetchType().getName())
        .build();
  }

  private static S3ArtifactOutcome getS3ArtifactOutcome(AmazonS3ArtifactConfig amazonS3ArtifactConfig,
      S3ArtifactDelegateResponse s3ArtifactDelegateResponse, boolean useDelegateResponse) {
    return S3ArtifactOutcome.builder()
        .bucketName(amazonS3ArtifactConfig.getBucketName().getValue())
        .region(
            amazonS3ArtifactConfig.getRegion() != null ? amazonS3ArtifactConfig.getRegion().getValue() : "us-east-1")
        .filePath(useDelegateResponse ? s3ArtifactDelegateResponse.getFilePath() : "")
        .connectorRef(amazonS3ArtifactConfig.getConnectorRef().getValue())
        .type(ArtifactSourceType.AMAZONS3.getDisplayName())
        .identifier(amazonS3ArtifactConfig.getIdentifier())
        .primaryArtifact(amazonS3ArtifactConfig.isPrimaryArtifact())
        .filePathRegex(amazonS3ArtifactConfig.getFilePathRegex().getValue())
        .metadata(useDelegateResponse ? getMetadata(s3ArtifactDelegateResponse) : null)
        .build();
  }

  private DockerArtifactOutcome getDockerArtifactOutcome(DockerHubArtifactConfig dockerConfig,
      DockerArtifactDelegateResponse dockerDelegateResponse, boolean useDelegateResponse) {
    String displayName = null;
    checkSHAEquality(dockerDelegateResponse, dockerConfig.getDigest(), useDelegateResponse);
    if (useDelegateResponse && dockerDelegateResponse != null && dockerDelegateResponse.getBuildDetails() != null
        && dockerDelegateResponse.getBuildDetails().getUiDisplayName() != null) {
      displayName = dockerDelegateResponse.getBuildDetails().getUiDisplayName();
    }
    return DockerArtifactOutcome.builder()
        .image(getImageValue(dockerDelegateResponse))
        .connectorRef(dockerConfig.getConnectorRef().getValue())
        .imagePath(dockerConfig.getImagePath().getValue())
        .tag(useDelegateResponse ? (dockerDelegateResponse != null
                     ? dockerDelegateResponse.getTag()
                     : (dockerConfig.getTag() != null ? dockerConfig.getTag().getValue() : null))
                                 : (dockerConfig.getTag() != null ? dockerConfig.getTag().getValue() : null))
        .tagRegex(dockerConfig.getTagRegex() != null ? dockerConfig.getTagRegex().getValue() : null)
        .identifier(dockerConfig.getIdentifier())
        .type(ArtifactSourceType.DOCKER_REGISTRY.getDisplayName())
        .primaryArtifact(dockerConfig.isPrimaryArtifact())
        .displayName(displayName)
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(dockerConfig)))
        .dockerConfigJsonSecret(createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(dockerConfig)))
        .label(getLabels(dockerDelegateResponse))
        .digest(dockerConfig.getDigest() != null ? dockerConfig.getDigest().getValue() : null)
        .metadata(useDelegateResponse ? getMetadata(dockerDelegateResponse) : null)
        .build();
  }

  private GcrArtifactOutcome getGcrArtifactOutcome(GcrArtifactConfig gcrArtifactConfig,
      GcrArtifactDelegateResponse gcrArtifactDelegateResponse, boolean useDelegateResponse) {
    checkSHAEquality(gcrArtifactDelegateResponse, gcrArtifactConfig.getDigest(), useDelegateResponse);
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
        .metadata(useDelegateResponse ? getMetadata(gcrArtifactDelegateResponse) : null)
        .label(getLabels(gcrArtifactDelegateResponse))
        .digest(gcrArtifactConfig.getDigest() != null ? gcrArtifactConfig.getDigest().getValue() : null)
        .dockerConfigJsonSecret(createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(gcrArtifactConfig)))
        .build();
  }

  private static GarArtifactOutcome getGarArtifactOutcome(GoogleArtifactRegistryConfig googleArtifactRegistryConfig,
      GarDelegateResponse garDelegateResponse, boolean useDelegateResponse) {
    checkSHAEquality(garDelegateResponse, googleArtifactRegistryConfig.getDigest(), useDelegateResponse);
    return GarArtifactOutcome.builder()
        .version(useDelegateResponse ? garDelegateResponse.getVersion()
                                     : (googleArtifactRegistryConfig.getVersion() != null
                                             ? googleArtifactRegistryConfig.getVersion().getValue()
                                             : null))
        .registryHostname(garDelegateResponse != null
                ? garDelegateResponse.getBuildDetails().getMetadata().get("registryHostname")
                : "")
        .connectorRef(googleArtifactRegistryConfig.getConnectorRef().getValue())
        .pkg(googleArtifactRegistryConfig.getPkg().getValue())
        .project(googleArtifactRegistryConfig.getProject().getValue())
        .region(googleArtifactRegistryConfig.getRegion().getValue())
        .repositoryName(googleArtifactRegistryConfig.getRepositoryName().getValue())
        .versionRegex(googleArtifactRegistryConfig.getVersionRegex().getValue())
        .type(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY.getDisplayName())
        .identifier(googleArtifactRegistryConfig.getIdentifier())
        .primaryArtifact(googleArtifactRegistryConfig.isPrimaryArtifact())
        .image(getImageValue(garDelegateResponse))
        .metadata(useDelegateResponse ? getMetadata(garDelegateResponse) : null)
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(googleArtifactRegistryConfig)))
        .label(getLabels(garDelegateResponse))
        .digest(googleArtifactRegistryConfig.getDigest() != null ? googleArtifactRegistryConfig.getDigest().getValue()
                                                                 : null)
        .dockerConfigJsonSecret(
            createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(googleArtifactRegistryConfig)))
        .repositoryType(googleArtifactRegistryConfig.getGoogleArtifactRegistryType().getValue())
        .build();
  }

  private EcrArtifactOutcome getEcrArtifactOutcome(EcrArtifactConfig ecrArtifactConfig,
      EcrArtifactDelegateResponse ecrArtifactDelegateResponse, boolean useDelegateResponse) {
    checkSHAEquality(ecrArtifactDelegateResponse, ecrArtifactConfig.getDigest(), useDelegateResponse);
    return EcrArtifactOutcome.builder()
        .registryId(getRegistryId(ecrArtifactDelegateResponse))
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
        .dockerConfigJsonSecret(createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(ecrArtifactConfig)))
        .label(getEcrLabels(ecrArtifactDelegateResponse))
        .digest(ecrArtifactConfig.getDigest() != null ? ecrArtifactConfig.getDigest().getValue() : null)
        .metadata(getMetadata(ecrArtifactDelegateResponse))
        .build();
  }

  private static Map<String, String> getEcrLabels(EcrArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || EmptyPredicate.isEmpty(artifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }
    return artifactDelegateResponse.getLabel();
  }

  private NexusArtifactOutcome getNexusArtifactOutcome(NexusRegistryArtifactConfig artifactConfig,
      NexusArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    String artifactPath = null;
    String displayName = null;
    String tag = null;

    if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("docker")) {
      checkSHAEquality(artifactDelegateResponse, artifactConfig.getDigest(), useDelegateResponse);
      NexusRegistryDockerConfig nexusRegistryDockerConfig =
          (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();
      artifactPath = nexusRegistryDockerConfig.getArtifactPath() != null
          ? nexusRegistryDockerConfig.getArtifactPath().getValue()
          : null;
    } else if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("maven")) {
      if (artifactDelegateResponse != null) {
        artifactPath = artifactDelegateResponse.getArtifactPath();
      }
    } else if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("npm")
        || artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("nuget")) {
      if (artifactDelegateResponse != null && artifactDelegateResponse.getBuildDetails() != null
          && artifactDelegateResponse.getBuildDetails().getMetadata() != null) {
        artifactPath = artifactDelegateResponse.getBuildDetails().getMetadata().get("package");
      }
    }

    if (useDelegateResponse && artifactDelegateResponse != null && artifactDelegateResponse.getBuildDetails() != null
        && isNotEmpty(artifactDelegateResponse.getBuildDetails().getUiDisplayName())) {
      displayName = artifactDelegateResponse.getBuildDetails().getUiDisplayName();
    }

    if (useDelegateResponse && artifactDelegateResponse != null && isNotEmpty(artifactDelegateResponse.getTag())) {
      tag = artifactDelegateResponse.getTag();
    } else {
      tag = artifactConfig.getTag() != null ? artifactConfig.getTag().fetchFinalValue().toString() : null;
    }

    return NexusArtifactOutcome.builder()
        .repositoryName(artifactConfig.getRepository().getValue())
        .image(getImageValue(artifactDelegateResponse))
        .connectorRef(artifactConfig.getConnectorRef().getValue())
        .artifactPath(artifactPath)
        .repositoryFormat(artifactConfig.getRepositoryFormat().getValue())
        .tag(tag)
        .tagRegex(artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : null)
        .identifier(artifactConfig.getIdentifier())
        .type(ArtifactSourceType.NEXUS3_REGISTRY.getDisplayName())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(artifactConfig)))
        .registryHostname(getRegistryHostnameValue(artifactDelegateResponse))
        .displayName(displayName)
        .digest(artifactConfig.getDigest() != null ? artifactConfig.getDigest().getValue() : null)
        .label(getLabels(artifactDelegateResponse))
        .metadata(useDelegateResponse ? getMetadata(artifactDelegateResponse) : null)
        .build();
  }

  private NexusArtifactOutcome getNexus2ArtifactOutcome(Nexus2RegistryArtifactConfig artifactConfig,
      NexusArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    String displayName = null;
    if (artifactDelegateResponse.getBuildDetails() != null
        && isNotEmpty(artifactDelegateResponse.getBuildDetails().getUiDisplayName())) {
      displayName = artifactDelegateResponse.getBuildDetails().getUiDisplayName();
    }

    Map<String, String> metadata = useDelegateResponse ? getMetadata(artifactDelegateResponse) : null;

    String artifactPath = null;
    if (isNotEmpty(metadata)) {
      String artifactId = metadata.get(ARTIFACT_ID);
      String packageName = metadata.get(PACKAGE_NAME);
      String artifactFileName = metadata.get(ARTIFACT_FILE_NAME);
      if (isNotEmpty(artifactId)) {
        artifactPath = artifactId;
      } else if (isNotEmpty(packageName)) {
        artifactPath = packageName;
      } else if (isNotEmpty(artifactFileName)) {
        artifactPath = artifactFileName;
      }
    }

    return NexusArtifactOutcome.builder()
        .repositoryName(artifactConfig.getRepository().getValue())
        .image(getImageValue(artifactDelegateResponse))
        .connectorRef(artifactConfig.getConnectorRef().getValue())
        .repositoryFormat(artifactConfig.getRepositoryFormat().getValue())
        .tag(useDelegateResponse ? artifactDelegateResponse.getTag()
                                 : (artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : null))
        .tagRegex(artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : null)
        .identifier(artifactConfig.getIdentifier())
        .type(ArtifactSourceType.NEXUS2_REGISTRY.getDisplayName())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(artifactConfig)))
        .registryHostname(getRegistryHostnameValue(artifactDelegateResponse))
        .displayName(displayName)
        .metadata(metadata)
        .artifactPath(artifactPath)
        .build();
  }

  private ArtifactoryArtifactOutcome getArtifactoryArtifactOutcome(ArtifactoryRegistryArtifactConfig artifactConfig,
      ArtifactoryArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    checkSHAEquality(artifactDelegateResponse, artifactConfig.getDigest(), useDelegateResponse);
    return ArtifactoryArtifactOutcome.builder()
        .repositoryName(artifactConfig.getRepository().getValue())
        .image(getImageValue(artifactDelegateResponse))
        .imagePath(artifactConfig.getArtifactPath().getValue())
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
        .dockerConfigJsonSecret(createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(artifactConfig)))
        .registryHostname(getRegistryHostnameValue(artifactDelegateResponse))
        .metadata(useDelegateResponse ? getMetadata(artifactDelegateResponse) : null)
        .digest(artifactConfig.getDigest() != null ? artifactConfig.getDigest().getValue() : null)
        .label(getArtifactoryLabels(artifactDelegateResponse))
        .build();
  }

  private static Map<String, String> getArtifactoryLabels(
      ArtifactoryArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || EmptyPredicate.isEmpty(artifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }

    return artifactDelegateResponse.getLabel();
  }

  private ArtifactoryGenericArtifactOutcome getArtifactoryGenericArtifactOutcome(
      ArtifactoryRegistryArtifactConfig artifactConfig,
      ArtifactoryGenericArtifactDelegateResponse artifactDelegateResponse, boolean useDelegateResponse) {
    String artifactPath = useDelegateResponse
        ? artifactDelegateResponse.getArtifactPath()
        : (ParameterField.isNull(artifactConfig.getArtifactPath()) ? null
                : ParameterField.isBlank(artifactConfig.getArtifactPathFilter())
                ? getArtifactoryGenericArtifactPath(
                    artifactConfig.getArtifactDirectory().getValue(), artifactConfig.getArtifactPath().getValue())
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
        .metadata(useDelegateResponse ? artifactDelegateResponse.getBuildDetails().getMetadata() : null)
        .build();
  }

  private String getArtifactoryGenericArtifactPath(String artifactDirectory, String artifactPath) {
    if (StringUtils.isNotEmpty(artifactDirectory) && StringUtils.isNotEmpty(artifactPath)) {
      return Paths.get(artifactDirectory, artifactPath).toString();
    }
    return null;
  }

  private CustomArtifactOutcome getCustomArtifactOutcome(CustomArtifactConfig artifactConfig) {
    return CustomArtifactOutcome.builder()
        .identifier(artifactConfig.getIdentifier())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .version(artifactConfig.getVersion().getValue())
        .build();
  }

  private CustomArtifactOutcome getCustomArtifactOutcome(CustomArtifactConfig artifactConfig,
      CustomArtifactDelegateResponse customArtifactDelegateResponse, boolean useDelegateResponse) {
    String version = artifactConfig.getVersion().getValue();
    if (useDelegateResponse) {
      version = customArtifactDelegateResponse.getVersion() != null ? customArtifactDelegateResponse.getVersion()
                                                                    : artifactConfig.getVersion().getValue();
    }
    return CustomArtifactOutcome.builder()
        .identifier(artifactConfig.getIdentifier())
        .primaryArtifact(artifactConfig.isPrimaryArtifact())
        .version(version)
        .image(version)
        .tag(version)
        .displayName(useDelegateResponse ? customArtifactDelegateResponse.getBuildDetails().getUiDisplayName() : null)
        .metadata(useDelegateResponse ? customArtifactDelegateResponse.getMetadata() : null)
        .build();
  }

  private AcrArtifactOutcome getAcrArtifactOutcome(AcrArtifactConfig acrArtifactConfig,
      AcrArtifactDelegateResponse acrArtifactDelegateResponse, boolean useDelegateResponse) {
    checkSHAEquality(acrArtifactDelegateResponse, acrArtifactConfig.getDigest(), useDelegateResponse);
    return AcrArtifactOutcome.builder()
        .subscription(acrArtifactConfig.getSubscriptionId().getValue())
        .registry(getRegistryHostnameValue(acrArtifactDelegateResponse))
        .repository(acrArtifactConfig.getRepository().getValue())
        .image(getImageValue(acrArtifactDelegateResponse))
        .connectorRef(acrArtifactConfig.getConnectorRef().getValue())
        .tag(getAcrTag(useDelegateResponse, acrArtifactDelegateResponse, acrArtifactConfig.getTag()))
        .tagRegex(acrArtifactConfig.getTagRegex() != null ? acrArtifactConfig.getTagRegex().getValue() : null)
        .identifier(acrArtifactConfig.getIdentifier())
        .type(ArtifactSourceType.ACR.getDisplayName())
        .primaryArtifact(acrArtifactConfig.isPrimaryArtifact())
        .imagePullSecret(createImagePullSecret(ArtifactUtils.getArtifactKey(acrArtifactConfig)))
        .metadata(useDelegateResponse ? getMetadata(acrArtifactDelegateResponse) : null)
        .label(getLabels(acrArtifactDelegateResponse))
        .digest(acrArtifactConfig.getDigest() != null ? acrArtifactConfig.getDigest().getValue() : null)
        .dockerConfigJsonSecret(createDockerConfigJsonSecret(ArtifactUtils.getArtifactKey(acrArtifactConfig)))
        .build();
  }

  private static JenkinsArtifactOutcome getJenkinsArtifactOutcome(JenkinsArtifactConfig jenkinsArtifactConfig,
      JenkinsArtifactDelegateResponse jenkinsArtifactDelegateResponse, boolean useDelegateResponse) {
    return JenkinsArtifactOutcome.builder()
        .jobName(jenkinsArtifactConfig.getJobName().getValue())
        .build(getJenkinsBuild(useDelegateResponse, jenkinsArtifactDelegateResponse, jenkinsArtifactConfig))
        .artifactPath(jenkinsArtifactConfig.getArtifactPath().getValue())
        .connectorRef(jenkinsArtifactConfig.getConnectorRef().getValue())
        .type(ArtifactSourceType.JENKINS.getDisplayName())
        .identifier(jenkinsArtifactConfig.getIdentifier())
        .primaryArtifact(jenkinsArtifactConfig.isPrimaryArtifact())
        .metadata(useDelegateResponse ? jenkinsArtifactDelegateResponse.getBuildDetails().getMetadata() : Map.of())
        .build();
  }

  private static BambooArtifactOutcome getBambooArtifactOutcome(BambooArtifactConfig bambooArtifactConfig,
      BambooArtifactDelegateResponse bambooArtifactDelegateResponse, boolean useDelegateResponse) {
    return BambooArtifactOutcome.builder()
        .planKey(bambooArtifactConfig.getPlanKey().getValue())
        .build(getBambooBuild(useDelegateResponse, bambooArtifactDelegateResponse, bambooArtifactConfig))
        .artifactPath(bambooArtifactConfig.getArtifactPaths().getValue())
        .connectorRef(bambooArtifactConfig.getConnectorRef().getValue())
        .type(ArtifactSourceType.BAMBOO.getDisplayName())
        .identifier(bambooArtifactConfig.getIdentifier())
        .primaryArtifact(bambooArtifactConfig.isPrimaryArtifact())
        .metadata(useDelegateResponse ? bambooArtifactDelegateResponse.getBuildDetails().getMetadata() : Map.of())
        .build();
  }

  public GoogleCloudStorageArtifactOutcome getGoogleCloudStorageArtifactOutcome(
      GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig,
      GoogleCloudStorageArtifactDelegateResponse googleCloudStorageArtifactDelegateResponse,
      boolean useDelegateResponse) {
    return GoogleCloudStorageArtifactOutcome.builder()
        .connectorRef(googleCloudStorageArtifactConfig.getConnectorRef().getValue())
        .identifier(googleCloudStorageArtifactConfig.getIdentifier())
        .type(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT.getDisplayName())
        .project(googleCloudStorageArtifactConfig.getProject().getValue())
        .bucket(googleCloudStorageArtifactConfig.getBucket().getValue())
        .artifactPath(useDelegateResponse ? googleCloudStorageArtifactDelegateResponse.getArtifactPath() : "")
        .primaryArtifact(googleCloudStorageArtifactConfig.isPrimaryArtifact())
        .metadata(getMetadata(googleCloudStorageArtifactDelegateResponse))
        .build();
  }

  private String getAcrTag(boolean useDelegateResponse, AcrArtifactDelegateResponse acrArtifactDelegateResponse,
      ParameterField<String> configTag) {
    return useDelegateResponse              ? acrArtifactDelegateResponse.getTag()
        : !ParameterField.isNull(configTag) ? configTag.getValue()
                                            : null;
  }

  private String getJenkinsBuild(boolean useDelegateResponse,
      JenkinsArtifactDelegateResponse jenkinsArtifactDelegateResponse, JenkinsArtifactConfig jenkinsArtifactConfig) {
    return useDelegateResponse
        ? jenkinsArtifactDelegateResponse.getBuild()
        : (jenkinsArtifactConfig.getBuild() != null ? jenkinsArtifactConfig.getBuild().getValue() : null);
  }

  private String getBambooBuild(boolean useDelegateResponse,
      BambooArtifactDelegateResponse bambooArtifactDelegateResponse, BambooArtifactConfig bambooArtifactConfig) {
    return useDelegateResponse
        ? bambooArtifactDelegateResponse.getBuild()
        : (bambooArtifactConfig.getBuild() != null ? bambooArtifactConfig.getBuild().getValue() : null);
  }

  private String getRegistryId(EcrArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || StringUtils.isBlank(artifactDelegateResponse.getRegistryId())) {
      return null;
    }
    return artifactDelegateResponse.getRegistryId();
  }

  private String getImageValue(ArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || artifactDelegateResponse.getBuildDetails() == null) {
      return null;
    }
    return EmptyPredicate.isNotEmpty(artifactDelegateResponse.getBuildDetails().getMetadata())
        ? artifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
        : null;
  }

  private Map<String, String> getLabels(DockerArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || EmptyPredicate.isEmpty(artifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }
    return artifactDelegateResponse.getLabel();
  }

  private Map<String, String> getLabels(GithubPackagesArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || EmptyPredicate.isEmpty(artifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }
    return artifactDelegateResponse.getLabel();
  }

  private Map<String, String> getLabels(NexusArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || EmptyPredicate.isEmpty(artifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }
    return artifactDelegateResponse.getLabel();
  }

  private Map<String, String> getLabels(GarDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || artifactDelegateResponse.getLabel() == null) {
      return Collections.emptyMap();
    }
    return EmptyPredicate.isNotEmpty(artifactDelegateResponse.getLabel()) ? artifactDelegateResponse.getLabel()
                                                                          : Collections.emptyMap();
  }

  private Map<String, String> getLabels(GcrArtifactDelegateResponse gcrArtifactDelegateResponse) {
    if (gcrArtifactDelegateResponse == null || isEmpty(gcrArtifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }
    return gcrArtifactDelegateResponse.getLabel();
  }

  private Map<String, String> getLabels(AcrArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || EmptyPredicate.isEmpty(artifactDelegateResponse.getLabel())) {
      return Collections.emptyMap();
    }
    return artifactDelegateResponse.getLabel();
  }

  private String getRegistryHostnameValue(ArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || artifactDelegateResponse.getBuildDetails() == null) {
      return null;
    }
    return EmptyPredicate.isNotEmpty(artifactDelegateResponse.getBuildDetails().getMetadata())
        ? artifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.REGISTRY_HOSTNAME)
        : null;
  }

  private Map<String, String> getMetadata(ArtifactDelegateResponse artifactDelegateResponse) {
    if (artifactDelegateResponse == null || artifactDelegateResponse.getBuildDetails() == null) {
      return null;
    }
    return EmptyPredicate.isNotEmpty(artifactDelegateResponse.getBuildDetails().getMetadata())
        ? artifactDelegateResponse.getBuildDetails().getMetadata()
        : null;
  }

  private String createImagePullSecret(String artifactKey) {
    return String.format("%s%s%s", IMAGE_PULL_SECRET_START, artifactKey, IMAGE_PULL_SECRET_END);
  }

  private String createDockerConfigJsonSecret(String artifactKey) {
    return String.format("%s%s%s", DOCKER_CONFIG_JSON_START, artifactKey, IMAGE_PULL_SECRET_END);
  }

  private void checkSHAEquality(ArtifactDelegateResponse artifactDelegateResponse, ParameterField<String> digestField,
      boolean useDelegateResponse) {
    if (!ArtifactConfigHelper.checkNullOrInput(digestField)) {
      String digest = (String) digestField.fetchFinalValue();
      Map<String, String> metaData = getMetadata(artifactDelegateResponse);
      if (useDelegateResponse && EmptyPredicate.isNotEmpty(metaData)) {
        String sha = metaData.get(ArtifactMetadataKeys.SHA);
        String shaV2 = metaData.get(ArtifactMetadataKeys.SHAV2);
        if (!digest.equals(sha) && !digest.equals(shaV2)) {
          throw new ArtifactServerException(
              "Artifact image SHA256 validation failed: image sha256 digest mismatch.\n Requested digest: " + digest
              + "\nAvailable digests:\n" + sha + " (V1)\n" + shaV2 + " (V2)");
        }
      }
    }
  }
}
