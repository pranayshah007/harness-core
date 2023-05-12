/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.artifact;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.executeWithExceptions;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.DelegateArtifactCollectionUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactPerpetualTaskExecutor implements PerpetualTaskExecutor {
  // Deadline until we try to publish parts of build details to the manager. This should be considerably less than than
  // the task timeout. Right now, timeout is 2 minutes, and this value is 1.5 minutes.
  private static final long INTERNAL_TIMEOUT_IN_MS = 90L * 1000;

  private final ArtifactRepositoryServiceImpl artifactRepositoryService;
  private final DelegateAgentManagerClient delegateAgentManagerClient;
  private final KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  private final Cache<String, ArtifactsPublishedCache<BuildDetails>> cache = Caffeine.newBuilder().build();

  @Inject
  public ArtifactPerpetualTaskExecutor(ArtifactRepositoryServiceImpl artifactRepositoryService,
      DelegateAgentManagerClient delegateAgentManagerClient, KryoSerializer kryoSerializer,
      KryoSerializer referenceFalseKryoSerializer) {
    this.artifactRepositoryService = artifactRepositoryService;
    this.kryoSerializer = kryoSerializer;
    this.delegateAgentManagerClient = delegateAgentManagerClient;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
  }

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("In ArtifactPerpetualTask artifact collection");
    ArtifactCollectionTaskParams artifactCollectionTaskParams = getTaskParams(params);

    String artifactStreamId = artifactCollectionTaskParams.getArtifactStreamId();
    log.info("Running artifact collection for artifactStreamId: {}", artifactStreamId);

    final BuildSourceParameters buildSourceParameters = (BuildSourceParameters) referenceFalseKryoSerializer.asObject(
        artifactCollectionTaskParams.getBuildSourceParams().toByteArray());
    String accountId = buildSourceParameters.getAccountId();

    // Fetch artifacts published cache for this artifactStreamId.
    ArtifactsPublishedCache<BuildDetails> currCache =
        getArtifactsPublishedCached(artifactStreamId, buildSourceParameters);
    Instant startTime = Instant.now();
    if (!currCache.needsToPublish()) {
      collectArtifacts(accountId, artifactStreamId, taskId, buildSourceParameters, currCache);
    }

    // This condition is true if we initially had unpublished artifacts, or if artifact collection happened and we got
    // some new unique artifacts after deduplication.
    if (currCache.needsToPublish()) {
      Instant deadline = startTime.plusMillis(INTERNAL_TIMEOUT_IN_MS);
      publishFromCache(accountId, taskId, artifactCollectionTaskParams, currCache, deadline);
    }

    log.info("Published artifact successfully");
    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  private void collectArtifacts(String accountId, String artifactStreamId, PerpetualTaskId taskId,
      BuildSourceParameters buildSourceParameters, ArtifactsPublishedCache<BuildDetails> currCache) {
    // Fetch new build details if there are no unpublished build details in the cache.
    final BuildSourceExecutionResponse buildSourceExecutionResponse =
        artifactRepositoryService.publishCollectedArtifacts(buildSourceParameters, currCache);

    // Return early if the artifact collection is unsuccessful.
    if (buildSourceExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      publishToManager(accountId, artifactStreamId, taskId, buildSourceExecutionResponse);
      log.info("Published unsuccessful artifact collection result");
      return;
    }

    List<BuildDetails> builds = buildSourceExecutionResponse.getBuildSourceResponse().getBuildDetails();
    if (isEmpty(builds)) {
      log.info("Published empty artifact collection result");
      return;
    }

    // Add to cache and remove duplicates.
    currCache.addCollectionResult(builds);
  }

  private void publishFromCache(String accountId, PerpetualTaskId taskId, ArtifactCollectionTaskParams taskParams,
      ArtifactsPublishedCache currCache, Instant deadline) {
    // If deadline (which was conservative) has passed, don't try to make any more publish calls to manager.
    if (deadline.isBefore(Instant.now())) {
      return;
    }

    String artifactStreamId = taskParams.getArtifactStreamId();
    if (!publishDeletedArtifactKeys(accountId, artifactStreamId, taskId, currCache)) {
      return;
    }
    if (publishUnpublishedBuildDetails(accountId, artifactStreamId, taskId, currCache)) {
      // Try to republish artifacts until deadline is reached or we have no more unpublished artifacts left.
      publishFromCache(accountId, taskId, taskParams, currCache, deadline);
    }
  }

  /**
   * publishDeletedArtifactKeys publishes artifact keys to the manager that need to be deleted. It returns false if the
   * operation failed unexpectedly.
   */
  private boolean publishDeletedArtifactKeys(
      String accountId, String artifactStreamId, PerpetualTaskId taskId, ArtifactsPublishedCache currCache) {
    Set<String> toBeDeletedArtifactKeys = currCache.getToBeDeletedArtifactKeys();
    if (isEmpty(toBeDeletedArtifactKeys)) {
      log.info(
          "Empty toBeDeletedArtifactKeys in publishDeletedArtifactKeys for artifactStreamId: {}, perpetualTaskId: {}",
          artifactStreamId, taskId.getId());
      return true;
    }

    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .buildSourceResponse(
                BuildSourceResponse.builder().toBeDeletedKeys(toBeDeletedArtifactKeys).cleanup(true).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .artifactStreamId(artifactStreamId)
            .build();

    boolean published = publishToManager(accountId, artifactStreamId, taskId, buildSourceExecutionResponse);
    if (!published) {
      return false;
    }

    currCache.removeDeletedArtifactKeys(toBeDeletedArtifactKeys);
    return true;
  }

  /**
   * publishUnpublishedBuildDetails publishes build details to the manager that are in the cache and are unpublished. It
   * returns true if the operation succeeds and there are more build details left after the current batch.
   */
  private boolean publishUnpublishedBuildDetails(String accountId, String artifactStreamId, PerpetualTaskId taskId,
      ArtifactsPublishedCache<BuildDetails> currCache) {
    ImmutablePair<List<BuildDetails>, Boolean> resp = currCache.getLimitedUnpublishedBuildDetails();
    List<BuildDetails> builds = resp.getLeft();
    if (isEmpty(builds)) {
      log.info("Empty build details in publishUnpublishedBuildDetails for artifactStreamId: {}, perpetualTaskId: {}",
          artifactStreamId, taskId.getId());
      return false;
    }

    // This signifies there are more build details left after this batch.
    boolean hasMoreToPublish = resp.getRight();
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(builds).stable(!hasMoreToPublish).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .artifactStreamId(artifactStreamId)
            .build();

    boolean published = publishToManager(accountId, artifactStreamId, taskId, buildSourceExecutionResponse);
    if (!published) {
      return false;
    }

    currCache.addPublishedBuildDetails(builds);
    return hasMoreToPublish;
  }

  private boolean publishToManager(String accountId, String artifactStreamId, PerpetualTaskId taskId,
      BuildSourceExecutionResponse buildSourceExecutionResponse) {
    try {
      byte[] responseSerialized = referenceFalseKryoSerializer.asBytes(buildSourceExecutionResponse);

      executeWithExceptions(delegateAgentManagerClient.publishArtifactCollectionResultV2(taskId.getId(), accountId,
          RequestBody.create(MediaType.parse("application/octet-stream"), responseSerialized)));
      return true;
    } catch (Exception ex) {
      log.error(
          format(
              "Failed to publish build source execution response with status: %s for artifactStreamId: %s, perpetualTaskId: %s",
              artifactStreamId, buildSourceExecutionResponse.getCommandExecutionStatus().name(), taskId.getId()),
          ex);
      return false;
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    ArtifactCollectionTaskParams taskParams = getTaskParams(params);
    cache.invalidate(taskParams.getArtifactStreamId());
    return false;
  }

  private ArtifactCollectionTaskParams getTaskParams(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), ArtifactCollectionTaskParams.class);
  }

  private ArtifactsPublishedCache<BuildDetails> getArtifactsPublishedCached(
      String artifactStreamId, BuildSourceParameters buildSourceParameters) {
    Function<BuildDetails, String> buildDetailsKeyFn = DelegateArtifactCollectionUtils.getBuildDetailsKeyFn(
        buildSourceParameters.getArtifactStreamType(), buildSourceParameters.getArtifactStreamAttributes());
    boolean enableCleanup =
        DelegateArtifactCollectionUtils.supportsCleanup(buildSourceParameters.getArtifactStreamType());
    return cache.get(artifactStreamId,
        id
        -> new ArtifactsPublishedCache(
            buildSourceParameters.getSavedBuildDetailsKeys(), buildDetailsKeyFn, enableCleanup));
  }
}
