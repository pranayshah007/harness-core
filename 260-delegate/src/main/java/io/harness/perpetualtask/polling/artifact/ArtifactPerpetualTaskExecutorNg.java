/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.artifact;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.polling.ArtifactPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.polling.ArtifactCollectionTaskParamsNg;
import io.harness.perpetualtask.polling.PollingResponsePublisher;
import io.harness.serializer.KryoSerializer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ArtifactPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  private final ArtifactRepositoryServiceImpl artifactRepositoryService;
  private final PollingResponsePublisher pollingResponsePublisher;
  private final KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private final KryoSerializer referenceFalseKryoSerializer;

  private final @Getter Cache<String, ArtifactsCollectionCache> cache = Caffeine.newBuilder().build();
  private static final long TIMEOUT_IN_MILLIS = 90L * 1000;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    ArtifactCollectionTaskParamsNg taskParams = getTaskParams(params);
    String pollingDocId = taskParams.getPollingDocId();
    String perpetualTaskId = taskId.getId();
    ArtifactTaskParameters artifactTaskParameters =
        (ArtifactTaskParameters) getKryoSerializer(params.getReferenceFalseKryoSerializer())
            .asObject(taskParams.getArtifactCollectionParams().toByteArray());
    ArtifactsCollectionCache artifactsCollectionCache = cache.get(pollingDocId, id -> new ArtifactsCollectionCache());

    Instant startTime = Instant.now();
    if (!artifactsCollectionCache.needsToPublish()) {
      collectArtifacts(artifactsCollectionCache, artifactTaskParameters, perpetualTaskId, pollingDocId,
          params.getReferenceFalseKryoSerializer());
    }

    if (artifactsCollectionCache.needsToPublish()) {
      Instant deadline = startTime.plusMillis(TIMEOUT_IN_MILLIS);
      publishFromCache(perpetualTaskId, pollingDocId, artifactTaskParameters, artifactsCollectionCache, deadline,
          params.getReferenceFalseKryoSerializer());
    }
    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  private void publishFromCache(String perpetualTaskId, String pollingDocId,
      ArtifactTaskParameters artifactTaskParameters, ArtifactsCollectionCache artifactsCollectionCache,
      Instant deadline, boolean referenceFalseSerializer) {
    if (deadline.isBefore(Instant.now())) {
      return;
    }

    List<ArtifactDelegateResponse> unpublishedArtifacts = artifactsCollectionCache.getUnpublishedArtifacts();
    Set<String> toBeDeletedKeys = artifactsCollectionCache.getToBeDeletedArtifactKeys();
    if (isEmpty(unpublishedArtifacts) && isEmpty(toBeDeletedKeys)) {
      return;
    }

    PollingDelegateResponse response =
        PollingDelegateResponse.builder()
            .accountId(artifactTaskParameters.getAccountId())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .pollingDocId(pollingDocId)
            .pollingResponseInfc(ArtifactPollingDelegateResponse.builder()
                                     .unpublishedArtifacts(unpublishedArtifacts)
                                     .toBeDeletedKeys(toBeDeletedKeys)
                                     .firstCollectionOnDelegate(artifactsCollectionCache.isFirstCollectionOnDelegate())
                                     .build())
            .build();

    if (pollingResponsePublisher.publishToManger(perpetualTaskId, response, referenceFalseSerializer)) {
      artifactsCollectionCache.setFirstCollectionOnDelegate(false);
      artifactsCollectionCache.clearUnpublishedArtifacts(unpublishedArtifacts);
      artifactsCollectionCache.removeDeletedArtifactKeys(toBeDeletedKeys);
    }
  }

  private ArtifactCollectionTaskParamsNg getTaskParams(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), ArtifactCollectionTaskParamsNg.class);
  }

  private void collectArtifacts(ArtifactsCollectionCache artifactsCollectionCache, ArtifactTaskParameters taskParams,
      String taskId, String pollingDocId, boolean referenceFalseSerializer) {
    try {
      ArtifactTaskExecutionResponse response = artifactRepositoryService.collectBuilds(taskParams);

      if (response == null) {
        log.error("Unsupported polling operation " + taskParams.getArtifactTaskType());
        return;
      }

      if (isEmpty(response.getArtifactDelegateResponses())) {
        log.info("No artifacts present in repository");
        return;
      }

      artifactsCollectionCache.populateCache(response.getArtifactDelegateResponses());
    } catch (Exception e) {
      log.error("Error while collecting artifacts ", e);
      pollingResponsePublisher.publishToManger(taskId,
          PollingDelegateResponse.builder()
              .accountId(taskParams.getAccountId())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(e.getMessage())
              .pollingDocId(pollingDocId)
              .build(),
          referenceFalseSerializer);
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    ArtifactCollectionTaskParamsNg taskParams = getTaskParams(params);
    cache.invalidate(taskParams.getPollingDocId());
    return true;
  }

  private KryoSerializer getKryoSerializer(boolean referenceFalse) {
    return referenceFalse ? referenceFalseKryoSerializer : kryoSerializer;
  }
}
