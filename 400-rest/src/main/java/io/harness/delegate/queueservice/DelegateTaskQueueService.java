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
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.logging.AccountLogContext;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateTaskQueueService implements DelegateServiceQueue<DelegateTask>, Runnable {
  @Inject private HsqsClientService hsqsClientService;
  @Inject private DelegateQueueServiceConfig delegateQueueServiceConfig;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private ResourceBasedDelegateSelectionCheckForTask delegateSelectionCheckForTask;
  @Inject private DelegateCache delegateCache;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  /**
   *
   * This function provides enqueues delegate task to queue service
   */
  @Override
  public void enqueue(DelegateTask delegateTask) {
    try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
      String topic = delegateQueueServiceConfig.getTopic();
      String task = referenceFalseKryoSerializer.asString(delegateTask);
      EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                          .topic(topic)
                                          .payload(task)
                                          .subTopic(delegateTask.getAccountId())
                                          .producerName(topic)
                                          .build();

      EnqueueResponse response = hsqsClientService.enqueue(enqueueRequest);
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
                                          .maxWaitDuration(100)
                                          .build();

      List<DequeueResponse> dequeueResponses = hsqsClientService.dequeue(dequeueRequest);
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
    } catch (Exception e) {
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
      AckResponse response = hsqsClientService.ack(AckRequest.builder()
                                                       .itemId(itemId)
                                                       .topic(delegateQueueServiceConfig.getTopic())
                                                       .consumerName(delegateQueueServiceConfig.getTopic())
                                                       .subTopic(accountId)
                                                       .build());

      return Objects.requireNonNull(response).getItemId();
    } catch (Exception e) {
      log.error("Error while acknowledging delegate task ", e);
      return null;
    }
  }
  private boolean isResourceAvailableToAssignTask(DelegateTaskDequeue delegateTaskDequeue) {
    return isDelegateTaskAborted(delegateTaskDequeue)
        || isResourceAvailableToAssignTask(delegateTaskDequeue.getDelegateTask());
  }

  @VisibleForTesting
  public boolean isResourceAvailableToAssignTask(DelegateTask delegateTask) {
    TaskType taskType = TaskType.valueOf(delegateTask.getTaskDataV2().getTaskType());
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
  List<Delegate> getDelegatesList(List<String> eligibleDelegateId, String accountId) {
    return eligibleDelegateId.stream().map(id -> delegateCache.get(accountId, id, false)).collect(Collectors.toList());
  }

  @VisibleForTesting
  void acknowledgeAndProcessDelegateTask(DelegateTaskDequeue delegateTaskDequeue) {
    try {
      if (delegateTaskDequeue.getDelegateTask() != null) {
        String itemId =
            acknowledge(delegateTaskDequeue.getItemId(), delegateTaskDequeue.getDelegateTask().getAccountId());
        log.info("Delegate task {} acknowledge with item id {} from Queue Service",
            delegateTaskDequeue.getDelegateTask().getUuid(), itemId);
        if (isNotEmpty(itemId)) {
          if (isDelegateTaskAborted(delegateTaskDequeue)) {
            delegateTaskServiceClassic.abortTask(
                delegateTaskDequeue.getDelegateTask().getAccountId(), delegateTaskDequeue.getDelegateTask().getUuid());
            delegateCache.removeFromAbortedTaskList(
                delegateTaskDequeue.getDelegateTask().getAccountId(), delegateTaskDequeue.getDelegateTask().getUuid());
            return;
          }
          String taskId =
              delegateTaskServiceClassic.saveAndBroadcastDelegateTaskV2(delegateTaskDequeue.getDelegateTask());
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

  private boolean isDelegateTaskAborted(DelegateTaskDequeue delegateTaskDequeue) {
    // check if it's in the list of aborted task event list
    String accountId = delegateTaskDequeue.getDelegateTask().getAccountId();
    String delegateTaskId = delegateTaskDequeue.getDelegateTask().getUuid();
    try (AutoLogContext ignore1 = new TaskLogContext(delegateTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      Set<String> delegateTaskAborted = delegateCache.getAbortedTaskList(accountId);
      if (isNotEmpty(delegateTaskAborted) && delegateTaskAborted.contains(delegateTaskId)) {
        log.info("Aborting delegate task from queue {}", delegateTaskDequeue.getDelegateTask().getUuid());
        return true;
      }
    }
    return false;
  }
}
