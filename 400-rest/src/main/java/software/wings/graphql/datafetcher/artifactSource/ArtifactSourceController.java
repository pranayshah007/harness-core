/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifactSource;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureMachineImageArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.GcsArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.artifact.SftpArtifactStream;
import software.wings.beans.artifact.SmbArtifactStream;
import software.wings.graphql.schema.type.QLAzureImageDefinition;
import software.wings.graphql.schema.type.QLKeyValuePair;
import software.wings.graphql.schema.type.artifactSource.QLACRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAMIArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAmazonS3ArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryDockerProps;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryFileProps;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryProps;
import software.wings.graphql.schema.type.artifactSource.QLAzureArtifactsArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAzureMachineImageArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLBambooArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLCustomArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLDockerArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLECRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLGCRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLGCSArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLJenkinsArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusDockerProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusMavenProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusNpmProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusNugetProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusRawProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusRepositoryFormat;
import software.wings.graphql.schema.type.artifactSource.QLSFTPArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLSMBArtifactSource;
import software.wings.utils.RepositoryFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ArtifactSourceController {
  private ArtifactSourceController() {}

  public static QLArtifactSource populateArtifactSource(ArtifactStream artifactStream) {
    ArtifactStreamType artifactStreamType = ArtifactStreamType.valueOf(artifactStream.getArtifactStreamType());
    switch (artifactStreamType) {
      case ACR:
        AcrArtifactStream acrArtifactStream = (AcrArtifactStream) artifactStream;
        return QLACRArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .azureCloudProviderId(acrArtifactStream.getSettingId())
            .registryName(acrArtifactStream.getRegistryName())
            .repositoryName(acrArtifactStream.getRepositoryName())
            .subscriptionId(acrArtifactStream.getSubscriptionId())
            .build();

      case AMI:
        AmiArtifactStream amiArtifactStream = (AmiArtifactStream) artifactStream;
        List<AmiArtifactStream.FilterClass> filters =
            Optional.ofNullable(amiArtifactStream.getFilters()).orElse(new ArrayList<>());
        List<AmiArtifactStream.Tag> tags = Optional.ofNullable(amiArtifactStream.getTags()).orElse(new ArrayList<>());
        return QLAMIArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .awsCloudProviderId(amiArtifactStream.getSettingId())
            .region(amiArtifactStream.getRegion())
            .amiResourceFilters(
                filters.stream()
                    .map(filter -> QLKeyValuePair.builder().key(filter.getKey()).value(filter.getValue()).build())
                    .collect(Collectors.toList()))
            .awsTags(tags.stream()
                         .map(tag -> QLKeyValuePair.builder().key(tag.getKey()).value(tag.getValue()).build())
                         .collect(Collectors.toList()))
            .build();

      case ECR:
        EcrArtifactStream ecrArtifactStream = (EcrArtifactStream) artifactStream;
        return QLECRArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .awsCloudProviderId(ecrArtifactStream.getSettingId())
            .imageName(ecrArtifactStream.getImageName())
            .region(ecrArtifactStream.getRegion())
            .build();

      case GCR:
        GcrArtifactStream gcrArtifactStream = (GcrArtifactStream) artifactStream;
        return QLGCRArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .gcpCloudProviderId(gcrArtifactStream.getSettingId())
            .dockerImageName(gcrArtifactStream.getDockerImageName())
            .registryHostName(gcrArtifactStream.getRegistryHostName())
            .build();

      case GCS:
        GcsArtifactStream gcsArtifactStream = (GcsArtifactStream) artifactStream;
        return QLGCSArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .gcpCloudProviderId(gcsArtifactStream.getSettingId())
            .artifactPaths(gcsArtifactStream.getArtifactPaths())
            .bucket(gcsArtifactStream.getJobname())
            .projectId(gcsArtifactStream.getProjectId())
            .build();

      case SMB:
        SmbArtifactStream smbArtifactStream = (SmbArtifactStream) artifactStream;
        return QLSMBArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .artifactPaths(smbArtifactStream.getArtifactPaths())
            .smbConnectorId(smbArtifactStream.getSettingId())
            .build();

      case SFTP:
        SftpArtifactStream sftpArtifactStream = (SftpArtifactStream) artifactStream;
        return QLSFTPArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .artifactPaths(sftpArtifactStream.getArtifactPaths())
            .sftpConnectorId(sftpArtifactStream.getSettingId())
            .build();

      case NEXUS:
        NexusArtifactStream nexusArtifactStream = (NexusArtifactStream) artifactStream;
        return QLNexusArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .properties(generateNexusProps(nexusArtifactStream))
            .parameters(Collections.emptyList())
            .build();

      case BAMBOO:
        BambooArtifactStream bambooArtifactStream = (BambooArtifactStream) artifactStream;
        return QLBambooArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .bambooConnectorId(bambooArtifactStream.getSettingId())
            .artifactPaths(bambooArtifactStream.getArtifactPaths())
            .planKey(bambooArtifactStream.getJobname())
            .createdAt(artifactStream.getCreatedAt())
            .build();

      case CUSTOM:
        return QLCustomArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .build();

      case DOCKER:
        DockerArtifactStream dockerArtifactStream = (DockerArtifactStream) artifactStream;
        return QLDockerArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .imageName(dockerArtifactStream.getImageName())
            .dockerConnectorId(dockerArtifactStream.getSettingId())
            .build();

      case JENKINS:
        JenkinsArtifactStream jenkinsArtifactStream = (JenkinsArtifactStream) artifactStream;
        return QLJenkinsArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .artifactPaths(jenkinsArtifactStream.getArtifactPaths())
            .jobName(jenkinsArtifactStream.getJobname())
            .jenkinsConnectorId(jenkinsArtifactStream.getSettingId())
            .build();

      case AMAZON_S3:
        AmazonS3ArtifactStream amazonS3ArtifactStream = (AmazonS3ArtifactStream) artifactStream;
        return QLAmazonS3ArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .awsCloudProviderId(amazonS3ArtifactStream.getSettingId())
            .bucket(amazonS3ArtifactStream.getJobname())
            .artifactPaths(amazonS3ArtifactStream.getArtifactPaths())
            .build();

      case ARTIFACTORY:
        ArtifactoryArtifactStream artifactoryArtifactStream = (ArtifactoryArtifactStream) artifactStream;
        return QLArtifactoryArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .properties(generateArtifactoryProps(artifactoryArtifactStream))
            .build();

      case AZURE_ARTIFACTS:
        AzureArtifactsArtifactStream azureArtifactsArtifactStream = (AzureArtifactsArtifactStream) artifactStream;
        return QLAzureArtifactsArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .azureConnectorId(azureArtifactsArtifactStream.getSettingId())
            .packageName(azureArtifactsArtifactStream.getPackageName())
            .packageType(azureArtifactsArtifactStream.getProtocolType())
            .project(azureArtifactsArtifactStream.getProject())
            .feedName(azureArtifactsArtifactStream.getFeed())
            .scope(EmptyPredicate.isNotEmpty(azureArtifactsArtifactStream.getProject()) ? "PROJECT" : "ORGANIZATION")
            .build();

      case AZURE_MACHINE_IMAGE:
        AzureMachineImageArtifactStream azureMachineImageArtifactStream =
            (AzureMachineImageArtifactStream) artifactStream;
        return QLAzureMachineImageArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .azureCloudProviderId(azureMachineImageArtifactStream.getSettingId())
            .imageType(azureMachineImageArtifactStream.getImageType().name())
            .subscriptionId(azureMachineImageArtifactStream.getSubscriptionId())
            .imageDefinition(
                QLAzureImageDefinition.builder()
                    .resourceGroup(azureMachineImageArtifactStream.getImageDefinition().getResourceGroup())
                    .imageGalleryName(azureMachineImageArtifactStream.getImageDefinition().getImageGalleryName())
                    .imageDefinitionName(azureMachineImageArtifactStream.getImageDefinition().getImageDefinitionName())
                    .build())
            .build();

      default:
        throw new InvalidRequestException("Artifact stream type not supported: " + artifactStreamType);
    }
  }
  private static QLArtifactoryProps generateArtifactoryProps(ArtifactoryArtifactStream artifactoryArtifactStream) {
    if (EmptyPredicate.isNotEmpty(artifactoryArtifactStream.getImageName())) {
      return QLArtifactoryDockerProps.builder()
          .dockerImageName(artifactoryArtifactStream.getImageName())
          .dockerRepositoryServer(artifactoryArtifactStream.getDockerRepositoryServer())
          .artifactoryConnectorId(artifactoryArtifactStream.getSettingId())
          .repository(artifactoryArtifactStream.getJobname())
          .build();
    }
    return QLArtifactoryFileProps.builder()
        .artifactoryConnectorId(artifactoryArtifactStream.getSettingId())
        .repository(artifactoryArtifactStream.getJobname())
        .artifactPath(artifactoryArtifactStream.getArtifactPattern())
        .build();
  }

  private static QLNexusProps generateNexusProps(NexusArtifactStream artifactStream) {
    RepositoryFormat repositoryType = RepositoryFormat.valueOf(artifactStream.getRepositoryFormat());
    switch (repositoryType) {
      case docker:
        return QLNexusDockerProps.builder()
            .nexusConnectorId(artifactStream.getSettingId())
            .repository(artifactStream.getJobname())
            .repositoryFormat(QLNexusRepositoryFormat.DOCKER)
            .dockerImageName(artifactStream.getImageName())
            .dockerRegistryUrl(artifactStream.getDockerRegistryUrl())
            .build();
      case maven:
        return QLNexusMavenProps.builder()
            .nexusConnectorId(artifactStream.getSettingId())
            .repository(artifactStream.getJobname())
            .repositoryFormat(QLNexusRepositoryFormat.MAVEN)
            .groupId(artifactStream.getGroupId())
            .artifactId(artifactStream.getArtifactPaths())
            .classifier(artifactStream.getClassifier())
            .extension(artifactStream.getExtension())
            .build();
      case npm:
        return QLNexusNpmProps.builder()
            .nexusConnectorId(artifactStream.getSettingId())
            .repository(artifactStream.getJobname())
            .repositoryFormat(QLNexusRepositoryFormat.NPM)
            .packageName(artifactStream.getPackageName())
            .build();
      case nuget:
        return QLNexusNugetProps.builder()
            .nexusConnectorId(artifactStream.getSettingId())
            .repository(artifactStream.getJobname())
            .repositoryFormat(QLNexusRepositoryFormat.NPM)
            .packageName(artifactStream.getPackageName())
            .build();
      case raw:
        return QLNexusRawProps.builder()
            .nexusConnectorId(artifactStream.getSettingId())
            .repository(artifactStream.getJobname())
            .repositoryFormat(QLNexusRepositoryFormat.RAW)
            .packageName(artifactStream.getPackageName())
            .build();
      default:
        throw new InvalidRequestException(
            "Nexus RepositoryType type not supported: " + artifactStream.getRepositoryType());
    }
  }

  public static QLArtifactSource populateArtifactSource(ArtifactStream artifactStream, List<String> parameters) {
    ArtifactStreamType artifactStreamType = ArtifactStreamType.valueOf(artifactStream.getArtifactStreamType());
    switch (artifactStreamType) {
      case NEXUS:
        return QLNexusArtifactSource.builder()
            .id(artifactStream.getUuid())
            .name(artifactStream.getName())
            .createdAt(artifactStream.getCreatedAt())
            .parameters(parameters)
            .build();
      default:
        throw new InvalidRequestException(
            format("Artifact stream type [%s]does not support parameters: ", artifactStreamType));
    }
  }
}
