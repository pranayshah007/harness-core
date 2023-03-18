/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts;

import static software.wings.utils.RepositoryType.generic;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.helpers.ext.jenkins.JobDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactDelegateRequestUtils {
  private static final String DEFAULT_REGION_AWS = "us-east-1";

  public GcrArtifactDelegateRequest getGcrDelegateRequest(String imagePath, String tag, String tagRegex,
      List<String> tagsList, String registryHostname, String connectorRef, GcpConnectorDTO gcpConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType) {
    return GcrArtifactDelegateRequest.builder()
        .imagePath(trim(imagePath))
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .tagsList(tagsList)
        .registryHostname(trim(registryHostname))
        .connectorRef(connectorRef)
        .gcpConnectorDTO(gcpConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }

  public EcrArtifactDelegateRequest getEcrDelegateRequest(String imagePath, String tag, String tagRegex,
      List<String> tagsList, String region, String connectorRef, AwsConnectorDTO awsConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType) {
    return EcrArtifactDelegateRequest.builder()
        .imagePath(trim(imagePath))
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .tagsList(tagsList)
        .region(trim(region))
        .connectorRef(connectorRef)
        .awsConnectorDTO(awsConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }
  public GarDelegateRequest getGoogleArtifactDelegateRequest(String region, String repositoryName, String project,
      String pkg, String version, String versionRegex, GcpConnectorDTO gcpConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType, int maxBuilds) {
    return GarDelegateRequest.builder()
        .region(trim(region))
        .project(trim(project))
        .maxBuilds(maxBuilds == -1 ? Integer.MAX_VALUE : maxBuilds)
        .repositoryName(trim(repositoryName))
        .gcpConnectorDTO(gcpConnectorDTO)
        .sourceType(sourceType)
        .pkg(trim(pkg))
        .versionRegex(versionRegex)
        .version(version)
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }
  public DockerArtifactDelegateRequest getDockerDelegateRequest(String imagePath, String tag, String tagRegex,
      List<String> tagsList, String connectorRef, DockerConnectorDTO dockerConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType,
      Boolean shouldFetchDockerV2DigestSHA256) {
    return DockerArtifactDelegateRequest.builder()
        .imagePath(trim(imagePath))
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .tagsList(tagsList)
        .connectorRef(connectorRef)
        .dockerConnectorDTO(dockerConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .shouldFetchDockerV2DigestSHA256(shouldFetchDockerV2DigestSHA256)
        .build();
  }
  public NexusArtifactDelegateRequest getNexusArtifactDelegateRequest(String repositoryName, String repositoryPort,
      String imagePath, String repositoryFormat, String artifactRepositoryUrl, String tag, String tagRegex,
      String connectorRef, NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType, String groupId, String artifactId) {
    return NexusArtifactDelegateRequest.builder()
        .repositoryName(repositoryName)
        .repositoryPort(repositoryPort)
        .artifactPath(trim(imagePath))
        .repositoryFormat(repositoryFormat)
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .connectorRef(connectorRef)
        .nexusConnectorDTO(nexusConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .artifactRepositoryUrl(artifactRepositoryUrl)
        .groupId(groupId)
        .artifactId(artifactId)
        .build();
  }

  public NexusArtifactDelegateRequest getNexusArtifactDelegateRequest(String repositoryName, String repositoryPort,
      String imagePath, String repositoryFormat, String artifactRepositoryUrl, String tag, String tagRegex,
      String connectorRef, NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType, String groupId, String artifactName, String extension, String classifier,
      String packageName, String group) {
    return NexusArtifactDelegateRequest.builder()
        .repositoryName(repositoryName)
        .repositoryPort(repositoryPort)
        .artifactPath(trim(imagePath))
        .repositoryFormat(repositoryFormat)
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .connectorRef(connectorRef)
        .nexusConnectorDTO(nexusConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .artifactRepositoryUrl(artifactRepositoryUrl)
        .groupId(groupId)
        .artifactName(artifactName)
        .extension(extension)
        .classifier(classifier)
        .packageName(packageName)
        .group(group)
        .maxBuilds(Integer.MAX_VALUE)
        .build();
  }

  public ArtifactSourceDelegateRequest getArtifactoryArtifactDelegateRequest(String repositoryName, String artifactPath,
      String repositoryFormat, String artifactRepositoryUrl, String tag, String tagRegex, String connectorRef,
      ArtifactoryConnectorDTO artifactoryConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType) {
    if ((!isNull(repositoryFormat)) && repositoryFormat.equals(generic.name())) {
      String artifactDirectory = artifactPath;
      if (artifactDirectory.isEmpty()) {
        artifactDirectory = "/";
      }
      return getArtifactoryGenericArtifactDelegateRequest(repositoryName, repositoryFormat, artifactDirectory, null,
          null, null, artifactoryConnectorDTO, encryptedDataDetails, ArtifactSourceType.ARTIFACTORY_REGISTRY);
    }
    return ArtifactoryArtifactDelegateRequest.builder()
        .repositoryName(repositoryName)
        .artifactPath(trim(artifactPath))
        .repositoryFormat(repositoryFormat)
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .connectorRef(connectorRef)
        .artifactoryConnectorDTO(artifactoryConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .artifactRepositoryUrl(artifactRepositoryUrl)
        .build();
  }

  public AcrArtifactDelegateRequest getAcrDelegateRequest(String subscription, String registry, String repository,
      AzureConnectorDTO azureConnectorDTO, String tag, String tagRegex, List<String> tagsList,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType) {
    return AcrArtifactDelegateRequest.builder()
        .subscription(subscription)
        .tag(trim(tag))
        .tagRegex(trim(tagRegex))
        .tagsList(tagsList)
        .registry(registry)
        .repository(repository)
        .azureConnectorDTO(azureConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }

  public ArtifactoryGenericArtifactDelegateRequest getArtifactoryGenericArtifactDelegateRequest(String repositoryName,
      String repositoryFormat, String artifactDirectory, String artifactPath, String artifactPathFilter,
      String connectorRef, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType) {
    return ArtifactoryGenericArtifactDelegateRequest.builder()
        .repositoryName(repositoryName)
        .repositoryFormat(repositoryFormat)
        .artifactDirectory(artifactDirectory)
        .artifactPath(artifactPath)
        .artifactPathFilter(artifactPathFilter)
        .connectorRef(connectorRef)
        .artifactoryConnectorDTO(artifactoryConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }

  public JenkinsArtifactDelegateRequest getJenkinsDelegateRequest(String connectorRef,
      JenkinsConnectorDTO jenkinsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType, List<JobDetails> jobDetails, String parentJobName, String jobName,
      List<String> artifactPath) {
    return JenkinsArtifactDelegateRequest.builder()
        .connectorRef(connectorRef)
        .jenkinsConnectorDTO(jenkinsConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .jobDetails(jobDetails)
        .parentJobName(parentJobName)
        .jobName(jobName)
        .artifactPaths(artifactPath)
        .build();
  }

  public JenkinsArtifactDelegateRequest getJenkinsDelegateArtifactRequest(String connectorRef,
      JenkinsConnectorDTO jenkinsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType, List<JobDetails> jobDetails, String parentJobName, String jobName,
      List<String> artifactPath, String BuildNumber) {
    return JenkinsArtifactDelegateRequest.builder()
        .connectorRef(connectorRef)
        .jenkinsConnectorDTO(jenkinsConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .jobDetails(jobDetails)
        .parentJobName(parentJobName)
        .jobName(jobName)
        .artifactPaths(artifactPath)
        .buildNumber(BuildNumber)
        .build();
  }

  public BambooArtifactDelegateRequest getBambooDelegateArtifactRequest(String connectorRef,
      BambooConnectorDTO jenkinsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType, String planKey, List<String> artifactPath, String BuildNumber) {
    return BambooArtifactDelegateRequest.builder()
        .connectorRef(connectorRef)
        .bambooConnectorDTO(jenkinsConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .planKey(planKey)
        .artifactPaths(artifactPath)
        .buildNumber(BuildNumber)
        .build();
  }

  public JenkinsArtifactDelegateRequest getJenkinsDelegateRequest(String connectorRef,
      JenkinsConnectorDTO jenkinsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails,
      ArtifactSourceType sourceType, List<JobDetails> jobDetails, String parentJobName, String jobName,
      List<String> artifactPath, Map<String, String> jobParameter) {
    return JenkinsArtifactDelegateRequest.builder()
        .connectorRef(connectorRef)
        .jenkinsConnectorDTO(jenkinsConnectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .jobDetails(jobDetails)
        .parentJobName(parentJobName)
        .jobName(jobName)
        .artifactPaths(artifactPath)
        .jobParameter(jobParameter)
        .build();
  }

  public static S3ArtifactDelegateRequest getAmazonS3DelegateRequest(String bucketName, String filePath,
      String filePathRegex, Object o, String connectorRef, AwsConnectorDTO connectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType, String region) {
    return S3ArtifactDelegateRequest.builder()
        .bucketName(trim(bucketName))
        .filePath(trim(filePath))
        .filePathRegex(trim(filePathRegex))
        .connectorRef(connectorRef)
        .region(EmptyPredicate.isNotEmpty(region) ? region : DEFAULT_REGION_AWS)
        .awsConnectorDTO(connectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }

  public CustomArtifactDelegateRequest getCustomDelegateRequest(String artifactsArrayPath, String versionRegex,
      String type, ArtifactSourceType sourceType, String versionPath, String script, Map<String, String> attributes,
      Map<String, String> inputs, String version, String executionId, long timeout, String accountId) {
    return CustomArtifactDelegateRequest.builder()
        .artifactsArrayPath(artifactsArrayPath)
        .attributes(attributes)
        .versionRegex(trim(versionRegex))
        .sourceType(sourceType)
        .type(type)
        .versionPath(versionPath)
        .script(script)
        .timeout(timeout)
        .inputs(inputs)
        .version(version)
        .executionId(executionId)
        .workingDirectory("/tmp")
        .accountId(accountId)
        .build();
  }

  public CustomArtifactDelegateRequest getCustomDelegateRequest(String artifactsArrayPath, String versionRegex,
      String type, ArtifactSourceType sourceType, String versionPath, String script, Map<String, String> attributes,
      Map<String, String> inputs, String version, String executionId, long timeout, String accountId,
      Map<String, EncryptionConfig> encryptionConfigs, Map<String, SecretDetail> secretDetails, int expressionToken) {
    return CustomArtifactDelegateRequest.builder()
        .artifactsArrayPath(artifactsArrayPath)
        .attributes(attributes)
        .versionRegex(trim(versionRegex))
        .sourceType(sourceType)
        .type(type)
        .versionPath(versionPath)
        .script(script)
        .timeout(timeout)
        .inputs(inputs)
        .version(version)
        .executionId(executionId)
        .workingDirectory("/tmp")
        .accountId(accountId)
        .encryptionConfigs(encryptionConfigs)
        .secretDetails(secretDetails)
        .expressionFunctorToken(expressionToken)
        .build();
  }

  private String trim(String str) {
    return str == null ? null : str.trim();
  }

  public static GithubPackagesArtifactDelegateRequest getGithubPackagesDelegateRequest(String packageName,
      String packageType, String version, String versionRegex, String org, String connectorRef,
      GithubConnectorDTO githubConnector, List<EncryptedDataDetail> encryptionDetails,
      ArtifactSourceType artifactSourceType) {
    return GithubPackagesArtifactDelegateRequest.builder()
        .packageName(packageName)
        .githubConnectorDTO(githubConnector)
        .version(version)
        .versionRegex(versionRegex)
        .connectorRef(connectorRef)
        .encryptedDataDetails(encryptionDetails)
        .sourceType(artifactSourceType)
        .packageType(packageType)
        .org(org)
        .build();
  }

  public static AzureArtifactsDelegateRequest getAzureArtifactsDelegateRequest(String packageName, String packageType,
      String version, String versionRegex, String project, String scope, String feed, String connectorRef,
      AzureArtifactsConnectorDTO azureConnectorDTO, List<EncryptedDataDetail> encryptionDetails,
      ArtifactSourceType artifactSourceType) {
    return AzureArtifactsDelegateRequest.builder()
        .azureArtifactsConnectorDTO(azureConnectorDTO)
        .connectorRef(connectorRef)
        .encryptedDataDetails(encryptionDetails)
        .project(project)
        .scope(scope)
        .feed(feed)
        .packageType(packageType)
        .packageName(packageName)
        .version(version)
        .versionRegex(versionRegex)
        .sourceType(artifactSourceType)
        .build();
  }

  public static GoogleCloudSourceArtifactDelegateRequest getGoogleCloudSourceArtifactDelegateRequest(String repository,
      String project, String sourceDirectory, GcpConnectorDTO gcpConnectorDTO, String connectorRef,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType) {
    return GoogleCloudSourceArtifactDelegateRequest.builder()
        .repository(repository)
        .project(project)
        .sourceDirectory(sourceDirectory)
        .gcpConnectorDTO(gcpConnectorDTO)
        .connectorRef(connectorRef)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }

  public static AMIArtifactDelegateRequest getAMIArtifactDelegateRequest(List<AMITag> tags, List<AMIFilter> filters,
      String region, String version, String versionRegex, String connectorRef, AwsConnectorDTO awsConnectorDTO,
      List<EncryptedDataDetail> encryptionDetails, ArtifactSourceType artifactSourceType) {
    Map<String, List<String>> tagMap = new HashMap<>();

    Map<String, String> filterMap = new HashMap<>();

    if (tags != null) {
      Map<String, List<AMITag>> collect = tags.stream().collect(Collectors.groupingBy(AMITag::getName));
      tagMap = tags.stream()
                   .collect(Collectors.groupingBy(AMITag::getName))
                   .keySet()
                   .stream()
                   .collect(Collectors.toMap(identity(),
                       s -> collect.get(s).stream().map(tag -> tag.getValue()).collect(toList()), (a, b) -> b));
    }

    if (filters != null) {
      filterMap = filters.stream().collect(Collectors.toMap(AMIFilter::getName, AMIFilter::getValue, (a, b) -> b));
    }

    return AMIArtifactDelegateRequest.builder()
        .awsConnectorDTO(awsConnectorDTO)
        .connectorRef(connectorRef)
        .encryptedDataDetails(encryptionDetails)
        .version(version)
        .versionRegex(versionRegex)
        .region(region)
        .tags(tagMap)
        .filters(filterMap)
        .sourceType(artifactSourceType)
        .build();
  }

  public static GoogleCloudStorageArtifactDelegateRequest getGoogleCloudStorageArtifactDelegateRequest(String bucket,
      String project, String artifactPath, GcpConnectorDTO gcpConnectorDTO, String connectorRef,
      List<EncryptedDataDetail> encryptedDataDetails, ArtifactSourceType sourceType) {
    return GoogleCloudStorageArtifactDelegateRequest.builder()
        .bucket(bucket)
        .project(project)
        .artifactPath(artifactPath)
        .gcpConnectorDTO(gcpConnectorDTO)
        .connectorRef(connectorRef)
        .encryptedDataDetails(encryptedDataDetails)
        .sourceType(sourceType)
        .build();
  }
}
