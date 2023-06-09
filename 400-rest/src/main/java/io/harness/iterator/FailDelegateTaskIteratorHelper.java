/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.PARKED;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.exception.WingsException.USER;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_EXPIRED;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class FailDelegateTaskIteratorHelper {
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private HPersistence persistence;
  @Inject private ConfigurationController configurationController;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private Clock clock;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateCache delegateCache;

  private static final long VALIDATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);
  private static int MAX_BROADCAST_ROUND = 3;

  @VisibleForTesting
  public void markTimedOutTasksAsFailed(DelegateTask delegateTask, boolean isDelegateTaskMigrationEnabled) {
    if (delegateTask.getStatus().equals(STARTED) && delegateTask.getExpiry() < currentTimeMillis()) {
      log.info("Marking following timed out tasks as failed [{}]", delegateTask.getUuid());
      endTasks(asList(delegateTask.getUuid()), isDelegateTaskMigrationEnabled);
    }
  }

  @VisibleForTesting
  public void markLongQueuedTasksAsFailed(DelegateTask delegateTask, boolean isDelegateTaskMigrationEnabled) {
    if (asList(QUEUED, PARKED, ABORTED).contains(delegateTask.getStatus())
        && (delegateTask.getExpiry() < currentTimeMillis())) {
      log.info("Marking following long queued tasks as failed [{}]", delegateTask.getUuid());
      endTasks(asList(delegateTask.getUuid()), isDelegateTaskMigrationEnabled);
    }
  }

  @VisibleForTesting
  public void markNotAcquiredAfterMultipleBroadcastAsFailed(
      DelegateTask delegateTask, boolean isDelegateTaskMigrationEnabled) {
    long now = clock.millis();
    if (delegateTask.getStatus().equals(QUEUED) && delegateTask.getBroadcastRound() <= MAX_BROADCAST_ROUND
        && delegateTask.getNextBroadcast() < (now + TimeUnit.MINUTES.toMillis(1))) {
      log.info("Marking non acquired task after multiple broadcast attempts, as failed [{}]", delegateTask.getUuid());
      endTasks(asList(delegateTask.getUuid()), isDelegateTaskMigrationEnabled);
    }
  }

  @VisibleForTesting
  public void endTasks(List<String> taskIds, boolean isDelegateTaskMigrationEnabled) {
    Map<String, DelegateTask> delegateTasks = new HashMap<>();
    Map<String, String> taskWaitIds = new HashMap<>();
    List<DelegateTask> tasksToExpire = new ArrayList<>();
    List<String> taskIdsToExpire = new ArrayList<>();
    try {
      List<DelegateTask> tasks =
          persistence.createQuery(DelegateTask.class, excludeAuthority, isDelegateTaskMigrationEnabled)
              .field(DelegateTaskKeys.uuid)
              .in(taskIds)
              .asList();

      for (DelegateTask task : tasks) {
        if (shouldExpireTask(task)) {
          tasksToExpire.add(task);
          taskIdsToExpire.add(task.getUuid());
          delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_EXPIRED);
          logValidationFailedErrorsInSelectionLog(task);
        }
      }

      delegateTasks.putAll(tasksToExpire.stream().collect(toMap(DelegateTask::getUuid, identity())));
      taskWaitIds.putAll(tasksToExpire.stream()
                             .filter(task -> isNotEmpty(task.getWaitId()))
                             .collect(toMap(DelegateTask::getUuid, DelegateTask::getWaitId)));
    } catch (Exception e1) {
      log.error("Failed to deserialize {} tasks. Trying individually...", taskIds.size(), e1);
      for (String taskId : taskIds) {
        try {
          DelegateTask task =
              persistence.createQuery(DelegateTask.class, excludeAuthority, isDelegateTaskMigrationEnabled)
                  .filter(DelegateTaskKeys.uuid, taskId)
                  .get();
          if (shouldExpireTask(task)) {
            taskIdsToExpire.add(taskId);
            delegateTasks.put(taskId, task);
            delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_EXPIRED);
            if (isNotEmpty(task.getWaitId())) {
              taskWaitIds.put(taskId, task.getWaitId());
            }
          }
        } catch (Exception e2) {
          log.error("Could not deserialize task {}. Trying again with only waitId field.", taskId, e2);
          taskIdsToExpire.add(taskId);
          try {
            String waitId =
                persistence.createQuery(DelegateTask.class, excludeAuthority, isDelegateTaskMigrationEnabled)
                    .filter(DelegateTaskKeys.uuid, taskId)
                    .project(DelegateTaskKeys.waitId, true)
                    .get()
                    .getWaitId();
            if (isNotEmpty(waitId)) {
              taskWaitIds.put(taskId, waitId);
            }
          } catch (Exception e3) {
            log.error(
                "Could not deserialize task {} with waitId only, giving up. Task will be deleted but notify not called.",
                taskId, e3);
          }
        }
      }
    }

    boolean deleted = persistence.deleteOnServer(
        persistence.createQuery(DelegateTask.class, excludeAuthority, isDelegateTaskMigrationEnabled)
            .field(DelegateTaskKeys.uuid)
            .in(taskIdsToExpire),
        isDelegateTaskMigrationEnabled);

    if (deleted) {
      taskIdsToExpire.forEach(taskId -> {
        if (taskWaitIds.containsKey(taskId)) {
          String errorMessage = delegateTasks.containsKey(taskId)
              ? assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTasks.get(taskId))
              : "Unable to determine proper error as delegate task could not be deserialized.";
          log.info("Marking task as failed - {}: {}", taskId, errorMessage);
          if (delegateTasks.get(taskId) != null) {
            delegateTaskService.handleResponse(delegateTasks.get(taskId), null,
                DelegateTaskResponse.builder()
                    .accountId(delegateTasks.get(taskId).getAccountId())
                    .responseCode(DelegateTaskResponse.ResponseCode.FAILED)
                    .response(ErrorNotifyResponseData.builder().expired(true).errorMessage(errorMessage).build())
                    .build());
          }
        }
      });
    }
  }

  private boolean shouldExpireTask(DelegateTask task) {
    return !task.isForceExecute();
  }

  @VisibleForTesting
  public void failValidationCompletedQueuedTask(DelegateTask delegateTask, boolean isDelegateTaskMigrationEnabled) {
    if (delegateTask == null) {
      return;
    }
    long validationTime = clock.millis() - VALIDATION_TIMEOUT;
    if (delegateTask.getStatus().equals(QUEUED) && delegateTask.getValidationStartedAt() != null
        && delegateTask.getValidationStartedAt() < validationTime) {
      if (delegateTask.getValidationCompleteDelegateIds().containsAll(delegateTask.getEligibleToExecuteDelegateIds())) {
        log.info("Found delegate task {} with validation completed by all delegates but not assigned",
            delegateTask.getUuid());
        try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
          // Check whether a whitelisted delegate is connected
          List<String> whitelistedDelegates = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
          if (isNotEmpty(whitelistedDelegates)) {
            log.info("Waiting for task {} to be acquired by a whitelisted delegate: {}", delegateTask.getUuid(),
                whitelistedDelegates);
            return;
          }
          final String errorMessage =
              assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);
          log.info("Failing task {} due to validation error, {}", delegateTask.getUuid(), errorMessage);

          DelegateResponseData response;
          boolean async = delegateTask.getTaskDataV2() != null ? delegateTask.getTaskDataV2().isAsync()
                                                               : delegateTask.getData().isAsync();
          if (async) {
            response = ErrorNotifyResponseData.builder()
                           .failureTypes(EnumSet.of(FailureType.DELEGATE_PROVISIONING))
                           .errorMessage(errorMessage)
                           .build();
          } else {
            response = RemoteMethodReturnValueData.builder()
                           .exception(new InvalidRequestException(errorMessage, USER))
                           .build();
          }
          Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class, isDelegateTaskMigrationEnabled)
                                              .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                              .filter(DelegateTaskKeys.uuid, delegateTask.getUuid());

          delegateTaskService.handleResponse(delegateTask, taskQuery,
              DelegateTaskResponse.builder()
                  .accountId(delegateTask.getAccountId())
                  .response(response)
                  .responseCode(DelegateTaskResponse.ResponseCode.OK)
                  .build());
        }
      }
    }
  }

  public void logValidationFailedErrorsInSelectionLog(DelegateTask delegateTask) {
    if (isEmpty(delegateTask.getExecutionCapabilities())) {
      return;
    }
    if (!delegateTask.getStatus().equals(QUEUED)) {
      return;
    }
    if (delegateTask.getValidationStartedAt() != null
        && !delegateTask.getValidationCompleteDelegateIds().containsAll(
            delegateTask.getEligibleToExecuteDelegateIds())) {
      return;
    }
    delegateTask.getExecutionCapabilities().forEach(capability -> {
      if (isNotEmpty(capability.getCapabilityValidationError())) {
        String capabilityErrors = String.format("%s : [%s]", capability.getCapabilityValidationError(),
            getHostNamesFromDelegateIds(delegateTask.getAccountId(), delegateTask.getEligibleToExecuteDelegateIds()));
        delegateSelectionLogsService.logTaskValidationFailed(delegateTask, capabilityErrors);
      }
    });
  }

  private List<String> getHostNamesFromDelegateIds(String accountId, LinkedList<String> eligibleToExecuteDelegateIds) {
    return eligibleToExecuteDelegateIds.stream()
        .map(id -> delegateCache.get(accountId, id, false))
        .filter(Objects::nonNull)
        .map(Delegate::getHostName)
        .collect(Collectors.toList());
  }
}
