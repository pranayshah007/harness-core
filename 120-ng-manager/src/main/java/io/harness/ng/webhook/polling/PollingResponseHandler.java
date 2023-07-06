/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.polling;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.polling.contracts.Type.ACR;
import static io.harness.polling.contracts.Type.AMAZON_S3;
import static io.harness.polling.contracts.Type.AMI;
import static io.harness.polling.contracts.Type.ARTIFACTORY;
import static io.harness.polling.contracts.Type.AZURE_ARTIFACTS;
import static io.harness.polling.contracts.Type.BAMBOO;
import static io.harness.polling.contracts.Type.CUSTOM_ARTIFACT;
import static io.harness.polling.contracts.Type.DOCKER_HUB;
import static io.harness.polling.contracts.Type.ECR;
import static io.harness.polling.contracts.Type.GCR;
import static io.harness.polling.contracts.Type.GCS_HELM;
import static io.harness.polling.contracts.Type.GITHUB_PACKAGES;
import static io.harness.polling.contracts.Type.GOOGLE_ARTIFACT_REGISTRY;
import static io.harness.polling.contracts.Type.GOOGLE_CLOUD_STORAGE_ARTIFACT;
import static io.harness.polling.contracts.Type.HTTP_HELM;
import static io.harness.polling.contracts.Type.JENKINS;
import static io.harness.polling.contracts.Type.NEXUS2;
import static io.harness.polling.contracts.Type.NEXUS3;
import static io.harness.polling.contracts.Type.S3_HELM;

import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.polling.ArtifactPollingDelegateResponse;
import io.harness.delegate.beans.polling.GitPollingDelegateResponse;
import io.harness.delegate.beans.polling.ManifestPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingResponseInfc;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.polling.artifact.ArtifactCollectionUtilsNg;
import io.harness.polling.bean.ArtifactInfo;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.GitPollingPolledResponse;
import io.harness.polling.bean.HelmChartManifestInfo;
import io.harness.polling.bean.ManifestInfo;
import io.harness.polling.bean.ManifestPolledResponse;
import io.harness.polling.bean.PolledResponseResult;
import io.harness.polling.bean.PolledResponseResult.PolledResponseResultBuilder;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingType;
import io.harness.polling.bean.artifact.AMIArtifactInfo;
import io.harness.polling.bean.artifact.AcrArtifactInfo;
import io.harness.polling.bean.artifact.ArtifactoryRegistryArtifactInfo;
import io.harness.polling.bean.artifact.AzureArtifactsInfo;
import io.harness.polling.bean.artifact.BambooArtifactInfo;
import io.harness.polling.bean.artifact.CustomArtifactInfo;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.bean.artifact.EcrArtifactInfo;
import io.harness.polling.bean.artifact.GARArtifactInfo;
import io.harness.polling.bean.artifact.GcrArtifactInfo;
import io.harness.polling.bean.artifact.GithubPackagesArtifactInfo;
import io.harness.polling.bean.artifact.GoogleCloudStorageArtifactInfo;
import io.harness.polling.bean.artifact.JenkinsArtifactInfo;
import io.harness.polling.bean.artifact.Nexus2RegistryArtifactInfo;
import io.harness.polling.bean.artifact.NexusRegistryArtifactInfo;
import io.harness.polling.bean.artifact.S3ArtifactInfo;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;
import io.harness.polling.service.intfc.PollingService;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PollingResponseHandler {
  private static final int MAX_FAILED_ATTEMPTS = 3500;
  private PollingService pollingService;
  private PollingPerpetualTaskService pollingPerpetualTaskService;
  private PolledItemPublisher polledItemPublisher;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private PersistentLocker persistentLocker;

  @Inject
  public PollingResponseHandler(PollingService pollingService, PollingPerpetualTaskService pollingPerpetualTaskService,
      PolledItemPublisher polledItemPublisher) {
    this.pollingService = pollingService;
    this.pollingPerpetualTaskService = pollingPerpetualTaskService;
    this.polledItemPublisher = polledItemPublisher;
  }

  public void handlePollingResponse(
      @NotEmpty String perpetualTaskId, @NotEmpty String accountId, PollingDelegateResponse executionResponse) {
    String pollDocId = executionResponse.getPollingDocId();
    PollingDocument pollingDocument = pollingService.get(accountId, pollDocId);
    if (pollingDocument == null || !perpetualTaskId.equals(pollingDocument.getPerpetualTaskId())) {
      pollingPerpetualTaskService.deletePerpetualTask(perpetualTaskId, accountId);
      return;
    }
    if (EmptyPredicate.isEmpty(pollingDocument.getSignatures())) {
      pollingService.delete(pollingDocument);
      return;
    }
    try (AutoLogContext ignore1 = new NgAutoLogContext(
             pollingDocument.getProjectIdentifier(), pollingDocument.getOrgIdentifier(), accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new NgPollingAutoLogContext(pollDocId, OVERRIDE_ERROR);) {
      log.info("Got a polling response {} for perpetual task id {}", executionResponse.getCommandExecutionStatus(),
          perpetualTaskId);
      if (executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
        handleSuccessResponse(pollingDocument, executionResponse.getPollingResponseInfc());
      } else {
        handleFailureResponse(pollingDocument);
      }
    }
  }

  private void handleSuccessResponse(PollingDocument pollingDocument, PollingResponseInfc pollingResponseInfc) {
    String accountId = pollingDocument.getAccountId();
    String pollDocId = pollingDocument.getUuid();

    if (pollingDocument.getFailedAttempts() > 0) {
      pollingService.updateFailedAttempts(accountId, pollDocId, 0);
    }

    switch (pollingDocument.getPollingType()) {
      case MANIFEST:
        handleManifestResponse(pollingDocument, pollingResponseInfc);
        break;
      case ARTIFACT:
        handleArtifactResponse(pollingDocument, pollingResponseInfc);
        break;
      case WEBHOOK_POLLING:
        handleGitPollingResponse(pollingDocument, pollingResponseInfc);
        break;
      default:
        throw new InvalidRequestException(
            "Not implemented yet for " + pollingDocument.getPollingType() + " polling type");
    }
  }

  private void handleArtifactResponse(PollingDocument pollingDocument, PollingResponseInfc pollingResponseInfc) {
    ArtifactPollingDelegateResponse response = (ArtifactPollingDelegateResponse) pollingResponseInfc;
    ArtifactPolledResponse savedResponse = (ArtifactPolledResponse) pollingDocument.getPolledResponse();
    String accountId = pollingDocument.getAccountId();
    String pollDocId = pollingDocument.getUuid();
    List<ArtifactDelegateResponse> unpublishedArtifacts = response.getUnpublishedArtifacts();
    List<String> unpublishedArtifactKeys =
        unpublishedArtifacts.stream().map(ArtifactCollectionUtilsNg::getArtifactKey).collect(Collectors.toList());

    // If polled response is null, it means it was first time collecting output from perpetual task
    // There is no need to publish collected new keys in this case.
    if (savedResponse == null) {
      log.info("This is a first time collecting output for artifacts from perpetual task {} and accountId {}. "
              + "Polled keys are not published",
          pollingDocument.getPerpetualTaskId(), accountId);
      pollingService.updatePolledResponse(accountId, pollDocId,
          ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(unpublishedArtifactKeys)).build());
      return;
    }

    // find if there are any new keys which are not in db. This is required because of delegate rebalancing,
    // delegate can loose context of latest keys.
    Set<String> savedArtifactKeys = savedResponse.getAllPolledKeys();
    List<String> newArtifactKeys = new ArrayList<>();
    Map<String, Metadata> newArtifactsMetadataMap = new HashMap<>();
    for (ArtifactDelegateResponse artifactDelegateResponse : unpublishedArtifacts) {
      String key = ArtifactCollectionUtilsNg.getArtifactKey(artifactDelegateResponse);
      if (!savedArtifactKeys.contains(key)) {
        newArtifactKeys.add(key);
        Map<String, String> metadata = new HashMap<>();
        if (artifactDelegateResponse.getBuildDetails() != null
            && artifactDelegateResponse.getBuildDetails().getMetadata() != null) {
          metadata = artifactDelegateResponse.getBuildDetails().getMetadata();
          metadata.values().removeAll(Collections.singleton(null));
        }
        newArtifactsMetadataMap.put(key, Metadata.newBuilder().putAllMetadata(metadata).build());
      }
    }

    PolledResponseResult polledResponseResult =
        getPolledResponseResultForArtifact((ArtifactInfo) pollingDocument.getPollingInfo());
    List<String> signaturesToPublishWithoutLock = getSignaturesWithNoLock(pollingDocument);
    if (isNotEmpty(newArtifactKeys) && isNotEmpty(signaturesToPublishWithoutLock)) {
      log.info("Publishing artifact versions {} for unlocked signatures {} to topic.", newArtifactKeys,
          signaturesToPublishWithoutLock);
      List<Metadata> newArtifactsMetadata =
          newArtifactKeys.stream().map(newArtifactsMetadataMap::get).collect(Collectors.toList());
      publishPolledItemToTopic(
          pollingDocument, newArtifactKeys, polledResponseResult, newArtifactsMetadata, signaturesToPublishWithoutLock);
    }
    handleLockedSignaturesAndUpdatePollingDoc(response, savedArtifactKeys, unpublishedArtifactKeys, pollingDocument,
        newArtifactKeys, polledResponseResult, newArtifactsMetadataMap);
  }

  private void publishPolledItemToTopic(PollingDocument pollingDocument, List<String> newVersions,
      PolledResponseResult polledResponseResult, List<Metadata> newArtifactsMetadata, List<String> signatures) {
    if (ngFeatureFlagHelperService.isEnabled(
            pollingDocument.getAccountId(), FeatureName.SPG_TRIGGER_FOR_ALL_ARTIFACTS_NG)) {
      // This ff is added needed in a use case where in customer wanted their pipeline to be triggered via trigger for
      // all the new pushed artifacts and manifests that were collected by the perpetual task in a single execution.
      // Hence we are sending a polling response for all the artifact or manifest version to the pipeline service.
      for (int i = 0; i < newVersions.size(); i++) {
        List<Metadata> metaDataList = pollingDocument.getPollingType() == PollingType.ARTIFACT
            ? Collections.singletonList(newArtifactsMetadata.get(i))
            : Collections.emptyList();
        polledItemPublisher.publishPolledItems(
            PollingResponse.newBuilder()
                .setAccountId(pollingDocument.getAccountId())
                .setPollingDocId(pollingDocument.getUuid())
                .setBuildInfo(BuildInfo.newBuilder()
                                  .setName(polledResponseResult.getName())
                                  .addAllMetadata(metaDataList)
                                  .addAllVersions(Collections.singletonList(newVersions.get(i)))
                                  .build())
                .setType(polledResponseResult.getType())
                .addAllSignatures(signatures)
                .build());
      }
    } else {
      polledItemPublisher.publishPolledItems(PollingResponse.newBuilder()
                                                 .setAccountId(pollingDocument.getAccountId())
                                                 .setPollingDocId(pollingDocument.getUuid())
                                                 .setBuildInfo(BuildInfo.newBuilder()
                                                                   .setName(polledResponseResult.getName())
                                                                   .addAllMetadata(newArtifactsMetadata)
                                                                   .addAllVersions(newVersions)
                                                                   .build())
                                                 .setType(polledResponseResult.getType())
                                                 .addAllSignatures(signatures)
                                                 .build());
    }
  }

  private void handleGitPollingResponse(PollingDocument pollingDocument, PollingResponseInfc pollingResponseInfc) {
    GitPollingDelegateResponse response = (GitPollingDelegateResponse) pollingResponseInfc;
    GitPollingPolledResponse savedResponse = (GitPollingPolledResponse) pollingDocument.getPolledResponse();
    String accountId = pollingDocument.getAccountId();
    String pollDocId = pollingDocument.getUuid();
    List<GitPollingWebhookData> gitPollingWebhookEventDeliveries = response.getUnpublishedEvents();
    List<String> unpublishedWebhookDeliveryIds = gitPollingWebhookEventDeliveries.stream()
                                                     .map(GitPollingWebhookData::getDeliveryId)
                                                     .collect(Collectors.toList());

    // If polled response is null, it means it was first time collecting output from perpetual task
    // There is no need to publish collected new keys in this case.
    if (savedResponse == null) {
      log.info("This is a first time collecting output for gitpolling from perpetual task {} and accountId {}. "
              + "Polled keys are not published",
          pollingDocument.getPerpetualTaskId(), accountId);

      pollingService.updatePolledResponse(accountId, pollDocId,
          GitPollingPolledResponse.builder().allPolledKeys(new HashSet<>(unpublishedWebhookDeliveryIds)).build());
      return;
    }

    // find if there are any new keys which are not in db. This is required because of delegate rebalancing,
    // delegate can lose context of latest keys.
    Set<String> savedWebHookDeliveryIds = savedResponse.getAllPolledKeys();
    List<String> newWebhookDeliveryIds = unpublishedWebhookDeliveryIds.stream()
                                             .filter(deliveryId -> !savedWebHookDeliveryIds.contains(deliveryId))
                                             .collect(Collectors.toList());

    if (isNotEmpty(newWebhookDeliveryIds)) {
      List<GitPollingWebhookData> result = gitPollingWebhookEventDeliveries.stream()
                                               .filter(item -> newWebhookDeliveryIds.contains(item.getDeliveryId()))
                                               .collect(Collectors.toList());

      publishPolledItemToWebhook(accountId, result);
      log.info("Published the webhook redelivery event to trigger for account {} ", accountId);
    }

    // after publishing event, update database as well.
    // if delegate rebalancing happened, unpublishedArtifactKeys are now the new versions. We might have to delete few
    // key from db.
    Set<String> toBeDeletedKeys = response.getToBeDeletedIds();
    Set<String> unpublishedGitPollingKeySet = new HashSet<>(unpublishedWebhookDeliveryIds);
    if (response.isFirstCollectionOnDelegate() && isEmpty(toBeDeletedKeys)) {
      toBeDeletedKeys = savedWebHookDeliveryIds.stream()
                            .filter(savedArtifactKey -> !unpublishedGitPollingKeySet.contains(savedArtifactKey))
                            .collect(Collectors.toSet());
    }
    savedWebHookDeliveryIds.removeAll(toBeDeletedKeys);
    savedWebHookDeliveryIds.addAll(unpublishedGitPollingKeySet);
    GitPollingPolledResponse pollingPolledResponse =
        GitPollingPolledResponse.builder().allPolledKeys(savedWebHookDeliveryIds).build();
    pollingService.updatePolledResponse(accountId, pollDocId, pollingPolledResponse);
  }

  private void publishPolledItemToWebhook(String accountId, List<GitPollingWebhookData> redeliveries) {
    polledItemPublisher.sendWebhookRequest(accountId, redeliveries);
  }

  private void handleFailureResponse(PollingDocument pollingDocument) {
    int failedCount = pollingDocument.getFailedAttempts() + 1;

    if (failedCount % 25 == 0 && failedCount != MAX_FAILED_ATTEMPTS) {
      pollingPerpetualTaskService.resetPerpetualTask(pollingDocument);
    }

    pollingService.updateFailedAttempts(pollingDocument.getAccountId(), pollingDocument.getUuid(), failedCount);

    if (failedCount == MAX_FAILED_ATTEMPTS) {
      pollingPerpetualTaskService.deletePerpetualTask(
          pollingDocument.getPerpetualTaskId(), pollingDocument.getAccountId());
    }
  }

  private void handleManifestResponse(PollingDocument pollingDocument, PollingResponseInfc pollingResponseInfc) {
    ManifestPollingDelegateResponse response = (ManifestPollingDelegateResponse) pollingResponseInfc;
    ManifestPolledResponse savedResponse = (ManifestPolledResponse) pollingDocument.getPolledResponse();
    String accountId = pollingDocument.getAccountId();
    String pollDocId = pollingDocument.getUuid();
    List<String> unpublishedManifests = response.getUnpublishedManifests();

    // If polled response is null, it means it was first time collecting output from perpetual task
    // There is no need to publish collected new versions in this case.
    if (savedResponse == null) {
      log.info("This is a first time collecting output for manifests from perpetual task {} and accountId {}. "
              + "Polled keys are not published",
          pollingDocument.getPerpetualTaskId(), accountId);
      pollingService.updatePolledResponse(accountId, pollDocId,
          ManifestPolledResponse.builder().allPolledKeys(new HashSet<>(unpublishedManifests)).build());
      return;
    }

    // find if there are any new versions which are not in db. This is required because of delegate rebalancing,
    // delegate can loose context of latest versions.
    Set<String> savedManifests = savedResponse.getAllPolledKeys();
    List<String> newVersions = unpublishedManifests.stream()
                                   .filter(manifest -> !savedManifests.contains(manifest))
                                   .collect(Collectors.toList());

    if (isNotEmpty(newVersions)) {
      log.info("Publishing manifest versions {} to topic.", newVersions);
      PolledResponseResult polledResponseResult =
          getPolledResponseResultForManifest((ManifestInfo) pollingDocument.getPollingInfo());
      publishPolledItemToTopic(
          pollingDocument, newVersions, polledResponseResult, new ArrayList<>(), pollingDocument.getSignatures());
    }

    // after publishing event, update database as well.
    // if delegate rebalancing happened, unpublishedManifests are now the new versions. We might have to delete few keys
    // from db.
    Set<String> toBeDeletedKeys = response.getToBeDeletedKeys();
    Set<String> unpublishedManifestSet = new HashSet<>(unpublishedManifests);
    if (response.isFirstCollectionOnDelegate() && isEmpty(toBeDeletedKeys)) {
      toBeDeletedKeys = savedManifests.stream()
                            .filter(savedManifest -> !unpublishedManifestSet.contains(savedManifest))
                            .collect(Collectors.toSet());
    }
    savedManifests.removeAll(toBeDeletedKeys);
    savedManifests.addAll(unpublishedManifestSet);
    ManifestPolledResponse manifestPolledResponse =
        ManifestPolledResponse.builder().allPolledKeys(savedManifests).build();
    pollingService.updatePolledResponse(accountId, pollDocId, manifestPolledResponse);
  }

  private PolledResponseResult getPolledResponseResultForManifest(ManifestInfo manifestInfo) {
    PolledResponseResult polledResponseResult = PolledResponseResult.builder().build();
    if (manifestInfo.getType().equals(ManifestType.HelmChart)) {
      polledResponseResult.setName(((HelmChartManifestInfo) manifestInfo).getChartName());
      if (manifestInfo.getStore() instanceof HttpStoreConfig) {
        polledResponseResult.setType(HTTP_HELM);
      } else if (manifestInfo.getStore() instanceof S3StoreConfig) {
        polledResponseResult.setType(S3_HELM);
      } else if (manifestInfo.getStore() instanceof GcsStoreConfig) {
        polledResponseResult.setType(GCS_HELM);
      } else {
        throw new InvalidRequestException(String.format("Unsupported Manifest Type {} or manifest store {}",
            manifestInfo.getType(), manifestInfo.getStore().toString()));
      }
    }
    return polledResponseResult;
  }

  private PolledResponseResult getPolledResponseResultForArtifact(ArtifactInfo artifactInfo) {
    PolledResponseResultBuilder polledResponseResultBuilder = PolledResponseResult.builder();
    switch (artifactInfo.getType()) {
      case DOCKER_REGISTRY:
        polledResponseResultBuilder.name(((DockerHubArtifactInfo) artifactInfo).getImagePath());
        polledResponseResultBuilder.type(DOCKER_HUB);
        break;
      case GCR:
        polledResponseResultBuilder.name(((GcrArtifactInfo) artifactInfo).getImagePath());
        polledResponseResultBuilder.type(GCR);
        break;
      case ECR:
        polledResponseResultBuilder.name(((EcrArtifactInfo) artifactInfo).getImagePath());
        polledResponseResultBuilder.type(ECR);
        break;
      case NEXUS3_REGISTRY:
        polledResponseResultBuilder.name(((NexusRegistryArtifactInfo) artifactInfo).getArtifactPath());
        polledResponseResultBuilder.type(NEXUS3);
        break;
      case NEXUS2_REGISTRY:
        polledResponseResultBuilder.name(((Nexus2RegistryArtifactInfo) artifactInfo).getRepositoryName());
        polledResponseResultBuilder.type(NEXUS2);
        break;
      case ARTIFACTORY_REGISTRY:
        if (EmptyPredicate.isNotEmpty(((ArtifactoryRegistryArtifactInfo) artifactInfo).getRepositoryFormat())
            && ((ArtifactoryRegistryArtifactInfo) artifactInfo).getRepositoryFormat().equals("generic")) {
          polledResponseResultBuilder.name(((ArtifactoryRegistryArtifactInfo) artifactInfo).getArtifactDirectory());
        } else {
          polledResponseResultBuilder.name(((ArtifactoryRegistryArtifactInfo) artifactInfo).getArtifactPath());
        }
        polledResponseResultBuilder.type(ARTIFACTORY);
        break;
      case ACR:
        polledResponseResultBuilder.name(((AcrArtifactInfo) artifactInfo).getRepository());
        polledResponseResultBuilder.type(ACR);
        break;
      case AMAZONS3:
        polledResponseResultBuilder.name(((S3ArtifactInfo) artifactInfo).getBucketName());
        polledResponseResultBuilder.type(AMAZON_S3);
        break;
      case JENKINS:
        polledResponseResultBuilder.name(((JenkinsArtifactInfo) artifactInfo).getJobName());
        polledResponseResultBuilder.type(JENKINS);
        break;
      case CUSTOM_ARTIFACT:
        polledResponseResultBuilder.name(((CustomArtifactInfo) artifactInfo).getArtifactsArrayPath());
        polledResponseResultBuilder.type(CUSTOM_ARTIFACT);
        break;
      case GOOGLE_ARTIFACT_REGISTRY:
        polledResponseResultBuilder.name(((GARArtifactInfo) artifactInfo).getPkg());
        polledResponseResultBuilder.type(GOOGLE_ARTIFACT_REGISTRY);
        break;
      case GITHUB_PACKAGES:
        polledResponseResultBuilder.name(((GithubPackagesArtifactInfo) artifactInfo).getPackageName());
        polledResponseResultBuilder.type(GITHUB_PACKAGES);
        break;
      case AZURE_ARTIFACTS:
        polledResponseResultBuilder.name(((AzureArtifactsInfo) artifactInfo).getPackageName());
        polledResponseResultBuilder.type(AZURE_ARTIFACTS);
        break;
      case GOOGLE_CLOUD_STORAGE_ARTIFACT:
        polledResponseResultBuilder.name(((GoogleCloudStorageArtifactInfo) artifactInfo).getArtifactPath());
        polledResponseResultBuilder.type(GOOGLE_CLOUD_STORAGE_ARTIFACT);
        break;
      case AMI:
        polledResponseResultBuilder.name(((AMIArtifactInfo) artifactInfo).getVersion());
        polledResponseResultBuilder.type(AMI);
        break;
      case BAMBOO:
        polledResponseResultBuilder.name(((BambooArtifactInfo) artifactInfo).getPlanKey());
        polledResponseResultBuilder.type(BAMBOO);
        break;
      default:
        throw new InvalidRequestException("Unsupported Artifact Type " + artifactInfo.getType().getDisplayName());
    }
    return polledResponseResultBuilder.build();
  }

  private List<String> getSignaturesWithNoLock(PollingDocument pollingDocument) {
    // Returns signatures for triggers which are NOT of type MultiRegionArtifact.
    List<String> signatures = pollingDocument.getSignatures();
    Map<String, List<String>> signaturesLock = pollingDocument.getSignaturesLock();
    if (isEmpty(signaturesLock) || isEmpty(signatures)) {
      return signatures;
    }
    return signatures.stream().filter(signature -> !signaturesLock.containsKey(signature)).collect(Collectors.toList());
  }

  public List<String> getSignaturesWithLock(List<String> signatures, Map<String, List<String>> signaturesLock) {
    // Returns signatures for triggers of type MultiRegionArtifact, which require locking logic to be carried out.
    List<String> signaturesWithLock;
    if (isEmpty(signaturesLock) || isEmpty(signatures)) {
      signaturesWithLock = null;
    } else {
      signaturesWithLock = signatures.stream().filter(signaturesLock::containsKey).collect(Collectors.toList());
    }
    return signaturesWithLock;
  }

  private List<String> getAllOtherPollingDocIdsToLock(
      String accountId, Map<String, List<String>> signaturesLock, List<String> signaturesWithLock) {
    List<String> allSignaturesForLock = new ArrayList<>();
    for (String signature : signaturesWithLock) {
      List<String> signaturesForLock = signaturesLock.get(signature);
      if (isNotEmpty(signaturesForLock)) {
        allSignaturesForLock.addAll(signaturesForLock);
      }
    }
    return pollingService.getUuidsBySignatures(accountId, allSignaturesForLock);
  }

  private void filterAndPublishArtifactsForLockedSignatures(PollingDocument pollingDocument,
      List<String> newArtifactKeys, PolledResponseResult polledResponseResult,
      Map<String, Metadata> newArtifactsMetadataMap, Map<String, List<String>> signaturesLock,
      List<String> signaturesWithLock, List<String> allOtherPollingDocIdsToLock) {
    String accountId = pollingDocument.getAccountId();
    List<PollingDocument> allLockedPollingDocuments = pollingService.getMany(accountId, allOtherPollingDocIdsToLock);
    for (String signature : signaturesWithLock) {
      List<PollingDocument> pollingDocumentsToCheck =
          allLockedPollingDocuments.stream()
              .filter(pollingDoc
                  -> signaturesLock.get(signature).stream().anyMatch(sig -> pollingDoc.getSignatures().contains(sig)))
              .collect(Collectors.toList());
      List<String> filteredNewArtifactKeys =
          newArtifactKeys.stream()
              .filter(key -> pollingDocumentsToCheck.stream().allMatch(pollingDoc -> {
                if (pollingDoc.getPolledResponse() == null) {
                  return false;
                }
                ArtifactPolledResponse polledResponse = (ArtifactPolledResponse) pollingDoc.getPolledResponse();
                if (polledResponse.getAllPolledKeys() == null) {
                  return false;
                }
                return polledResponse.getAllPolledKeys().contains(key);
              }))
              .collect(Collectors.toList());
      List<Metadata> filteredNewArtifactsMetadata =
          filteredNewArtifactKeys.stream().map(newArtifactsMetadataMap::get).collect(Collectors.toList());
      if (isNotEmpty(filteredNewArtifactKeys)) {
        log.info(
            "Publishing artifact versions {} for locked signature {} to topic", filteredNewArtifactKeys, signature);
        publishPolledItemToTopic(pollingDocument, filteredNewArtifactKeys, polledResponseResult,
            filteredNewArtifactsMetadata, Collections.singletonList(signature));
      }
    }
  }

  private void updateArtifactPolledResponse(ArtifactPollingDelegateResponse response, Set<String> savedArtifactKeys,
      List<String> unpublishedArtifactKeys, String accountId, String pollDocId) {
    Set<String> toBeDeletedKeys = response.getToBeDeletedKeys();
    Set<String> unpublishedArtifactKeySet = new HashSet<>(unpublishedArtifactKeys);
    if (response.isFirstCollectionOnDelegate() && isEmpty(toBeDeletedKeys)) {
      toBeDeletedKeys = savedArtifactKeys.stream()
                            .filter(savedArtifactKey -> !unpublishedArtifactKeySet.contains(savedArtifactKey))
                            .collect(Collectors.toSet());
    }
    savedArtifactKeys.removeAll(toBeDeletedKeys);
    savedArtifactKeys.addAll(unpublishedArtifactKeySet);
    ArtifactPolledResponse artifactPolledResponse =
        ArtifactPolledResponse.builder().allPolledKeys(savedArtifactKeys).build();
    pollingService.updatePolledResponse(accountId, pollDocId, artifactPolledResponse);
  }

  private void handleLockedSignaturesAndUpdatePollingDoc(ArtifactPollingDelegateResponse response,
      Set<String> savedArtifactKeys, List<String> unpublishedArtifactKeys, PollingDocument pollingDocument,
      List<String> newArtifactKeys, PolledResponseResult polledResponseResult,
      Map<String, Metadata> newArtifactsMetadataMap) {
    String accountId = pollingDocument.getAccountId();
    String pollDocId = pollingDocument.getUuid();
    List<String> signatures = pollingDocument.getSignatures();
    Map<String, List<String>> signaturesLock = pollingDocument.getSignaturesLock();
    // Get signatures of MultiRegionArtifact triggers.
    List<String> signaturesWithLock = getSignaturesWithLock(signatures, signaturesLock);
    List<AcquiredLock<?>> acquiredLocks = new ArrayList<>();
    boolean shouldCheckLockedSignatures = isNotEmpty(newArtifactKeys) && isNotEmpty(signaturesWithLock)
        && ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS);
    if (shouldCheckLockedSignatures) {
      // Check pollingDocuments mapping to signatures in signaturesLock if the collected tags are present
      // and publish these tags accordingly.
      List<String> allOtherPollingDocIdsToLock =
          getAllOtherPollingDocIdsToLock(accountId, signaturesLock, signaturesWithLock);
      // The current PollingDocId should be locked as well.
      List<String> allPollingDocIdsToLock = new ArrayList<>(allOtherPollingDocIdsToLock);
      allPollingDocIdsToLock.add(pollDocId);
      log.info("Acquiring multi-region artifact locks for accountId {} and pollingDocIds {}", accountId,
          allPollingDocIdsToLock);
      boolean locksAcquiredSuccess = false;
      try {
        for (String pollingDocIdToLock : allPollingDocIdsToLock) {
          acquiredLocks.add(
              persistentLocker.waitToAcquireLock(pollingDocIdToLock, Duration.ofMinutes(1), Duration.ofSeconds(10)));
        }
        locksAcquiredSuccess = true;
      } catch (Exception e) {
        log.error(
            "Failed to acquire locks for multi-region artifacts in accountId {}, pollingDocId {} and tags {}. The pollingDoc versions will be updated but events will not be published",
            accountId, pollDocId, unpublishedArtifactKeys, e);
      }
      if (locksAcquiredSuccess) {
        try {
          filterAndPublishArtifactsForLockedSignatures(pollingDocument, newArtifactKeys, polledResponseResult,
              newArtifactsMetadataMap, signaturesLock, signaturesWithLock, allOtherPollingDocIdsToLock);
        } catch (Exception e) {
          log.error(
              "Failed to publish artifact for locked signatures in accountId {}, pollingDocId {}, locked signatures {} and tags {}",
              accountId, pollDocId, signaturesLock, unpublishedArtifactKeys, e);
        }
      }
    }
    // After publishing event, update database as well.
    // if delegate rebalancing happened, unpublishedArtifactKeys are now the new versions. We might have to delete few
    // key from db.
    try {
      updateArtifactPolledResponse(response, savedArtifactKeys, unpublishedArtifactKeys, accountId, pollDocId);
    } catch (Exception e) {
      log.error("Failed to update pollingDocument with new tags in accountId {}, pollingDocId {} and tags {}",
          accountId, pollDocId, unpublishedArtifactKeys, e);
    } finally {
      if (shouldCheckLockedSignatures) {
        log.info("Releasing all acquired locks for accountId {} and pollingDocId {}", accountId, pollDocId);
        for (AcquiredLock<?> acquiredLock : acquiredLocks) {
          acquiredLock.release();
        }
      }
    }
  }
}
