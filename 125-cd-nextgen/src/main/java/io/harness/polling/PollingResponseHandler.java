/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.polling.contracts.Type.ACR;
import static io.harness.polling.contracts.Type.ARTIFACTORY;
import static io.harness.polling.contracts.Type.DOCKER_HUB;
import static io.harness.polling.contracts.Type.ECR;
import static io.harness.polling.contracts.Type.GCR;
import static io.harness.polling.contracts.Type.GCS_HELM;
import static io.harness.polling.contracts.Type.HTTP_HELM;
import static io.harness.polling.contracts.Type.NEXUS3;
import static io.harness.polling.contracts.Type.S3_HELM;

import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.polling.ArtifactPollingDelegateResponse;
import io.harness.delegate.beans.polling.ManifestPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingResponseInfc;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.polling.artifact.ArtifactCollectionUtilsNg;
import io.harness.polling.bean.PolledResponseResult;
import io.harness.polling.bean.PolledResponseResult.PolledResponseResultBuilder;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.artifact.AcrArtifactInfo;
import io.harness.polling.bean.artifact.ArtifactInfo;
import io.harness.polling.bean.artifact.ArtifactPolledResponse;
import io.harness.polling.bean.artifact.ArtifactoryRegistryArtifactInfo;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.bean.artifact.EcrArtifactInfo;
import io.harness.polling.bean.artifact.GcrArtifactInfo;
import io.harness.polling.bean.artifact.NexusRegistryArtifactInfo;
import io.harness.polling.bean.manifest.HelmChartManifestInfo;
import io.harness.polling.bean.manifest.ManifestInfo;
import io.harness.polling.bean.manifest.ManifestPolledResponse;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.PollingResponse;
import io.harness.polling.service.PolledItemPublisher;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;
import io.harness.polling.service.intfc.PollingService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
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
      pollingService.updatePolledResponse(accountId, pollDocId,
          ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(unpublishedArtifactKeys)).build());
      return;
    }

    // find if there are any new keys which are not in db. This is required because of delegate rebalancing,
    // delegate can loose context of latest keys.
    Set<String> savedArtifactKeys = savedResponse.getAllPolledKeys();
    List<String> newArtifactKeys = unpublishedArtifactKeys.stream()
                                       .filter(artifact -> !savedArtifactKeys.contains(artifact))
                                       .collect(Collectors.toList());

    if (isNotEmpty(newArtifactKeys)) {
      log.info("Publishing artifact versions {} to topic.", newArtifactKeys);
      PolledResponseResult polledResponseResult =
          getPolledResponseResultForArtifact((ArtifactInfo) pollingDocument.getPollingInfo());
      publishPolledItemToTopic(pollingDocument, newArtifactKeys, polledResponseResult);
    }

    // after publishing event, update database as well.
    // if delegate rebalancing happened, unpublishedArtifactKeys are now the new versions. We might have to delete few
    // key from db.
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

  private void publishPolledItemToTopic(
      PollingDocument pollingDocument, List<String> newVersions, PolledResponseResult polledResponseResult) {
    polledItemPublisher.publishPolledItems(
        PollingResponse.newBuilder()
            .setAccountId(pollingDocument.getAccountId())
            .setBuildInfo(
                BuildInfo.newBuilder().setName(polledResponseResult.getName()).addAllVersions(newVersions).build())
            .setType(polledResponseResult.getType())
            .addAllSignatures(pollingDocument.getSignatures())
            .build());
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
      publishPolledItemToTopic(pollingDocument, newVersions, polledResponseResult);
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
      default:
        throw new InvalidRequestException("Unsupported Artifact Type" + artifactInfo.getType().getDisplayName());
    }
    return polledResponseResultBuilder.build();
  }
}
