/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.PARKED;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_EXPIRED;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountStatus;
import software.wings.beans.LicenseInfo.LicenseInfoKeys;
import software.wings.beans.TaskType;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class FailDelegateTaskIterator implements MongoPersistenceIterator.Handler<Account> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private HPersistence persistence;
  @Inject private AccountService accountService;
  @Inject private ConfigurationController configurationController;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private Clock clock;
  @Inject private DelegateCache delegateCache;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private ValidationFailedTaskMessageHelper validationFailedTaskMessageHelper;

  private static final long VALIDATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

  private static final long DELEGATE_TASK_FAIL_TIMEOUT = 5;
  private static final FindOptions expiryLimit = new FindOptions().limit(100);
  private static final SecureRandom random = new SecureRandom();

  public void registerIterators(int threadPoolSize) {
    PersistenceIteratorFactory.PumpExecutorOptions options =
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .interval(Duration.ofSeconds(DELEGATE_TASK_FAIL_TIMEOUT))
            .poolSize(threadPoolSize)
            .name("DelegateTaskFail")
            .build();
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(options, FailDelegateTaskIterator.class,
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .clazz(Account.class)
            .fieldName(AccountKeys.delegateTaskFailIteration)
            .targetInterval(Duration.ofSeconds(DELEGATE_TASK_FAIL_TIMEOUT))
            .acceptableNoAlertDelay(Duration.ofSeconds(45))
            .acceptableExecutionTime(Duration.ofSeconds(30))
            .filterExpander(query
                -> query.or(query.criteria(AccountKeys.licenseInfo).doesNotExist(),
                    query.criteria(AccountKeys.licenseInfo + "." + LicenseInfoKeys.accountStatus)
                        .equal(AccountStatus.ACTIVE)))
            .handler(this)
            .schedulingType(MongoPersistenceIterator.SchedulingType.REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Account account) {
    if (configurationController.isPrimary()) {
      markTimedOutTasksAsFailed(account);
      markLongQueuedTasksAsFailed(account);
      failValidationCompletedQueuedTask(account);
    }
  }

  @VisibleForTesting
  public void markTimedOutTasksAsFailed(Account account) {
    List<Key<DelegateTask>> longRunningTimedOutTaskKeys = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                                              .filter(DelegateTaskKeys.accountId, account.getUuid())
                                                              .filter(DelegateTaskKeys.status, STARTED)
                                                              .field(DelegateTaskKeys.expiry)
                                                              .lessThan(currentTimeMillis())
                                                              .asKeyList(expiryLimit);

    if (!longRunningTimedOutTaskKeys.isEmpty()) {
      List<String> keyList = longRunningTimedOutTaskKeys.stream().map(key -> key.getId().toString()).collect(toList());
      log.info("Marking following timed out tasks as failed [{}]", keyList);
      endTasks(keyList);
    }
  }

  private AtomicInteger clustering = new AtomicInteger(1);

  @VisibleForTesting
  public void markLongQueuedTasksAsFailed(Account account) {
    // Find tasks which have been queued for too long
    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                    .filter(DelegateTaskKeys.accountId, account.getUuid())
                                    .field(DelegateTaskKeys.status)
                                    .in(asList(QUEUED, PARKED, ABORTED))
                                    .field(DelegateTaskKeys.expiry)
                                    .lessThan(currentTimeMillis());

    // We usually pick from the top, but if we have full bucket we maybe slowing down
    // lets randomize a bit to increase the distribution
    int clusteringValue = clustering.get();
    if (clusteringValue > 1) {
      query.field(DelegateTaskKeys.createdAt).mod(clusteringValue, random.nextInt(clusteringValue));
    }

    List<Key<DelegateTask>> longQueuedTaskKeys = query.asKeyList(expiryLimit);
    clustering.set(longQueuedTaskKeys.size() == expiryLimit.getLimit() ? Math.min(16, clusteringValue * 2)
                                                                       : Math.max(1, clusteringValue / 2));

    if (!longQueuedTaskKeys.isEmpty()) {
      List<String> keyList = longQueuedTaskKeys.stream().map(key -> key.getId().toString()).collect(toList());
      log.info("Marking following long queued tasks as failed [{}]", keyList);
      endTasks(keyList);
    }
  }

  @VisibleForTesting
  public void endTasks(List<String> taskIds) {
    Map<String, DelegateTask> delegateTasks = new HashMap<>();
    Map<String, String> taskWaitIds = new HashMap<>();
    List<DelegateTask> tasksToExpire = new ArrayList<>();
    List<String> taskIdsToExpire = new ArrayList<>();
    try {
      List<DelegateTask> tasks = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                     .field(DelegateTaskKeys.uuid)
                                     .in(taskIds)
                                     .asList();

      for (DelegateTask task : tasks) {
        if (shouldExpireTask(task)) {
          tasksToExpire.add(task);
          taskIdsToExpire.add(task.getUuid());
          delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_EXPIRED);
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
              persistence.createQuery(DelegateTask.class, excludeAuthority).filter(DelegateTaskKeys.uuid, taskId).get();
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
            String waitId = persistence.createQuery(DelegateTask.class, excludeAuthority)
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
        persistence.createQuery(DelegateTask.class, excludeAuthority).field(DelegateTaskKeys.uuid).in(taskIdsToExpire));

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
  public void failValidationCompletedQueuedTask(Account account) {
    long validationTime = clock.millis() - VALIDATION_TIMEOUT;
    Query<DelegateTask> validationStartedTaskQuery = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                                         .filter(DelegateTaskKeys.accountId, account.getUuid())
                                                         .filter(DelegateTaskKeys.status, QUEUED)
                                                         .field(DelegateTaskKeys.validationStartedAt)
                                                         .lessThan(validationTime);
    try (HIterator<DelegateTask> iterator = new HIterator<>(validationStartedTaskQuery.fetch())) {
      while (iterator.hasNext()) {
        DelegateTask delegateTask = iterator.next();
        if (delegateTask.getValidationCompleteDelegateIds().containsAll(
                delegateTask.getEligibleToExecuteDelegateIds())) {
          log.info("Found delegate task {} with validation completed by all delegates but not assigned",
              delegateTask.getUuid());
          try (AutoLogContext ignore = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
                   TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
            // Check whether a whitelisted delegate is connected
            List<String> whitelistedDelegates = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
            if (isNotEmpty(whitelistedDelegates)) {
              log.info("Waiting for task {} to be acquired by a whitelisted delegate: {}", delegateTask.getUuid(),
                  whitelistedDelegates);
              return;
            }
            final String errorMessage = validationFailedTaskMessageHelper.generateValidationError(delegateTask);
            log.info("Failing task {} due to validation error, {}", delegateTask.getUuid(), errorMessage);

            delegateSelectionLogsService.logTaskValidationFailed(delegateTask, errorMessage);

            DelegateResponseData response;
            if (delegateTask.getData().isAsync()) {
              response = ErrorNotifyResponseData.builder()
                             .failureTypes(EnumSet.of(FailureType.DELEGATE_PROVISIONING))
                             .errorMessage(errorMessage)
                             .build();
            } else {
              response = RemoteMethodReturnValueData.builder()
                             .exception(new InvalidRequestException(errorMessage, USER))
                             .build();
            }
            Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class)
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
  }
}
