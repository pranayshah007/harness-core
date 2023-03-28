/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.manifest;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.polling.ManifestPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.polling.ManifestCollectionTaskParamsNg;
import io.harness.perpetualtask.polling.PollingResponsePublisher;
import io.harness.serializer.KryoSerializer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ManifestPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  private final KryoSerializer kryoSerializer;
  private final ManifestCollectionService manifestCollectionService;
  private final PollingResponsePublisher pollingResponsePublisher;

  private final @Getter Cache<String, ManifestsCollectionCache> cache = Caffeine.newBuilder().build();
  private static final long TIMEOUT_IN_MILLIS = 120L * 1000;

  @Inject
  public ManifestPerpetualTaskExecutorNg(KryoSerializer kryoSerializer,
      ManifestCollectionService manifestCollectionService, PollingResponsePublisher pollingResponsePublisher) {
    this.kryoSerializer = kryoSerializer;
    this.manifestCollectionService = manifestCollectionService;
    this.pollingResponsePublisher = pollingResponsePublisher;
  }

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    ManifestCollectionTaskParamsNg taskParams = getTaskParams(params);
    String pollingDocId = taskParams.getPollingDocId();
    String perpetualTaskId = taskId.getId();
    ManifestsCollectionCache manifestsCollectionCache = cache.get(pollingDocId, id -> new ManifestsCollectionCache());

    Instant startTime = Instant.now();
    if (!manifestsCollectionCache.needsToPublish()) {
      collectManifests(manifestsCollectionCache, taskParams, perpetualTaskId);
    }

    if (manifestsCollectionCache.needsToPublish()) {
      Instant deadline = startTime.plusMillis(TIMEOUT_IN_MILLIS);
      publishFromCache(manifestsCollectionCache, deadline, taskParams, perpetualTaskId);
    }

    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    ManifestCollectionTaskParamsNg taskParams = getTaskParams(params);
    cache.invalidate(taskParams.getPollingDocId());
    ManifestDelegateConfig manifestConfig =
        (ManifestDelegateConfig) kryoSerializer.asObject(taskParams.getManifestCollectionParams().toByteArray());
    manifestCollectionService.cleanup(manifestConfig);
    return true;
  }

  private ManifestCollectionTaskParamsNg getTaskParams(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), ManifestCollectionTaskParamsNg.class);
  }

  private void collectManifests(
      ManifestsCollectionCache manifestsCollectionCache, ManifestCollectionTaskParamsNg taskParams, String taskId) {
    try {
      ManifestDelegateConfig manifestConfig =
          (ManifestDelegateConfig) kryoSerializer.asObject(taskParams.getManifestCollectionParams().toByteArray());
      List<String> chartVersions = manifestCollectionService.collectManifests(manifestConfig);
      if (isEmpty(chartVersions)) {
        log.info("No manifests present for the repository");
        return;
      }
      manifestsCollectionCache.populateCache(chartVersions);
    } catch (Exception e) {
      log.error("Error while collecting manifests ", e);
      pollingResponsePublisher.publishToManger(taskId,
          PollingDelegateResponse.builder()
              .accountId(taskParams.getAccountId())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(e.getMessage())
              .pollingDocId(taskParams.getPollingDocId())
              .build());
    }
  }

  private void publishFromCache(ManifestsCollectionCache manifestsCollectionCache, Instant expiryTime,
      ManifestCollectionTaskParamsNg taskParams, String taskId) {
    if (expiryTime.isBefore(Instant.now())) {
      log.warn("Manifest Collection timed out after {} seconds",
          Instant.now().compareTo(expiryTime.minusMillis(TIMEOUT_IN_MILLIS)));
      return;
    }

    List<String> unpublishedManifests = manifestsCollectionCache.getUnpublishedManifests();
    Set<String> toBeDeletedKeys = manifestsCollectionCache.getToBeDeletedManifestKeys();
    if (isEmpty(unpublishedManifests) && isEmpty(toBeDeletedKeys)) {
      return;
    }

    PollingDelegateResponse response =
        PollingDelegateResponse.builder()
            .accountId(taskParams.getAccountId())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .pollingDocId(taskParams.getPollingDocId())
            .pollingResponseInfc(
                ManifestPollingDelegateResponse.builder()
                    .unpublishedManifests(unpublishedManifests)
                    .toBeDeletedKeys(toBeDeletedKeys)
                    .firstCollectionOnDelegate(
                        manifestsCollectionCache.getFirstCollectionOnDelegate().isFirstCollectionOnDelegate())
                    .build())
            .build();

    if (pollingResponsePublisher.publishToManger(taskId, response)) {
      manifestsCollectionCache.setFirstCollectionOnDelegateFalse();
      manifestsCollectionCache.clearUnpublishedVersions(unpublishedManifests);
      manifestsCollectionCache.removeDeletedArtifactKeys(toBeDeletedKeys);
    }
  }
}
