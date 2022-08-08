/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.collect;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownArtifactStreamTypeException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.Artifact.ContentStatus;
import software.wings.beans.artifact.Artifact.Status;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.ArtifactoryCollectionTaskParameters;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.aws.AmazonS3CollectionTaskParameters;
import software.wings.beans.command.JenkinsTaskParams;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.delegatetasks.collect.artifacts.AzureArtifactsCollectionTaskParameters;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.MappingUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by peeyushaggarwal on 5/11/16.
 *
 * @see CollectEvent
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class ArtifactCollectEventListener extends QueueListener<CollectEvent> {
  @Inject private ArtifactService artifactService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private SecretManager secretManager;
  @Inject private FeatureFlagService featureFlagService;

  @Inject
  public ArtifactCollectEventListener(QueueConsumer<CollectEvent> queueConsumer) {
    super(queueConsumer, true);
  }

  /* (non-Javadoc)
   * @see software.wings.core.queue.QueueListener#onMessage(software.wings.core.queue.Queuable)
   */
  @Override
  public void onMessage(CollectEvent message) {
    Artifact artifact = message.getArtifact();
    String accountId = artifact.getAccountId();
    final String uuid = artifact.getUuid();

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ArtifactStreamLogContext(
             artifact.getArtifactStreamId(), artifact.getArtifactStreamType(), OVERRIDE_ERROR)) {
      log.info("Received artifact collection event");

      artifactService.updateStatus(uuid, accountId, Status.RUNNING, ContentStatus.DOWNLOADING);

      ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
      if (artifactStream == null) {
        throw new InvalidRequestException("Artifact Stream does not exist", USER);
      }

      String waitId = generateUuid();
      DelegateTask delegateTask = createDelegateTask(accountId, artifactStream, artifact, waitId);
      log.info("Registering callback for the artifact with waitId {}", waitId);
      waitNotifyEngine.waitForAllOn(GENERAL, new ArtifactCollectionCallback(uuid), waitId);
      log.info("Queuing delegate task of artifactSourceName {} ", artifact.getArtifactSourceName());
      delegateService.queueTask(delegateTask);
    } catch (Exception ex) {
      log.error("Failed to collect artifact. Reason {}", ExceptionUtils.getMessage(ex), ex);
      artifactService.updateStatus(uuid, accountId, Status.APPROVED, ContentStatus.FAILED);
    }
  }

  private DelegateTask createDelegateTask(
      String accountId, ArtifactStream artifactStream, Artifact artifact, String waitId) {
    Map<String, String> artifactMetadata = MappingUtils.safeCopy(artifact.getMetadata());
    switch (ArtifactStreamType.valueOf(artifactStream.getArtifactStreamType())) {
      case JENKINS: {
        JenkinsArtifactStream jenkinsArtifactStream = (JenkinsArtifactStream) artifactStream;
        SettingAttribute settingAttribute = settingsService.get(jenkinsArtifactStream.getSettingId());
        JenkinsConfig jenkinsConfig = (JenkinsConfig) settingAttribute.getValue();

        JenkinsTaskParams jenkinsTaskParams =
            JenkinsTaskParams.builder()
                .jenkinsConfig(jenkinsConfig)
                .encryptedDataDetails(secretManager.getEncryptionDetails(jenkinsConfig, null, null))
                .jobName(jenkinsArtifactStream.getJobname())
                .artifactPaths(jenkinsArtifactStream.getArtifactPaths())
                .metaData(artifactMetadata)
                .build();

        return DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.JENKINS_COLLECTION.name())
                      .parameters(new Object[] {jenkinsTaskParams})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();
      }
      case BAMBOO: {
        BambooArtifactStream bambooArtifactStream = (BambooArtifactStream) artifactStream;
        SettingAttribute settingAttribute = settingsService.get(bambooArtifactStream.getSettingId());
        BambooConfig bambooConfig = (BambooConfig) settingAttribute.getValue();

        return DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.BAMBOO_COLLECTION.name())
                      .parameters(
                          new Object[] {bambooConfig, secretManager.getEncryptionDetails(bambooConfig, null, null),
                              bambooArtifactStream.fetchArtifactStreamAttributes(featureFlagService), artifactMetadata})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();
      }
      case NEXUS: {
        NexusArtifactStream nexusArtifactStream = (NexusArtifactStream) artifactStream;
        SettingAttribute settingAttribute = settingsService.get(nexusArtifactStream.getSettingId());
        NexusConfig nexusConfig = (NexusConfig) settingAttribute.getValue();

        return DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .data(
                TaskData.builder()
                    .async(true)
                    .taskType(TaskType.NEXUS_COLLECTION.name())
                    .parameters(new Object[] {nexusConfig, secretManager.getEncryptionDetails(nexusConfig, null, null),
                        nexusArtifactStream.fetchArtifactStreamAttributes(featureFlagService), artifactMetadata})
                    .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                    .build())
            .build();
      }
      case ARTIFACTORY: {
        ArtifactoryArtifactStream artifactoryArtifactStream = (ArtifactoryArtifactStream) artifactStream;
        SettingAttribute settingAttribute = settingsService.get(artifactoryArtifactStream.getSettingId());
        ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) settingAttribute.getValue();

        final ArtifactoryCollectionTaskParameters artifactoryCollectionTaskParameters =
            ArtifactoryCollectionTaskParameters.builder()
                .artifactoryConfig(artifactoryConfig)
                .encryptedDataDetails(secretManager.getEncryptionDetails(artifactoryConfig, null, null))
                .jobName(artifactoryArtifactStream.getJobname())
                .metadata(artifactMetadata)
                .build();
        return DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.ARTIFACTORY_COLLECTION.name())
                      .parameters(new Object[] {artifactoryCollectionTaskParameters})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();
      }
      case AMAZON_S3: {
        AmazonS3ArtifactStream amazonS3ArtifactStream = (AmazonS3ArtifactStream) artifactStream;
        SettingAttribute settingAttribute = settingsService.get(amazonS3ArtifactStream.getSettingId());
        AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
        final AmazonS3CollectionTaskParameters amazonS3CollectionTaskParameters =
            AmazonS3CollectionTaskParameters.builder()
                .awsConfig(awsConfig)
                .encryptedDataDetails(secretManager.getEncryptionDetails(awsConfig, null, null))
                .jobName(amazonS3ArtifactStream.getJobname())
                .artifactPaths(amazonS3ArtifactStream.getArtifactPaths())
                .build();
        return DelegateTask.builder()
            .accountId(accountId)
            .tags(isNotEmpty(awsConfig.getTag()) ? singletonList(awsConfig.getTag()) : null)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.AMAZON_S3_COLLECTION.name())
                      .parameters(new Object[] {amazonS3CollectionTaskParameters})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();
      }
      case AZURE_ARTIFACTS:
        AzureArtifactsArtifactStream azureArtifactsArtifactStream = (AzureArtifactsArtifactStream) artifactStream;
        SettingAttribute settingAttribute = settingsService.get(azureArtifactsArtifactStream.getSettingId());
        AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) settingAttribute.getValue();

        return DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .data(
                TaskData.builder()
                    .async(true)
                    .taskType(TaskType.AZURE_ARTIFACTS_COLLECTION.name())
                    .parameters(new Object[] {
                        AzureArtifactsCollectionTaskParameters.builder()
                            .accountId(accountId)
                            .azureArtifactsConfig(azureArtifactsConfig)
                            .encryptedDataDetails(secretManager.getEncryptionDetails(azureArtifactsConfig, null, null))
                            .artifactStreamAttributes(
                                azureArtifactsArtifactStream.fetchArtifactStreamAttributes(featureFlagService))
                            .artifactMetadata(artifactMetadata)
                            .build()})
                    .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                    .build())
            .build();
      default:
        throw new UnknownArtifactStreamTypeException(artifactStream.getArtifactStreamType());
    }
  }
}
