/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;

import static software.wings.beans.Log.Builder.aLog;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnknownArtifactStreamTypeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;

import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.amazons3.AmazonS3Service;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.ArtifactoryConfigToArtifactoryRequestMapper;
import software.wings.service.mappers.artifact.NexusConfigToNexusRequestMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Helper class that has common collection logic that's used by all the artifact collection tasks.
 * @author rktummala
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactCollectionTaskHelper {
  private static final String ARTIFACT_STRING = "artifact/";
  private static final String ARTIFACT_FILE_SIZE_MESSAGE = "Getting artifact file size for artifact: ";
  private static final String ON_DELEGATE = " on delegate";
  @Inject private AmazonS3Service amazonS3Service;
  @Inject private DelegateLogService logService;
  @Inject private ArtifactoryService artifactoryService;
  @Inject private AzureArtifactsService azureArtifactsService;
  @Inject private EncryptionService encryptionService;
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private BambooService bambooService;
  @Inject private NexusService nexusService;

  public Pair<String, InputStream> downloadArtifactAtRuntime(ArtifactStreamAttributes artifactStreamAttributes,
      String accountId, String appId, String activityId, String commandUnitName, String hostName) {
    Map<String, String> metadata = artifactStreamAttributes.getMetadata();
    Pair<String, InputStream> pair = null;
    ArtifactStreamType artifactStreamType =
        ArtifactStreamType.valueOf(artifactStreamAttributes.getArtifactStreamType());
    switch (artifactStreamType) {
      case AMAZON_S3:
        log.info("Downloading artifact [{}] from bucket :[{}]", metadata.get(ArtifactMetadataKeys.artifactFileName),
            metadata.get(ArtifactMetadataKeys.bucketName));
        saveExecutionLog("Metadata only option set for AMAZON_S3. Starting download of artifact: "
                + metadata.get(ArtifactMetadataKeys.artifactFileName)
                + " from bucket: " + metadata.get(ArtifactMetadataKeys.bucketName)
                + " with key: " + metadata.get(ArtifactMetadataKeys.key),
            RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        pair = amazonS3Service.downloadArtifact((AwsConfig) artifactStreamAttributes.getServerSetting().getValue(),
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails(),
            metadata.get(ArtifactMetadataKeys.bucketName), metadata.get(ArtifactMetadataKeys.key));
        if (pair != null) {
          saveExecutionLog(
              "AMAZON_S3: Download complete for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName)
                  + " from bucket: " + metadata.get(ArtifactMetadataKeys.bucketName)
                  + " with key: " + metadata.get(ArtifactMetadataKeys.key),
              RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        } else {
          saveExecutionLog(
              "AMAZON_S3: Download failed for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName)
                  + " from bucket: " + metadata.get(ArtifactMetadataKeys.bucketName)
                  + " with key: " + metadata.get(ArtifactMetadataKeys.key),
              FAILURE, accountId, appId, activityId, commandUnitName, hostName);
        }
        return pair;
      case ARTIFACTORY:
        log.info("Downloading artifact [{}] from artifactory at path :[{}]",
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
        saveExecutionLog("Metadata only option set for ARTIFACTORY. Starting download of artifact: "
                + metadata.get(ArtifactMetadataKeys.artifactPath),
            RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
            (ArtifactoryConfig) artifactStreamAttributes.getServerSetting().getValue(), encryptionService,
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails());
        pair = artifactoryService.downloadArtifact(artifactoryRequest, artifactStreamAttributes.getJobName(), metadata);
        if (pair != null) {
          saveExecutionLog(
              "ARTIFACTORY: Download complete for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName),
              RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        } else {
          saveExecutionLog(
              "ARTIFACTORY: Download failed for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName),
              FAILURE, accountId, appId, activityId, commandUnitName, hostName);
        }
        return pair;
      case JENKINS:
        log.info("Downloading artifact [{}] from JENKINS at path :[{}] on delegate",
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
        saveExecutionLog("Metadata only option set for JENKINS. Starting download of artifact: "
                + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
            RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        JenkinsConfig jenkinsConfig = (JenkinsConfig) artifactStreamAttributes.getServerSetting().getValue();
        encryptionService.decrypt(
            jenkinsConfig, artifactStreamAttributes.getArtifactServerEncryptedDataDetails(), false);
        Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);

        try {
          String artifactPathRegex =
              metadata.get(ArtifactMetadataKeys.artifactPath)
                  .substring(metadata.get(ArtifactMetadataKeys.artifactPath).lastIndexOf(ARTIFACT_STRING)
                      + ARTIFACT_STRING.length());
          pair = jenkins.downloadArtifact(
              artifactStreamAttributes.getJobName(), metadata.get(ArtifactMetadataKeys.buildNo), artifactPathRegex);
          if (pair != null) {
            saveExecutionLog("JENKINS: Download complete for artifact: "
                    + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
                RUNNING, accountId, appId, activityId, commandUnitName, hostName);
          } else {
            saveExecutionLog("JENKINS: Download failed for artifact: "
                    + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
                FAILURE, accountId, appId, activityId, commandUnitName, hostName);
          }
        } catch (IOException | URISyntaxException e) {
          log.warn("Artifact download failed", e);
        }
        return pair;
      case AZURE_ARTIFACTS:
        log.info("Downloading artifact [{}] from azure artifacts with version: [{}]",
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.version));
        saveExecutionLog("Metadata only option set for AZURE ARTIFACTS. Starting download of artifact: "
                + metadata.get(ArtifactMetadataKeys.artifactFileName),
            RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        pair = azureArtifactsService.downloadArtifact(
            (AzureArtifactsConfig) artifactStreamAttributes.getServerSetting().getValue(),
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails(), artifactStreamAttributes, metadata);
        if (pair != null) {
          saveExecutionLog(
              "AZURE ARTIFACTS: Download complete for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName),
              RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        } else {
          saveExecutionLog(
              "AZURE ARTIFACTS: Download failed for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName),
              FAILURE, accountId, appId, activityId, commandUnitName, hostName);
        }
        return pair;
      case BAMBOO:
        log.info("Downloading artifact [{}] from BAMBOO at path :[{}] on delegate",
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
        saveExecutionLog("Metadata only option set for BAMBOO. Starting download of artifact: "
                + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
            RUNNING, accountId, appId, activityId, commandUnitName, hostName);

        try {
          pair = bambooService.downloadArtifact((BambooConfig) artifactStreamAttributes.getServerSetting().getValue(),
              artifactStreamAttributes.getArtifactServerEncryptedDataDetails(),
              metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
          if (pair != null) {
            saveExecutionLog("BAMBOO: Download complete for artifact: "
                    + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
                RUNNING, accountId, appId, activityId, commandUnitName, hostName);
          } else {
            saveExecutionLog("BAMBOO: Download failed for artifact: "
                    + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
                FAILURE, accountId, appId, activityId, commandUnitName, hostName);
          }
        } catch (ArtifactServerException e) {
          log.warn("Artifact download failed", e);
        }
        return pair;
      case NEXUS:
        log.info("Downloading artifact [{}] from Nexus with version: [{}] on delegate",
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.version));
        saveExecutionLog("Metadata only option set for NEXUS. Starting download of artifact: "
                + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
            RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        pair = nexusService.downloadArtifactByUrl(
            NexusConfigToNexusRequestMapper.toNexusRequest(
                (NexusConfig) artifactStreamAttributes.getServerSetting().getValue(), encryptionService,
                artifactStreamAttributes.getArtifactServerEncryptedDataDetails()),
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
        if (pair != null) {
          saveExecutionLog("NEXUS: Download complete for artifact: "
                  + metadata.get(ArtifactMetadataKeys.artifactFileName) + ON_DELEGATE,
              RUNNING, accountId, appId, activityId, commandUnitName, hostName);
        } else {
          saveExecutionLog("NEXUS: Download failed for artifact: " + metadata.get(ArtifactMetadataKeys.artifactFileName)
                  + ON_DELEGATE,
              FAILURE, accountId, appId, activityId, commandUnitName, hostName);
        }
        return pair;
      default:
        throw new UnknownArtifactStreamTypeException(artifactStreamType.name());
    }
  }

  public Long getArtifactFileSize(ArtifactStreamAttributes artifactStreamAttributes) {
    Map<String, String> metadata = artifactStreamAttributes.getMetadata();
    ArtifactStreamType artifactStreamType =
        ArtifactStreamType.valueOf(artifactStreamAttributes.getArtifactStreamType());
    switch (artifactStreamType) {
      case AMAZON_S3:
        log.info("Getting artifact file size for artifact " + metadata.get(ArtifactMetadataKeys.artifactFileName)
            + " in bucket: " + metadata.get(ArtifactMetadataKeys.bucketName)
            + " with key: " + metadata.get(ArtifactMetadataKeys.key));
        return amazonS3Service.getFileSize((AwsConfig) artifactStreamAttributes.getServerSetting().getValue(),
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails(),
            metadata.get(ArtifactMetadataKeys.bucketName), metadata.get(ArtifactMetadataKeys.key));
      case ARTIFACTORY:
        log.info(ARTIFACT_FILE_SIZE_MESSAGE + metadata.get(ArtifactMetadataKeys.artifactPath));
        ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
            (ArtifactoryConfig) artifactStreamAttributes.getServerSetting().getValue(), encryptionService,
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails());
        return artifactoryService.getFileSize(artifactoryRequest, artifactStreamAttributes.getMetadata());
      case AZURE_ARTIFACTS:
        log.info(ARTIFACT_FILE_SIZE_MESSAGE + metadata.get(ArtifactMetadataKeys.version));
        if (!metadata.containsKey(ArtifactMetadataKeys.artifactFileName)
            || isBlank(metadata.get(ArtifactMetadataKeys.artifactFileName))) {
          throw new InvalidArgumentsException(ImmutablePair.of(ArtifactMetadataKeys.artifactFileName, "not found"));
        }

        List<AzureArtifactsPackageFileInfo> fileInfos = azureArtifactsService.listFiles(
            (AzureArtifactsConfig) artifactStreamAttributes.getServerSetting().getValue(),
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails(), artifactStreamAttributes,
            artifactStreamAttributes.getMetadata(), false);
        if (isEmpty(fileInfos)) {
          throw new InvalidArgumentsException(ImmutablePair.of(
              ArtifactMetadataKeys.version, metadata.getOrDefault(ArtifactMetadataKeys.version, "unknown")));
        }

        String fileName = metadata.get(ArtifactMetadataKeys.artifactFileName);
        Optional<AzureArtifactsPackageFileInfo> optional =
            fileInfos.stream().filter(fileInfo -> fileName.equals(fileInfo.getName())).findFirst();
        if (!optional.isPresent()) {
          throw new InvalidArgumentsException(ImmutablePair.of(
              ArtifactMetadataKeys.version, metadata.getOrDefault(ArtifactMetadataKeys.version, "unknown")));
        }

        return optional.get().getSize();
      case JENKINS:
        if (!metadata.containsKey(ArtifactMetadataKeys.artifactFileName)
            || isBlank(metadata.get(ArtifactMetadataKeys.artifactFileName))) {
          throw new InvalidArgumentsException(ImmutablePair.of(ArtifactMetadataKeys.artifactFileName, "not found"));
        }
        log.info(ARTIFACT_FILE_SIZE_MESSAGE + metadata.get(ArtifactMetadataKeys.artifactFileName));
        JenkinsConfig jenkinsConfig = (JenkinsConfig) artifactStreamAttributes.getServerSetting().getValue();
        encryptionService.decrypt(
            jenkinsConfig, artifactStreamAttributes.getArtifactServerEncryptedDataDetails(), false);
        Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
        String artifactPathRegex =
            metadata.get(ArtifactMetadataKeys.artifactPath)
                .substring(metadata.get(ArtifactMetadataKeys.artifactPath).lastIndexOf(ARTIFACT_STRING)
                    + ARTIFACT_STRING.length());
        return jenkins.getFileSize(
            artifactStreamAttributes.getJobName(), metadata.get(ArtifactMetadataKeys.buildNo), artifactPathRegex);
      case BAMBOO:
        if (!metadata.containsKey(ArtifactMetadataKeys.artifactFileName)
            || isBlank(metadata.get(ArtifactMetadataKeys.artifactFileName))) {
          throw new InvalidArgumentsException(ImmutablePair.of(ArtifactMetadataKeys.artifactFileName, "not found"));
        }
        log.info(ARTIFACT_FILE_SIZE_MESSAGE + metadata.get(ArtifactMetadataKeys.artifactFileName));
        return bambooService.getFileSize((BambooConfig) artifactStreamAttributes.getServerSetting().getValue(),
            artifactStreamAttributes.getArtifactServerEncryptedDataDetails(),
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
      case NEXUS:
        if (!metadata.containsKey(ArtifactMetadataKeys.artifactFileName)
            || isBlank(metadata.get(ArtifactMetadataKeys.artifactFileName))) {
          throw new InvalidArgumentsException(ImmutablePair.of(ArtifactMetadataKeys.artifactFileName, "not found"));
        }
        log.info(ARTIFACT_FILE_SIZE_MESSAGE + metadata.get(ArtifactMetadataKeys.artifactFileName));
        return nexusService.getFileSize(
            NexusConfigToNexusRequestMapper.toNexusRequest(
                (NexusConfig) artifactStreamAttributes.getServerSetting().getValue(), encryptionService,
                artifactStreamAttributes.getArtifactServerEncryptedDataDetails()),
            metadata.get(ArtifactMetadataKeys.artifactFileName), metadata.get(ArtifactMetadataKeys.artifactPath));
      default:
        throw new UnknownArtifactStreamTypeException(artifactStreamType.name());
    }
  }

  private void saveExecutionLog(String line, CommandExecutionStatus commandExecutionStatus, String accountId,
      String appId, String activityId, String commandUnitName, String hostName) {
    logService.save(accountId,
        aLog()
            .appId(appId)
            .activityId(activityId)
            .logLevel(LogLevel.INFO)
            .logLine(line)
            .executionResult(commandExecutionStatus)
            .commandUnitName(commandUnitName)
            .hostName(hostName)
            .build());
  }
}
