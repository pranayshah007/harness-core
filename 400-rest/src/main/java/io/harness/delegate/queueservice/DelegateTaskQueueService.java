/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.queueservice;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.hsqs.client.HsqsClient;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateTaskQueueService implements DelegateServiceQueue<DelegateTask>, Runnable {
  @Inject private HsqsClient hsqsServiceClient;
  @Inject private DelegateQueueServiceConfig delegateQueueServiceConfig;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private ResourceBasedDelegateSelectionCheckForTask delegateSelectionCheckForTask;
  @Inject private DelegateCache delegateCache;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject
  public DelegateTaskQueueService(HsqsClient hsqsServiceClient) {
    this.hsqsServiceClient = hsqsServiceClient;
  }

  /**
   *
   * This function provides enqueues delegate task to queue service
   */
  @Override
  public void enqueue(DelegateTask delegateTask) {
    String taskType = delegateTask.getData() != null ? delegateTask.getData().getTaskType()
                                                     : delegateTask.getTaskDataV2().getTaskType();
    try (AutoLogContext ignore = new TaskLogContext(
             delegateTask.getUuid(), taskType, TaskType.valueOf(taskType).getTaskGroup().name(), OVERRIDE_ERROR)) {
      String topic = delegateQueueServiceConfig.getTopic();
      String task = referenceFalseKryoSerializer.asString(delegateTask);
      EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                          .topic(topic)
                                          .payload(task)
                                          .subTopic(delegateTask.getAccountId())
                                          .producerName(topic)
                                          .build();

      EnqueueResponse response = hsqsServiceClient.enqueue(enqueueRequest).execute().body();
      log.info("Delegate task {} queued with item ID {}", delegateTask.getUuid(), response.getItemId());
    } catch (Exception e) {
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
      List<DequeueResponse> dequeueResponses = hsqsServiceClient.dequeue(dequeueRequest).execute().body();
      List<DelegateTaskDequeue> delegateTasksDequeueList =
          Objects.requireNonNull(dequeueResponses)
              .stream()
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
      AckResponse response = hsqsServiceClient
                                 .ack(AckRequest.builder()
                                          .itemID(itemId)
                                          .topic(delegateQueueServiceConfig.getTopic())
                                          .subTopic(accountId)
                                          .build())
                                 .execute()
                                 .body();
      return Objects.requireNonNull(response).getItemID();
    } catch (IOException e) {
      log.error("Error while acknowledging delegate task ", e);
      return null;
    }
  }
  private boolean isResourceAvailableToAssignTask(DelegateTaskDequeue delegateTaskDequeue) {
    return isResourceAvailableToAssignTask(delegateTaskDequeue.getDelegateTask());
  }

  public boolean isResourceAvailableToAssignTask(DelegateTask delegateTask) {
    if (delegateTask.getTaskDataV2() != null) {
      return isResourceAvailableToAssignTaskV2(delegateTask);
    }

    TaskType taskType = TaskType.valueOf(delegateTask.getData().getTaskType());
    String accountId = delegateTask.getAccountId();
    List<Delegate> delegateList = getDelegatesList(delegateTask.getEligibleToExecuteDelegateIds(), accountId);
    Optional<List<String>> filteredDelegateList =
        delegateSelectionCheckForTask.perform(delegateList, taskType, accountId);
    if (filteredDelegateList.isEmpty() || isEmpty(filteredDelegateList.get())) {
      return false;
    }
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(filteredDelegateList.get()));
    return true;
  }

  @VisibleForTesting
  boolean isResourceAvailableToAssignTaskV2(DelegateTask delegateTask) {
    TaskType taskType = TaskType.valueOf(delegateTask.getTaskDataV2().getTaskType());
    String accountId = delegateTask.getAccountId();
    List<Delegate> delegateList = getDelegatesList(delegateTask.getEligibleToExecuteDelegateIds(), accountId);
    Optional<List<String>> filteredDelegateList =
        delegateSelectionCheckForTask.perform(delegateList, taskType, accountId);
    if (filteredDelegateList.isEmpty() || isNotEmpty(filteredDelegateList.get())) {
      return false;
    }
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(filteredDelegateList.get()));
    return true;
  }

  @VisibleForTesting
  List<Delegate> getDelegatesList(List<String> eligibleDelegateId, String accountId) {
    return eligibleDelegateId.stream().map(id -> delegateCache.get(accountId, id, false)).collect(Collectors.toList());
  }

  @VisibleForTesting
  void acknowledgeAndProcessDelegateTask(DelegateTaskDequeue delegateTaskDequeue) {
    try {
      if (delegateTaskDequeue.getDelegateTask() != null) {
        if (delegateTaskDequeue.getDelegateTask().getTaskDataV2() != null) {
          acknowledgeAndProcessDelegateTaskV2(delegateTaskDequeue);
        }
        String itemId =
            acknowledge(delegateTaskDequeue.getItemId(), delegateTaskDequeue.getDelegateTask().getAccountId());
        log.info("Delegate task {} acknowledge with item id {} from Queue Service",
            delegateTaskDequeue.getDelegateTask().getUuid(), itemId);
        if (isNotEmpty(itemId)) {
          String taskId =
              delegateTaskServiceClassic.saveAndBroadcastDelegateTask(delegateTaskDequeue.getDelegateTask());
          log.info("Queued task {} broadcasting to delegate.", taskId);
        }
      }
    } catch (Exception e) {
      log.error("Unable to acknowledge queue service on dequeue delegate task id {}, item Id {}",
          delegateTaskDequeue.getDelegateTask().getUuid(), delegateTaskDequeue.getItemId(), e);
    }
  }
  @VisibleForTesting
  void acknowledgeAndProcessDelegateTaskV2(DelegateTaskDequeue delegateTaskDequeue) {
    try {
      if (delegateTaskDequeue.getDelegateTask() != null) {
        String itemId =
            acknowledge(delegateTaskDequeue.getItemId(), delegateTaskDequeue.getDelegateTask().getAccountId());
        log.info("Delegate task {} acknowledge with item id {} from Queue Service",
            delegateTaskDequeue.getDelegateTask().getUuid(), itemId);
        if (isNotEmpty(itemId)) {
          String taskId =
              delegateTaskServiceClassic.saveAndBroadcastDelegateTask(delegateTaskDequeue.getDelegateTask());
          log.info("Queued task {} broadcasting to delegate.", taskId);
        }
      }
    } catch (Exception e) {
      log.error("Unable to acknowledge queue service on dequeue delegate task id {}, item Id {}",
          delegateTaskDequeue.getDelegateTask().getUuid(), delegateTaskDequeue.getItemId(), e);
    }
  }
  @VisibleForTesting
  Optional<DelegateTask> convertToDelegateTask(String payload, String itemId) {
    try {
      DelegateTask delegateTask = (DelegateTask) referenceFalseKryoSerializer.asObject(payload);
      return Optional.ofNullable(delegateTask);
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
