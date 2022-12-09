/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.utils.RepositoryFormat.generic;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNugetConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryRawConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactConfigToDelegateReqMapper {
  private final String ACCEPT_ALL_REGEX = "\\*";

  public DockerArtifactDelegateRequest getDockerDelegateRequest(DockerHubArtifactConfig artifactConfig,
      DockerConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getDockerDelegateRequest(artifactConfig.getImagePath().getValue(), tag,
        tagRegex, null, connectorRef, connectorDTO, encryptedDataDetails, ArtifactSourceType.DOCKER_REGISTRY);
  }

  public S3ArtifactDelegateRequest getAmazonS3DelegateRequest(AmazonS3ArtifactConfig artifactConfig,
      AwsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String bucket = artifactConfig.getBucketName().getValue();
    String filePath = artifactConfig.getFilePath().getValue();
    String filePathRegex = artifactConfig.getFilePathRegex().getValue();

    if (StringUtils.isBlank(bucket)) {
      throw new InvalidRequestException("Please input bucketName.");
    }

    if (StringUtils.isAllBlank(filePathRegex, filePath)) {
      throw new InvalidRequestException("Please input one of the field - filePath or filePathRegex.");
    }

    if (StringUtils.isBlank(filePath)) {
      filePath = "";
    }

    if (StringUtils.isBlank(filePathRegex)) {
      filePathRegex = "";
    }

    return ArtifactDelegateRequestUtils.getAmazonS3DelegateRequest(artifactConfig.getBucketName().getValue(), filePath,
        filePathRegex, null, connectorRef, connectorDTO, encryptedDataDetails, ArtifactSourceType.AMAZONS3,
        artifactConfig.getRegion() != null ? artifactConfig.getRegion().getValue() : "us-east-1");
  }

  public GithubPackagesArtifactDelegateRequest getGithubPackagesDelegateRequest(
      GithubPackagesArtifactConfig artifactConfig, GithubConnectorDTO connectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String versionRegex = artifactConfig.getVersionRegex() != null
        ? (StringUtils.isBlank(artifactConfig.getVersionRegex().getValue())
                ? ""
                : artifactConfig.getVersionRegex().getValue())
        : "";
    String version = artifactConfig.getVersion() != null
        ? StringUtils.isBlank(artifactConfig.getVersion().getValue()) ? "" : artifactConfig.getVersion().getValue()
        : "";

    // If both version and versionRegex are empty, versionRegex is latest among all versions.
    if (isEmpty(version) && isEmpty(versionRegex)) {
      versionRegex = "*";
    }

    return ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(artifactConfig.getPackageName().getValue(),
        artifactConfig.getPackageType().getValue(), version, versionRegex, artifactConfig.getOrg().getValue(),
        connectorRef, connectorDTO, encryptedDataDetails, ArtifactSourceType.GITHUB_PACKAGES);
  }

  public AzureArtifactsDelegateRequest getAzureArtifactsDelegateRequest(AzureArtifactsConfig artifactConfig,
      AzureArtifactsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String versionRegex = artifactConfig.getVersionRegex().getValue();

    if (StringUtils.isBlank(versionRegex)) {
      versionRegex = "";
    }

    String version = artifactConfig.getVersion().getValue();

    if (StringUtils.isBlank(version)) {
      version = "";
    }

    // If both version and versionRegex are empty, throw exception.
    if (StringUtils.isAllBlank(version, versionRegex)) {
      throw new InvalidRequestException("Please specify version or versionRegex. Both cannot be empty.");
    }

    return ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(artifactConfig.getPackageName().getValue(),
        artifactConfig.getPackageType().getValue(), version, versionRegex, artifactConfig.getProject().getValue(),
        artifactConfig.getScope().getValue(), artifactConfig.getFeed().getValue(), connectorRef, connectorDTO,
        encryptedDataDetails, ArtifactSourceType.AZURE_ARTIFACTS);
  }

  public AMIArtifactDelegateRequest getAMIDelegateRequest(AMIArtifactConfig artifactConfig,
      AwsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String versionRegex = artifactConfig.getVersionRegex().getValue();

    if (StringUtils.isBlank(versionRegex)) {
      versionRegex = "";
    }

    String version = artifactConfig.getVersion().getValue();

    if (StringUtils.isBlank(version)) {
      version = "";
    }

    // If both version and versionRegex are empty, throw exception.
    if (StringUtils.isAllBlank(version, versionRegex)) {
      throw new InvalidRequestException("Please specify version or versionRegex. Both cannot be empty.");
    }

    return ArtifactDelegateRequestUtils.getAMIArtifactDelegateRequest(artifactConfig.getTags().getValue(),
        artifactConfig.getFilters().getValue(), artifactConfig.getRegion().getValue(), version, versionRegex,
        connectorRef, connectorDTO, encryptedDataDetails, ArtifactSourceType.AMI);
  }

  public JenkinsArtifactDelegateRequest getJenkinsDelegateRequest(JenkinsArtifactConfig artifactConfig,
      JenkinsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String artifactPath = artifactConfig.getArtifactPath() != null ? artifactConfig.getArtifactPath().getValue() : "";
    String jobName = artifactConfig.getJobName() != null ? artifactConfig.getJobName().getValue() : "";
    String buildNumber = artifactConfig.getBuild() != null ? artifactConfig.getBuild().getValue() : "";
    return ArtifactDelegateRequestUtils.getJenkinsDelegateArtifactRequest(connectorRef, connectorDTO,
        encryptedDataDetails, ArtifactSourceType.JENKINS, null, null, jobName, Arrays.asList(artifactPath),
        buildNumber);
  }
  public CustomArtifactDelegateRequest getCustomDelegateRequest(
      CustomArtifactConfig artifactConfig, Ambiance ambiance) {
    long timeout = 600000L;
    CustomScriptInlineSource customScriptInlineSource = (CustomScriptInlineSource) artifactConfig.getScripts()
                                                            .getFetchAllArtifacts()
                                                            .getShellScriptBaseStepInfo()
                                                            .getSource()
                                                            .getSpec();
    if (EmptyPredicate.isNotEmpty(customScriptInlineSource.getScript().getValue())) {
      if (isEmpty(artifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath().getValue())) {
        throw new InvalidArtifactServerException("Artifacts Array Path is missing", Level.ERROR, USER);
      }
      if (isEmpty(artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().getValue())) {
        throw new InvalidArtifactServerException("Version Path is missing", Level.ERROR, USER);
      }
    }

    if (artifactConfig.getTimeout() != null && artifactConfig.getTimeout().getValue() != null
        && isNotEmpty(artifactConfig.getTimeout().getValue().toString())) {
      timeout = artifactConfig.getTimeout().getValue().getTimeoutInMillis();
    }

    return ArtifactDelegateRequestUtils.getCustomDelegateRequest(
        artifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath().getValue(),
        artifactConfig.getVersionRegex().getValue(),
        artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource().getType(),
        ArtifactSourceType.CUSTOM_ARTIFACT,
        artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().getValue(),
        customScriptInlineSource.getScript().fetchFinalValue().toString(),
        NGVariablesUtils.getStringMapVariables(artifactConfig.getScripts().getFetchAllArtifacts().getAttributes(), 0L),
        NGVariablesUtils.getStringMapVariables(artifactConfig.getInputs(), 0L), artifactConfig.getVersion().getValue(),
        ambiance != null ? AmbianceUtils.obtainCurrentRuntimeId(ambiance) : "", timeout,
        AmbianceUtils.getAccountId(ambiance));
  }

  public GcrArtifactDelegateRequest getGcrDelegateRequest(GcrArtifactConfig gcrArtifactConfig,
      GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all gcr artifacts.
    String tagRegex = gcrArtifactConfig.getTagRegex() != null ? gcrArtifactConfig.getTagRegex().getValue() : "";
    String tag = gcrArtifactConfig.getTag() != null ? gcrArtifactConfig.getTag().getValue() : "";
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getGcrDelegateRequest(gcrArtifactConfig.getImagePath().getValue(), tag,
        tagRegex, null, gcrArtifactConfig.getRegistryHostname().getValue(), connectorRef, gcpConnectorDTO,
        encryptedDataDetails, ArtifactSourceType.GCR);
  }
  public GarDelegateRequest getGarDelegateRequest(GoogleArtifactRegistryConfig garArtifactConfig,
      GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all gcr artifacts.
    String versionRegex =
        garArtifactConfig.getVersionRegex() != null ? garArtifactConfig.getVersionRegex().getValue() : "";
    String version = garArtifactConfig.getVersion() != null ? garArtifactConfig.getVersion().getValue() : "";
    if (StringUtils.isBlank(version) && StringUtils.isBlank(versionRegex)) {
      versionRegex = "/*";
    }
    return ArtifactDelegateRequestUtils.getGoogleArtifactDelegateRequest(garArtifactConfig.getRegion().getValue(),
        garArtifactConfig.getRepositoryName().getValue(), garArtifactConfig.getProject().getValue(),
        garArtifactConfig.getPkg().getValue(), version, versionRegex, gcpConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, Integer.MAX_VALUE);
  }

  public EcrArtifactDelegateRequest getEcrDelegateRequest(EcrArtifactConfig ecrArtifactConfig,
      AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all ecr artifacts.
    String tagRegex = ecrArtifactConfig.getTagRegex() != null ? ecrArtifactConfig.getTagRegex().getValue() : "";
    String tag = ecrArtifactConfig.getTag() != null ? ecrArtifactConfig.getTag().getValue() : "";
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getEcrDelegateRequest(ecrArtifactConfig.getImagePath().getValue(), tag,
        tagRegex, null, ecrArtifactConfig.getRegion().getValue(), connectorRef, awsConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.ECR);
  }

  public NexusArtifactDelegateRequest getNexusArtifactDelegateRequest(NexusRegistryArtifactConfig artifactConfig,
      NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    String packageName = null;
    String groupId = null;
    String artifactId = null;
    String extension = null;
    String classifier = null;
    String port = null;
    String artifactRepositoryUrl = null;
    String group = null;
    if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("npm")) {
      NexusRegistryNpmConfig nexusRegistryNpmConfig =
          (NexusRegistryNpmConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = nexusRegistryNpmConfig.getPackageName().getValue();
    } else if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("nuget")) {
      NexusRegistryNugetConfig nexusRegistryNugetConfig =
          (NexusRegistryNugetConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = nexusRegistryNugetConfig.getPackageName().getValue();
    } else if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("raw")) {
      NexusRegistryRawConfig nexusRegistryRawConfig =
          (NexusRegistryRawConfig) artifactConfig.getNexusRegistryConfigSpec();
      group = nexusRegistryRawConfig.getGroup().getValue();
    } else if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("docker")) {
      NexusRegistryDockerConfig nexusRegistryDockerConfig =
          (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();
      port = nexusRegistryDockerConfig.getRepositoryPort() != null
          ? nexusRegistryDockerConfig.getRepositoryPort().getValue()
          : null;
      artifactRepositoryUrl = nexusRegistryDockerConfig.getRepositoryUrl() != null
          ? nexusRegistryDockerConfig.getRepositoryUrl().getValue()
          : null;
      artifactId = nexusRegistryDockerConfig.getArtifactPath() != null
          ? nexusRegistryDockerConfig.getArtifactPath().getValue()
          : null;
    } else {
      NexusRegistryMavenConfig nexusRegistryMavenConfig =
          (NexusRegistryMavenConfig) artifactConfig.getNexusRegistryConfigSpec();
      groupId = nexusRegistryMavenConfig.getGroupId().getValue();
      artifactId = nexusRegistryMavenConfig.getArtifactId().getValue();
      extension = nexusRegistryMavenConfig.getExtension().getValue();
      classifier = nexusRegistryMavenConfig.getClassifier().getValue();
    }

    return ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(artifactConfig.getRepository().getValue(), port,
        artifactId, artifactConfig.getRepositoryFormat().getValue(), artifactRepositoryUrl, tag, tagRegex, connectorRef,
        nexusConnectorDTO, encryptedDataDetails, ArtifactSourceType.NEXUS3_REGISTRY, groupId, artifactId, extension,
        classifier, packageName, group);
  }

  public NexusArtifactDelegateRequest getNexus2ArtifactDelegateRequest(Nexus2RegistryArtifactConfig artifactConfig,
      NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    String packageName = null;
    String groupId = null;
    String artifactId = null;
    String extension = null;
    String classifier = null;
    if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("npm")) {
      NexusRegistryNpmConfig nexusRegistryNpmConfig =
          (NexusRegistryNpmConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = nexusRegistryNpmConfig.getPackageName().getValue();
    } else if (artifactConfig.getRepositoryFormat().getValue().equalsIgnoreCase("nuget")) {
      NexusRegistryNugetConfig nexusRegistryNugetConfig =
          (NexusRegistryNugetConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = nexusRegistryNugetConfig.getPackageName().getValue();
    } else {
      NexusRegistryMavenConfig nexusRegistryMavenConfig =
          (NexusRegistryMavenConfig) artifactConfig.getNexusRegistryConfigSpec();
      groupId = nexusRegistryMavenConfig.getGroupId().getValue();
      artifactId = nexusRegistryMavenConfig.getArtifactId().getValue();
      extension = nexusRegistryMavenConfig.getExtension().getValue();
      classifier = nexusRegistryMavenConfig.getClassifier().getValue();
    }

    return ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(artifactConfig.getRepository().getValue(), null,
        null, artifactConfig.getRepositoryFormat().getValue(), null, tag, tagRegex, connectorRef, nexusConnectorDTO,
        encryptedDataDetails, ArtifactSourceType.NEXUS2_REGISTRY, groupId, artifactId, extension, classifier,
        packageName, "");
  }

  public ArtifactSourceDelegateRequest getArtifactoryArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    if (artifactConfig.getRepositoryFormat().getValue().equals(generic.name())) {
      return ArtifactConfigToDelegateReqMapper.getArtifactoryGenericArtifactDelegateRequest(
          artifactConfig, artifactoryConnectorDTO, encryptedDataDetails, connectorRef);
    } else {
      return ArtifactConfigToDelegateReqMapper.getArtifactoryDockerArtifactDelegateRequest(
          artifactConfig, artifactoryConnectorDTO, encryptedDataDetails, connectorRef);
    }
  }

  private ArtifactoryArtifactDelegateRequest getArtifactoryDockerArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    String artifactRepositoryUrl =
        artifactConfig.getRepositoryUrl() != null ? artifactConfig.getRepositoryUrl().getValue() : null;

    return (ArtifactoryArtifactDelegateRequest) ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(
        artifactConfig.getRepository().getValue(), artifactConfig.getArtifactPath().getValue(),
        artifactConfig.getRepositoryFormat().getValue(), artifactRepositoryUrl, tag, tagRegex, connectorRef,
        artifactoryConnectorDTO, encryptedDataDetails, ArtifactSourceType.ARTIFACTORY_REGISTRY);
  }

  private ArtifactoryGenericArtifactDelegateRequest getArtifactoryGenericArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, artifactPathFilter is latest among all artifacts.
    String artifactPathFilter = ParameterField.isNull(artifactConfig.getArtifactPathFilter())
        ? ""
        : artifactConfig.getArtifactPathFilter().getValue();
    String artifactPath =
        ParameterField.isNull(artifactConfig.getArtifactPath()) ? "" : artifactConfig.getArtifactPath().getValue();

    String artifactDirectory = ParameterField.isNull(artifactConfig.getArtifactDirectory())
        ? null
        : artifactConfig.getArtifactDirectory().getValue();

    return ArtifactDelegateRequestUtils.getArtifactoryGenericArtifactDelegateRequest(
        artifactConfig.getRepository().getValue(), artifactConfig.getRepositoryFormat().getValue(), artifactDirectory,
        artifactPath, artifactPathFilter, connectorRef, artifactoryConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.ARTIFACTORY_REGISTRY);
  }

  public static ArtifactSourceDelegateRequest getAcrDelegateRequest(AcrArtifactConfig acrArtifactConfig,
      AzureConnectorDTO azureConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all acr artifacts.
    String tagRegex =
        ParameterField.isNull(acrArtifactConfig.getTagRegex()) ? "" : acrArtifactConfig.getTagRegex().getValue();
    String tag = ParameterField.isNull(acrArtifactConfig.getTag()) ? "" : acrArtifactConfig.getTag().getValue();
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getAcrDelegateRequest(acrArtifactConfig.getSubscriptionId().getValue(),
        acrArtifactConfig.getRegistry().getValue(), acrArtifactConfig.getRepository().getValue(), azureConnectorDTO,
        tag, tagRegex, null, encryptedDataDetails, ArtifactSourceType.ACR);
  }
}
