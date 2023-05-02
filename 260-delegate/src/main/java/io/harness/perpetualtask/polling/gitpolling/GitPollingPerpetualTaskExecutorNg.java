/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.gitpolling;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.polling.GitPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.task.gitpolling.request.GitPollingTaskParameters;
import io.harness.delegate.task.gitpolling.response.GitPollingTaskExecutionResponse;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.polling.GitPollingTaskParamsNg;
import io.harness.perpetualtask.polling.PollingResponsePublisher;
import io.harness.serializer.KryoSerializer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
public class GitPollingPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  private final GitPollingServiceImpl gitPollingService;
  private final PollingResponsePublisher pollingResponsePublisher;
  private final KryoSerializer kryoSerializer;
  private final KryoSerializer referenceFalseKryoSerializer;

  private final @Getter Cache<String, GitPollingCache> cache = Caffeine.newBuilder().build();
  private static final long TIMEOUT_IN_MILLIS = 90L * 1000;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    GitPollingTaskParamsNg taskParams = getTaskParams(params);
    String pollingDocId = taskParams.getPollingDocId();
    String perpetualTaskId = taskId.getId();
    GitPollingTaskParameters gitPollingTaskParameters =
        (GitPollingTaskParameters) getKryoSerializer(params.getReferenceFalseKryoSerializer())
            .asObject(taskParams.getGitpollingWebhookParams().toByteArray());
    GitPollingCache gitPollingCache = cache.get(pollingDocId, id -> new GitPollingCache());

    if (!gitPollingCache.needsToPublish()) {
      getWebhookEvents(gitPollingCache, gitPollingTaskParameters, perpetualTaskId, pollingDocId,
          params.getReferenceFalseKryoSerializer());
    }

    if (gitPollingCache.needsToPublish()) {
      publishFromCache(perpetualTaskId, pollingDocId, gitPollingTaskParameters, gitPollingCache,
          params.getReferenceFalseKryoSerializer());
    }

    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  private void publishFromCache(String perpetualTaskId, String pollingDocId, GitPollingTaskParameters taskParams,
      GitPollingCache gitPollingCache, boolean referenceFalseSerializer) {
    List<GitPollingWebhookData> unpublishedWebhooks = gitPollingCache.getUnpublishedWebhooks();
    Set<String> toBeDeletedIds = gitPollingCache.getToBeDeletedWebookDeliveryIds();
    if (isEmpty(unpublishedWebhooks) && isEmpty(toBeDeletedIds)) {
      return;
    }

    PollingDelegateResponse response =
        PollingDelegateResponse.builder()
            .accountId(taskParams.getAccountId())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .pollingDocId(pollingDocId)
            .pollingResponseInfc(GitPollingDelegateResponse.builder()
                                     .unpublishedEvents(unpublishedWebhooks)
                                     .toBeDeletedIds(toBeDeletedIds)
                                     .firstCollectionOnDelegate(gitPollingCache.isFirstCollectionOnDelegate())
                                     .build())
            .build();

    if (pollingResponsePublisher.publishToManger(perpetualTaskId, response, referenceFalseSerializer)) {
      gitPollingCache.setFirstCollectionOnDelegate(false);
      gitPollingCache.clearUnpublishedWebhooks(unpublishedWebhooks);
      gitPollingCache.removeDeletedWebhookIds(toBeDeletedIds);
    }
  }

  private void getWebhookEvents(GitPollingCache gitPollingCache, GitPollingTaskParameters gitPollingTaskParameters,
      String taskId, String pollingDocId, boolean referenceFalseSerializer) {
    try {
      GitPollingTaskExecutionResponse response =
          gitPollingService.getWebhookRecentDeliveryEvents(gitPollingTaskParameters);

      if (response == null) {
        log.error("Unsupported polling operation " + gitPollingTaskParameters.getGitPollingTaskType());
        return;
      }

      if (isEmpty(response.getGitPollingWebhookEventResponses())) {
        log.info("No webhook events present in repository");
        return;
      }

      gitPollingCache.populateCache(response.getGitPollingWebhookEventResponses());

    } catch (Exception e) {
      log.error("Error while getting webhooks ", e);
      pollingResponsePublisher.publishToManger(taskId,
          PollingDelegateResponse.builder()
              .accountId(gitPollingTaskParameters.getAccountId())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(e.getMessage())
              .pollingDocId(pollingDocId)
              .build(),
          referenceFalseSerializer);
    }
  }

  private GitPollingTaskParamsNg getTaskParams(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), GitPollingTaskParamsNg.class);
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return true;
  }

  private KryoSerializer getKryoSerializer(boolean referenceFalse) {
    return referenceFalse ? referenceFalseKryoSerializer : kryoSerializer;
  }
}
