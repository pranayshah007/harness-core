/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.queueservice;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.queueservice.DelegateTaskDequeue;
import io.harness.queueservice.ResourceBasedDelegateSelectionCheckForTask;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.queueservice.infc.DelegateServiceQueue;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;

import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateTaskQueueService implements DelegateServiceQueue<DelegateTask>, Runnable {
  @Inject private HsqsServiceClient hsqsServiceClient;
  @Inject private DelegateQueueServiceConfig delegateQueueServiceConfig;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private ResourceBasedDelegateSelectionCheckForTask delegateSelectionCheckForTask;
  @Inject private DelegateCache delegateCache;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject
  public DelegateTaskQueueService(HsqsServiceClient hsqsServiceClient) {
    this.hsqsServiceClient = hsqsServiceClient;
  }

  /**
   *
   * This function provides enqueues delegate task to queue service
   */
  @Override
  public void enqueue(DelegateTask delegateTask) {
    try (AutoLogContext ignore = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
             TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
      String topic = delegateQueueServiceConfig.getTopic();
      String task = java.util.Base64.getEncoder().encodeToString(referenceFalseKryoSerializer.asBytes(delegateTask));

      EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                          .topic(topic)
                                          .payload(task)
                                          .subTopic(delegateTask.getAccountId())
                                          .producerName(topic)
                                          .build();

      EnqueueResponse response = hsqsServiceClient.enqueue(enqueueRequest, "sampleToken").execute().body();
      assert response != null;
      log.info("Delegate task {} queued with item ID {}", delegateTask.getUuid(), response.getItemId());
    } catch (IOException e) {
      log.error("Error while queueing delegate task {}", delegateTask.getUuid(), e);
    }
  }

  /**
   *
   * This function provides dequeues delegate task from queue service, max 100 per back.
   * return list of delegate tasks
   */
  @Override
  public <T> Object dequeue() {
    try {
      DequeueRequest dequeueRequest = DequeueRequest.builder()
                                          .batchSize(100)
                                          .consumerName(delegateQueueServiceConfig.getTopic())
                                          .topic(delegateQueueServiceConfig.getTopic())
                                          .build();
      List<DequeueResponse> dequeueResponses =
          hsqsServiceClient.dequeue(dequeueRequest, "sampleToken").execute().body();
      List<DelegateTaskDequeue> delegateTasksDequeueList =
          dequeueResponses.stream()
              .map(dequeueResponse
                  -> DelegateTaskDequeue.builder()
                         .payload(dequeueResponse.getPayload())
                         .itemId(dequeueResponse.getItemId())
                         .delegateTask(convertToDelegateTask(dequeueResponse.getPayload(), dequeueResponse.getItemId())
                                           .orElse(null))
                         .build())
              .filter(this::isResourceAvailableToAssignTask)
              .collect(toList());
      delegateTasksDequeueList.forEach(this::acknowledgeAndProcessDelegateTask);
      return true;
    } catch (IOException e) {
      log.error("Error while dequeue delegate task ", e);
      return false;
    }
  }

  /**
   *
   * Once the object is dequeued, acknowledge will confirm the item is removed from the queue
   */
  @Override
  public String acknowledge(String itemId, String accountId) {
    try {
      AckResponse response =
          hsqsServiceClient
              .ack(AckRequest.builder().consumerName(delegateQueueServiceConfig.getTopic()).subTopic(accountId).build(),
                  "sampleToken")
              .execute()
              .body();
      return response != null ? response.getItemID() : "";
    } catch (IOException e) {
      log.error("Error while acknowledging delegate task ", e);
      return null;
    }
  }

  private boolean isResourceAvailableToAssignTask(DelegateTaskDequeue delegateTaskDequeue) {
    TaskType taskType = TaskType.valueOf(delegateTaskDequeue.getDelegateTask().getTaskDataV2().getTaskType());
    String accountId = delegateTaskDequeue.getDelegateTask().getAccountId();
    List<Delegate> delegateList =
        getDelegatesList(delegateTaskDequeue.getDelegateTask().getEligibleToExecuteDelegateIds(), accountId);
    Optional<List<String>> filteredDelegateList =
        delegateSelectionCheckForTask.perform(delegateList, taskType, accountId);
    return filteredDelegateList.isPresent();
  }

  private List<Delegate> getDelegatesList(List<String> eligibleDelegateId, String accountId) {
    return eligibleDelegateId.stream().map(id -> delegateCache.get(accountId, id, false)).collect(Collectors.toList());
  }

  private void acknowledgeAndProcessDelegateTask(DelegateTaskDequeue delegateTaskDequeue) {
    try {
      if (delegateTaskDequeue.getDelegateTask() != null
          && delegateTaskServiceClassic.saveAndBroadcastDelegateTaskV2(delegateTaskDequeue.getDelegateTask())) {
        acknowledge(delegateTaskDequeue.getItemId(), delegateTaskDequeue.getDelegateTask().getAccountId());
      }
    } catch (Exception e) {
      log.error("Unable to acknowledge queue service on dequeue delegate task id {}, item Id {}",
          delegateTaskDequeue.getDelegateTask().getUuid(), delegateTaskDequeue.getItemId(), e);
    }
  }

  public Optional<DelegateTask> convertToDelegateTask(String payload, String itemId) {
    try {
      return Optional.ofNullable(
          (DelegateTask) referenceFalseKryoSerializer.asObject(Base64.getDecoder().decode(payload)));
    } catch (Exception e) {
      log.error("Error while decoding delegate task from queue, item Id {}. ", itemId, e);
    }
    return Optional.empty();
  }

  @Override
  public void run() {
    if (getMaintenanceFlag()) {
      return;
    }
    dequeue();
  }
}
