/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.network.SafeHttpCall.executeWithExceptions;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.artifact.ArtifactsPublishedCache;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.dto.HelmChart;
import software.wings.delegatetasks.helm.ManifestRepoServiceType;
import software.wings.delegatetasks.manifest.ApplicationManifestLogContext;
import software.wings.delegatetasks.manifest.ManifestCollectionExecutionResponse;
import software.wings.delegatetasks.manifest.ManifestCollectionResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Singleton
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ManifestPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final long INTERNAL_TIMEOUT_IN_MS = 120L * 1000;

  private final DelegateAgentManagerClient delegateAgentManagerClient;
  private final KryoSerializer kryoSerializer;
  private final ManifestRepositoryService manifestRepositoryService;
  @Inject @Named("referenceFalseKryoSerializer") private final KryoSerializer referenceFalseKryoSerializer;

  private final @Getter Cache<String, ArtifactsPublishedCache<HelmChart>> cache = Caffeine.newBuilder().build();

  @Inject
  public ManifestPerpetualTaskExecutor(
      @Named(ManifestRepoServiceType.HELM_COMMAND_SERVICE) ManifestRepositoryService manifestRepositoryService,
      DelegateAgentManagerClient delegateAgentManagerClient, KryoSerializer kryoSerializer,
      KryoSerializer referenceFalseKryoSerializer) {
    this.kryoSerializer = kryoSerializer;
    this.manifestRepositoryService = manifestRepositoryService;
    this.delegateAgentManagerClient = delegateAgentManagerClient;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
  }

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    ManifestCollectionTaskParams manifestParams = getTaskParams(params);
    String appManifestId = manifestParams.getAppManifestId();
    log.info("Started manifest collection for appManifestId:{}", appManifestId);
    ManifestCollectionParams manifestCollectionParams =
        (ManifestCollectionParams) referenceFalseKryoSerializer.asObject(
            manifestParams.getManifestCollectionParams().toByteArray());

    ArtifactsPublishedCache<HelmChart> appManifestCache = cache.get(appManifestId,
        id
        -> new ArtifactsPublishedCache<>(manifestCollectionParams.getPublishedVersions(), HelmChart::getVersion, true));

    String perpetualTaskId = taskId.getId();
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ApplicationManifestLogContext(
             appManifestId, manifestCollectionParams.getServiceId(), OVERRIDE_ERROR)) {
      Instant startTime = Instant.now();
      if (!appManifestCache.needsToPublish()) {
        collectManifests(appManifestCache, manifestCollectionParams, perpetualTaskId);
      }

      if (appManifestCache.needsToPublish()) {
        publishFromCache(
            appManifestCache, startTime.plusMillis(INTERNAL_TIMEOUT_IN_MS), manifestCollectionParams, perpetualTaskId);
        log.info("Published manifest successfully");
      }
    } catch (Exception e) {
      log.error("Manifest collection failed with the following error: ", e);
    }

    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  private void publishFromCache(ArtifactsPublishedCache<HelmChart> appManifestCache, Instant expiryTime,
      ManifestCollectionParams params, String taskId) {
    if (expiryTime.isBefore(Instant.now())) {
      log.warn("Manifest Collection timed out after {} seconds",
          Instant.now().compareTo(expiryTime.minusMillis(INTERNAL_TIMEOUT_IN_MS)));
      return;
    }
    ImmutablePair<List<HelmChart>, Boolean> unpublishedDetails = appManifestCache.getLimitedUnpublishedBuildDetails();
    List<HelmChart> unpublishedVersions = unpublishedDetails.getLeft()
                                              .stream()
                                              .sorted(Comparator.comparing(HelmChart::getVersion).reversed())
                                              .collect(Collectors.toList());
    Set<String> toBeDeletedVersions = appManifestCache.getToBeDeletedArtifactKeys();
    if (isEmpty(toBeDeletedVersions) && isEmpty(unpublishedVersions)) {
      log.info("No new manifest versions added or deleted to publish");
      return;
    }
    ManifestCollectionExecutionResponse response =
        ManifestCollectionExecutionResponse.builder()
            .appManifestId(params.getAppManifestId())
            .appId(params.getAppId())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .manifestCollectionResponse(ManifestCollectionResponse.builder()
                                            .helmCharts(unpublishedVersions)
                                            .toBeDeletedKeys(toBeDeletedVersions)
                                            .stable(!unpublishedDetails.getRight())
                                            .build())
            .build();

    if (publishToManager(params.getAccountId(), taskId, response)) {
      appManifestCache.removeDeletedArtifactKeys(toBeDeletedVersions);
      appManifestCache.addPublishedBuildDetails(unpublishedVersions);
      publishFromCache(appManifestCache, expiryTime, params, taskId);
      log.info("Published {} manifest versions to manager",
          unpublishedVersions.stream().map(HelmChart::getVersion).collect(Collectors.joining(",")));
    }
  }

  private boolean publishToManager(String accountId, String taskId, ManifestCollectionExecutionResponse response) {
    try {
      byte[] responseSerialized = referenceFalseKryoSerializer.asBytes(response);

      executeWithExceptions(delegateAgentManagerClient.publishManifestCollectionResultV2(
          taskId, accountId, RequestBody.create(MediaType.parse("application/octet-stream"), responseSerialized)));
      return true;
    } catch (Exception ex) {
      log.error("Failed to publish build source execution response with status: {}",
          response.getCommandExecutionStatus().name(), ex);
      return false;
    }
  }

  private void collectManifests(ArtifactsPublishedCache<HelmChart> appManifestCache, ManifestCollectionParams params,
      String taskId) throws Exception {
    try {
      List<HelmChart> collectedManifests = manifestRepositoryService.collectManifests(params);
      if (isEmpty(collectedManifests)) {
        log.info("No manifests present for the repository");
        return;
      }

      log.info("Collected {} manifest versions from repository", collectedManifests.size());
      appManifestCache.addCollectionResult(collectedManifests);
    } catch (Exception e) {
      publishToManager(params.getAccountId(), taskId,
          ManifestCollectionExecutionResponse.builder()
              .appManifestId(params.getAppManifestId())
              .appId(params.getAppId())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    ManifestCollectionTaskParams manifestParams = getTaskParams(params);
    cache.invalidate(manifestParams.getAppManifestId());
    ManifestCollectionParams manifestCollectionParams =
        (ManifestCollectionParams) referenceFalseKryoSerializer.asObject(
            manifestParams.getManifestCollectionParams().toByteArray());
    try {
      manifestRepositoryService.cleanup(manifestCollectionParams);
      log.info("Cleanup completed successfully for perpetual task: {}, app manifest: {}", taskId.getId(),
          manifestParams.getAppManifestId());
    } catch (Exception e) {
      log.warn("Error in cleaning up after manifest collection", e);
    }
    return false;
  }

  private ManifestCollectionTaskParams getTaskParams(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), ManifestCollectionTaskParams.class);
  }
}
