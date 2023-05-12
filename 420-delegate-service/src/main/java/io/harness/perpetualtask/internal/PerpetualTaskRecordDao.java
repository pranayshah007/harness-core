/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_ASSIGNED;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_INVALID;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_NON_ASSIGNABLE;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_UNASSIGNED;

import static java.util.Arrays.asList;

import io.harness.delegate.task.DelegateLogContext;
import io.harness.network.FibonacciBackOff;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerpetualTaskRecordDao {
  private final HPersistence persistence;
  private static final int MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT = 10;
  private static final int BATCH_SIZE_FOR_PERPETUAL_TASK_TO_REBALANCE = 20;

  @Inject
  public PerpetualTaskRecordDao(HPersistence persistence) {
    this.persistence = persistence;
  }

  public void appointDelegate(String taskId, String delegateId, long lastContextUpdated) {
    try (DelegateLogContext ignore = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      log.info("Appoint perpetual task: {}", taskId);
      Query<PerpetualTaskRecord> query =
          persistence.createQuery(PerpetualTaskRecord.class).filter(PerpetualTaskRecordKeys.uuid, taskId);
      UpdateOperations<PerpetualTaskRecord> updateOperations =
          persistence.createUpdateOperations(PerpetualTaskRecord.class)
              .set(PerpetualTaskRecordKeys.delegateId, delegateId)
              .set(PerpetualTaskRecordKeys.state, TASK_ASSIGNED)
              .set(PerpetualTaskRecordKeys.assignAfterMs, 0)
              .unset(PerpetualTaskRecordKeys.assignTryCount)
              .unset(PerpetualTaskRecordKeys.unassignedReason)
              .set(PerpetualTaskRecordKeys.client_context_last_updated, lastContextUpdated);
      persistence.update(query, updateOperations);
    }
  }

  public void updateTaskUnassignedReasonAndException(
      String taskId, PerpetualTaskUnassignedReason reason, int assignTryCount, String exception) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.uuid, taskId)
                                           .field(PerpetualTaskRecordKeys.state)
                                           .in(asList(PerpetualTaskState.TASK_UNASSIGNED, TASK_NON_ASSIGNABLE));
    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.unassignedReason, reason)
            .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED)
            .set(PerpetualTaskRecordKeys.exception, exception)
            .set(PerpetualTaskRecordKeys.assignTryCount,
                Math.min(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT, assignTryCount + 1))
            .set(PerpetualTaskRecordKeys.assignAfterMs,
                System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(FibonacciBackOff.getFibonacciElement(
                        Math.min(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT, assignTryCount + 1))));
    persistence.update(query, updateOperations);
  }

  public void updateTaskUnassignedReason(String taskId, PerpetualTaskUnassignedReason reason, int assignTryCount) {
    updateTaskUnassignedReasonAndException(taskId, reason, assignTryCount, reason.toString());
  }

  public void updateTaskStateNonAssignableReason(
      String taskId, PerpetualTaskUnassignedReason reason, int assignTryCount, PerpetualTaskState perpetualTaskState) {
    updateTaskStateNonAssignableReasonAndException(
        taskId, reason, assignTryCount, perpetualTaskState, reason.toString());
  }

  public void updateTaskStateNonAssignableReasonAndException(String taskId, PerpetualTaskUnassignedReason reason,
      int assignTryCount, PerpetualTaskState perpetualTaskState, String exception) {
    Query<PerpetualTaskRecord> query =
        persistence.createQuery(PerpetualTaskRecord.class)
            .filter(PerpetualTaskRecordKeys.uuid, taskId)
            .field(PerpetualTaskRecordKeys.state)
            .in(asList(PerpetualTaskState.TASK_UNASSIGNED, TASK_NON_ASSIGNABLE, TASK_ASSIGNED));
    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.unassignedReason, reason)
            .set(PerpetualTaskRecordKeys.state, perpetualTaskState)
            .set(PerpetualTaskRecordKeys.exception, exception)
            .set(PerpetualTaskRecordKeys.assignTryCount,
                Math.min(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT, assignTryCount + 1))
            .set(PerpetualTaskRecordKeys.assignAfterMs,
                System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(FibonacciBackOff.getFibonacciElement(
                        Math.min(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT, assignTryCount + 1))));
    persistence.update(query, updateOperations);
  }

  public void updateTaskNonAssignableToAssignable(String accountId) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .field(PerpetualTaskRecordKeys.accountId)
                                           .equal(accountId)
                                           .field(PerpetualTaskRecordKeys.state)
                                           .equal(TASK_NON_ASSIGNABLE);

    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED)
            .unset(PerpetualTaskRecordKeys.unassignedReason)
            .unset(PerpetualTaskRecordKeys.assignTryCount);
    persistence.update(query, updateOperations);
  }

  public void setTaskUnassigned(String taskId) {
    Query<PerpetualTaskRecord> query =
        persistence.createQuery(PerpetualTaskRecord.class).filter(PerpetualTaskRecordKeys.uuid, taskId);

    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED)
            .set(PerpetualTaskRecordKeys.delegateId, "");

    persistence.update(query, updateOperations);
  }

  public boolean resetDelegateIdForTask(
      String accountId, String taskId, PerpetualTaskExecutionBundle taskExecutionBundle) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.accountId, accountId)
                                           .filter(PerpetualTaskRecordKeys.uuid, taskId);

    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.delegateId, "")
            .set(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_UNASSIGNED)
            .unset(PerpetualTaskRecordKeys.unassignedReason)
            .unset(PerpetualTaskRecordKeys.assignTryCount);

    if (taskExecutionBundle != null) {
      updateOperations.set(PerpetualTaskRecordKeys.task_parameters, taskExecutionBundle.toByteArray());
    } else {
      updateOperations.unset(PerpetualTaskRecordKeys.task_parameters);
    }

    UpdateResults update = persistence.update(query, updateOperations);
    return update.getUpdatedCount() > 0;
  }

  public long updateTasksSchedule(String accountId, String perpetualTaskType, long intervalInMillis) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.accountId, accountId)
                                           .filter(PerpetualTaskRecordKeys.perpetualTaskType, perpetualTaskType);

    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.delegateId, "")
            .set(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_UNASSIGNED)
            .set(PerpetualTaskRecordKeys.intervalSeconds, intervalInMillis / 1000)
            .unset(PerpetualTaskRecordKeys.unassignedReason);

    UpdateResults updateResults = persistence.update(query, updateOperations);
    return updateResults.getUpdatedCount();
  }

  public boolean pauseTask(String accountId, String taskId) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.accountId, accountId)
                                           .filter(PerpetualTaskRecordKeys.uuid, taskId);

    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.delegateId, "")
            .set(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_PAUSED);

    updateOperations.unset(PerpetualTaskRecordKeys.task_parameters);

    UpdateResults update = persistence.update(query, updateOperations);
    return update.getUpdatedCount() > 0;
  }

  public String save(PerpetualTaskRecord record) {
    return persistence.save(record);
  }

  public boolean remove(String accountId, String taskId) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .field(PerpetualTaskRecordKeys.accountId)
                                           .equal(accountId)
                                           .field(PerpetualTaskRecordKeys.uuid)
                                           .equal(taskId);
    return persistence.delete(query);
  }

  public boolean removeAllTasksForAccount(String accountId) {
    Query<PerpetualTaskRecord> query =
        persistence.createQuery(PerpetualTaskRecord.class).field(PerpetualTaskRecordKeys.accountId).equal(accountId);
    return persistence.delete(query);
  }

  public List<PerpetualTaskRecord> listAssignedTasks(String delegateId, String accountId) {
    return persistence.createQuery(PerpetualTaskRecord.class)
        .field(PerpetualTaskRecordKeys.state)
        .equal(TASK_ASSIGNED)
        .field(PerpetualTaskRecordKeys.accountId)
        .equal(accountId)
        .field(PerpetualTaskRecordKeys.delegateId)
        .equal(delegateId)
        .project(PerpetualTaskRecordKeys.uuid, true)
        .project(PerpetualTaskRecordKeys.client_context_last_updated, true)
        .asList();
  }

  public List<PerpetualTaskRecord> listAllPerpetualTasksForAccount(String accountId) {
    List<PerpetualTaskRecord> perpetualTaskRecords = new ArrayList<>();

    Query<PerpetualTaskRecord> query =
        persistence.createQuery(PerpetualTaskRecord.class).field(PerpetualTaskRecordKeys.accountId).equal(accountId);
    try (HIterator<PerpetualTaskRecord> tasksIterator = new HIterator<>(query.fetch())) {
      while (tasksIterator.hasNext()) {
        perpetualTaskRecords.add(tasksIterator.next());
      }
    }

    return perpetualTaskRecords;
  }

  public List<PerpetualTaskRecord> listValidK8sWatchPerpetualTasksForAccount(String accountId) {
    List<PerpetualTaskRecord> perpetualTaskRecords = new ArrayList<>();

    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .field(PerpetualTaskRecordKeys.accountId)
                                           .equal(accountId)
                                           .field(PerpetualTaskRecordKeys.perpetualTaskType)
                                           .equal("K8S_WATCH")
                                           .field(PerpetualTaskRecordKeys.state)
                                           .notEqual(TASK_UNASSIGNED);
    try (HIterator<PerpetualTaskRecord> tasksIterator = new HIterator<>(query.fetch())) {
      while (tasksIterator.hasNext()) {
        perpetualTaskRecords.add(tasksIterator.next());
      }
    }

    return perpetualTaskRecords;
  }

  public List<PerpetualTaskRecord> listBatchOfPerpetualTasksToRebalanceForAccount(String accountId) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.accountId, accountId)
                                           .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_TO_REBALANCE);

    return query.asList(new FindOptions().limit(BATCH_SIZE_FOR_PERPETUAL_TASK_TO_REBALANCE));
  }

  public PerpetualTaskRecord getTask(String taskId) {
    return persistence.createQuery(PerpetualTaskRecord.class).field(PerpetualTaskRecordKeys.uuid).equal(taskId).get();
  }

  public Optional<PerpetualTaskRecord> getExistingPerpetualTask(
      String accountId, String perpetualTaskType, PerpetualTaskClientContext clientContext) {
    Query<PerpetualTaskRecord> perpetualTaskRecordQuery = persistence.createQuery(PerpetualTaskRecord.class)
                                                              .field(PerpetualTaskRecordKeys.accountId)
                                                              .equal(accountId)
                                                              .field(PerpetualTaskRecordKeys.perpetualTaskType)
                                                              .equal(perpetualTaskType);

    if (clientContext.getClientId() != null) {
      perpetualTaskRecordQuery.field(PerpetualTaskRecordKeys.client_id).equal(clientContext.getClientId());
    } else if (clientContext.getClientParams() != null) {
      perpetualTaskRecordQuery.field(PerpetualTaskRecordKeys.client_params).equal(clientContext.getClientParams());
    }

    return Optional.ofNullable(perpetualTaskRecordQuery.get());
  }

  public boolean saveHeartbeat(String taskId, long heartbeatMillis, long failedExecutionCount) {
    // TODO: make sure that the heartbeat is coming from the right assignment. There is a race
    //       that could register heartbeat comming from wrong assignment
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.uuid, taskId)
                                           .filter(PerpetualTaskRecordKeys.state, TASK_ASSIGNED);

    UpdateOperations<PerpetualTaskRecord> taskUpdateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.lastHeartbeat, heartbeatMillis)
            .set(PerpetualTaskRecordKeys.failedExecutionCount, failedExecutionCount);
    UpdateResults update = persistence.update(query, taskUpdateOperations);
    return update.getUpdatedCount() > 0;
  }

  public void saveTaskFailureExceptionAndCount(String taskId, String exceptionMsg, long failedExecutionCount) {
    Query<PerpetualTaskRecord> query =
        persistence.createQuery(PerpetualTaskRecord.class).filter(PerpetualTaskRecordKeys.uuid, taskId);

    UpdateOperations<PerpetualTaskRecord> taskUpdateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.exception, exceptionMsg)
            .set(PerpetualTaskRecordKeys.failedExecutionCount, failedExecutionCount);
    persistence.update(query, taskUpdateOperations);
  }

  public void markAllTasksOnDelegateForReassignment(String accountId, String delegateId) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.accountId, accountId)
                                           .filter(PerpetualTaskRecordKeys.delegateId, delegateId);

    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.state, TASK_UNASSIGNED);
    persistence.update(query, updateOperations);
  }

  public void updateInvalidStateWithExceptions(
      PerpetualTaskRecord perpetualTaskRecord, PerpetualTaskUnassignedReason reason, String exception) {
    Query<PerpetualTaskRecord> query = persistence.createQuery(PerpetualTaskRecord.class)
                                           .filter(PerpetualTaskRecordKeys.uuid, perpetualTaskRecord.getUuid());
    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.unassignedReason, reason)
            .set(PerpetualTaskRecordKeys.state, TASK_INVALID)
            .set(PerpetualTaskRecordKeys.exception, exception);
    persistence.update(query, updateOperations);
  }

  public void updateTaskProcessed(String taskId, int assignTryCount) {
    Query<PerpetualTaskRecord> query =
        persistence.createQuery(PerpetualTaskRecord.class).filter(PerpetualTaskRecordKeys.uuid, taskId);
    UpdateOperations<PerpetualTaskRecord> updateOperations =
        persistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.assignAfterMs,
                System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(FibonacciBackOff.getFibonacciElement(
                        Math.min(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT, assignTryCount + 1))));
    persistence.update(query, updateOperations);
  }
}
