/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.delegate.message.ManagerMessageConstants.UPDATE_PERPETUAL_TASK;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_ASSIGNED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_CREATE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_DELETE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_NONASSIGNABLE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_PAUSE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_RESET;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASK_UNASSIGNED;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_NON_ASSIGNABLE;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_UNASSIGNED;

import static software.wings.beans.TaskType.PT_SERIALIZATION_SUPPORT;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.perpetualtask.PerpetualTaskScheduleConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.grpc.auth.DelegateAuthServerInterceptor;
import io.harness.grpc.utils.HTimestamps;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;
import io.harness.reflection.ReflectionUtils;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.service.intfc.PerpetualTaskStateObserver;

import software.wings.app.MainConfiguration;
import software.wings.beans.PerpetualTaskBroadcastEvent;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.perpetualtask.PerpetualTaskCrudObserver;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.Durations;
import io.grpc.Context;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.atmosphere.cpr.BroadcasterFactory;
import org.eclipse.jetty.util.ConcurrentHashSet;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class PerpetualTaskServiceImpl implements PerpetualTaskService, DelegateObserver {
  private Set<Pair<String, String>> broadcastAggregateSet = new ConcurrentHashSet<>();

  private PerpetualTaskRecordDao perpetualTaskRecordDao;
  private PerpetualTaskServiceClientRegistry clientRegistry;
  private final BroadcasterFactory broadcasterFactory;
  private final PerpetualTaskScheduleService perpetualTaskScheduleService;
  private static final int TASK_FAILED_EXECUTION_LIMIT = 5;
  private static final int MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT = 5;

  @Inject private MainConfiguration mainConfiguration;
  @Inject private RemoteObserverInformer remoteObserverInformer;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject
  public PerpetualTaskServiceImpl(PerpetualTaskRecordDao perpetualTaskRecordDao,
      PerpetualTaskServiceClientRegistry clientRegistry, BroadcasterFactory broadcasterFactory,
      PerpetualTaskScheduleService perpetualTaskScheduleService) {
    this.perpetualTaskRecordDao = perpetualTaskRecordDao;
    this.clientRegistry = clientRegistry;
    this.broadcasterFactory = broadcasterFactory;
    this.perpetualTaskScheduleService = perpetualTaskScheduleService;
  }

  @Getter private Subject<PerpetualTaskCrudObserver> perpetualTaskCrudSubject = new Subject<>();
  @Getter private Subject<PerpetualTaskStateObserver> perpetualTaskStateObserverSubject = new Subject<>();

  @Override
  public void appointDelegate(String accountId, String taskId, String delegateId, long lastContextUpdated) {
    perpetualTaskRecordDao.appointDelegate(taskId, delegateId, lastContextUpdated);

    broadcastAggregateSet.add(Pair.of(accountId, delegateId));

    // Both subject and remote Observer are needed since in few places DMS might not be present
    perpetualTaskStateObserverSubject.fireInform(
        PerpetualTaskStateObserver::onPerpetualTaskAssigned, accountId, taskId, delegateId);
    remoteObserverInformer.sendEvent(ReflectionUtils.getMethod(PerpetualTaskStateObserver.class,
                                         "onPerpetualTaskAssigned", String.class, String.class, String.class),
        PerpetualTaskServiceImpl.class, accountId, taskId, delegateId);

    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);
    if (perpetualTaskRecord != null) {
      String perpetualTaskType = perpetualTaskRecord.getPerpetualTaskType();
      delegateMetricsService.recordPerpetualTaskMetrics(accountId, perpetualTaskType, PERPETUAL_TASK_ASSIGNED);
    }
  }

  public void broadcastToDelegate() {
    Set<Pair<String, String>> sendingHashSet;
    synchronized (broadcastAggregateSet) {
      sendingHashSet = new HashSet<>(broadcastAggregateSet);
      broadcastAggregateSet.clear();
    }
    sendingHashSet.forEach(setEntry
        -> broadcasterFactory.lookup(DelegateTaskBroadcastHelper.STREAM_DELEGATE_PATH + setEntry.getLeft(), true)
               .broadcast(PerpetualTaskBroadcastEvent.builder()
                              .eventType(UPDATE_PERPETUAL_TASK)
                              .broadcastDelegateId(setEntry.getRight())
                              .build()));
  }

  @Override
  public String createTask(String perpetualTaskType, String accountId, PerpetualTaskClientContext clientContext,
      PerpetualTaskSchedule schedule, boolean allowDuplicate, String taskDescription) {
    return createPerpetualTaskInternal(
        perpetualTaskType, accountId, clientContext, schedule, allowDuplicate, taskDescription);
  }

  @Override
  public String createPerpetualTaskInternal(String perpetualTaskType, String accountId,
      PerpetualTaskClientContext clientContext, PerpetualTaskSchedule schedule, boolean allowDuplicate,
      String taskDescription) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (!allowDuplicate) {
        Optional<PerpetualTaskRecord> perpetualTaskMaybe =
            perpetualTaskRecordDao.getExistingPerpetualTask(accountId, perpetualTaskType, clientContext);
        if (perpetualTaskMaybe.isPresent()) {
          PerpetualTaskRecord perpetualTaskRecord = perpetualTaskMaybe.get();
          log.info("Perpetual task with id={} exists.", perpetualTaskRecord.getUuid());
          return perpetualTaskRecord.getUuid();
        }
      }

      PerpetualTaskRecord record =
          PerpetualTaskRecord.builder()
              .accountId(accountId)
              .perpetualTaskType(perpetualTaskType)
              .clientContext(clientContext)
              .timeoutMillis(Durations.toMillis(schedule.getTimeout()))
              .intervalSeconds(getTaskTimeInterval(schedule, accountId, perpetualTaskType))
              .delegateId("")
              .state(PerpetualTaskState.TASK_UNASSIGNED)
              .assignIteration(currentTimeMillis())
              .taskDescription(taskDescription)
              .referenceFalseKryoSerializer(
                  delegateTaskService.isTaskTypeSupportedByAllDelegates(accountId, PT_SERIALIZATION_SUPPORT.name()))
              .build();

      perpetualTaskCrudSubject.fireInform(PerpetualTaskCrudObserver::onPerpetualTaskCreated);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(PerpetualTaskCrudObserver.class, "onPerpetualTaskCreated"),
          PerpetualTaskServiceImpl.class);
      String taskId = perpetualTaskRecordDao.save(record);
      try (AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId, OVERRIDE_ERROR)) {
        log.info("Created a perpetual task with id={}.", taskId);
      }
      delegateMetricsService.recordPerpetualTaskMetrics(accountId, perpetualTaskType, PERPETUAL_TASK_CREATE);
      return taskId;
    }
  }

  @Override
  public boolean resetTask(String accountId, String taskId, PerpetualTaskExecutionBundle taskExecutionBundle) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId, OVERRIDE_ERROR)) {
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);
      if (perpetualTaskRecord != null) {
        String perpetualTaskType = perpetualTaskRecord.getPerpetualTaskType();
        log.info("Resetting the perpetual task {}, type: {}", taskId, perpetualTaskType);
        delegateMetricsService.recordPerpetualTaskMetrics(accountId, perpetualTaskType, PERPETUAL_TASK_RESET);
      }
      return perpetualTaskRecordDao.resetDelegateIdForTask(accountId, taskId, taskExecutionBundle);
    }
  }

  @Override
  public long updateTasksSchedule(String accountId, String perpetualTaskType, long intervalInMillis) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Updating task schedule for perpetual task type: {}", perpetualTaskType);
      return perpetualTaskRecordDao.updateTasksSchedule(accountId, perpetualTaskType, intervalInMillis);
    }
  }

  @Override
  public boolean deleteTask(String accountId, String taskId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId, OVERRIDE_ERROR)) {
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);
      if (perpetualTaskRecord != null) {
        String perpetualTaskType = perpetualTaskRecord.getPerpetualTaskType();
        delegateMetricsService.recordPerpetualTaskMetrics(accountId, perpetualTaskType, PERPETUAL_TASK_DELETE);
      }

      boolean hasDeleted = perpetualTaskRecordDao.remove(accountId, taskId);
      if (hasDeleted) {
        log.info("Deleted the perpetual task {} ", taskId);
      }
      return hasDeleted;
    }
  }

  @Override
  public boolean pauseTask(String accountId, String taskId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId, OVERRIDE_ERROR)) {
      log.info("Pausing the perpetual task, {}", taskId);
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);
      if (perpetualTaskRecord != null) {
        String perpetualTaskType = perpetualTaskRecord.getPerpetualTaskType();
        delegateMetricsService.recordPerpetualTaskMetrics(accountId, perpetualTaskType, PERPETUAL_TASK_PAUSE);
      }
      return perpetualTaskRecordDao.pauseTask(accountId, taskId);
    }
  }

  @Override
  public boolean resumeTask(String accountId, String taskId) {
    return resetTask(accountId, taskId, null);
  }

  @Override
  public boolean deleteAllTasksForAccount(String accountId) {
    return perpetualTaskRecordDao.removeAllTasksForAccount(accountId);
  }

  @Override
  public List<PerpetualTaskAssignDetails> listAssignedTasks(String delegateId) {
    String accountId = DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY.get(Context.current());
    return listAssignedTasks(delegateId, accountId);
  }

  @Override
  public List<PerpetualTaskAssignDetails> listAssignedTasks(String delegateId, String accountId) {
    List<PerpetualTaskRecord> taskRecords = perpetualTaskRecordDao.listAssignedTasks(delegateId, accountId);

    return taskRecords.stream()
        .map(task
            -> PerpetualTaskAssignDetails.newBuilder()
                   .setTaskId(PerpetualTaskId.newBuilder().setId(task.getUuid()))
                   .setLastContextUpdated(HTimestamps.fromMillis(task.getClientContext().getLastContextUpdated()))
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public List<PerpetualTaskRecord> listAllTasksForAccount(String accountId) {
    return perpetualTaskRecordDao.listAllPerpetualTasksForAccount(accountId);
  }

  @Override
  public PerpetualTaskRecord getTaskRecord(String taskId) {
    return perpetualTaskRecordDao.getTask(taskId);
  }

  @Override
  public String getPerpetualTaskType(String taskId) {
    PerpetualTaskRecord perpetualTaskRecord = getTaskRecord(taskId);
    return perpetualTaskRecord.getPerpetualTaskType();
  }

  @Override
  public PerpetualTaskExecutionContext perpetualTaskContext(String taskId, boolean referenceFalseKryoSerializer) {
    log.info("Getting perpetual task context for task with id: {}", taskId);
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);

    PerpetualTaskExecutionParams params = null;
    try {
      params = getTaskParams(perpetualTaskRecord, referenceFalseKryoSerializer);
    } catch (Exception e) {
      log.error("Error while fetching perpetual task context task params ", e);
      perpetualTaskRecordDao.updateInvalidStateWithExceptions(
          perpetualTaskRecord, PerpetualTaskUnassignedReason.PT_TASK_FAILED, ExceptionUtils.getMessage(e));
      return null;
    }

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromSeconds(perpetualTaskRecord.getIntervalSeconds()))
                                         .setTimeout(Durations.fromMillis(perpetualTaskRecord.getTimeoutMillis()))
                                         .build();

    return PerpetualTaskExecutionContext.newBuilder()
        .setTaskParams(params)
        .setTaskSchedule(schedule)
        .setHeartbeatTimestamp(HTimestamps.fromMillis(perpetualTaskRecord.getLastHeartbeat()))
        .build();
  }

  private PerpetualTaskExecutionParams getTaskParams(
      PerpetualTaskRecord perpetualTaskRecord, boolean referenceFalseKryoSerializer) throws Exception {
    Message perpetualTaskParams = null;

    if (perpetualTaskRecord.getClientContext().getClientParams() != null) {
      PerpetualTaskServiceClient client = clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType());
      perpetualTaskParams = client.getTaskParams(perpetualTaskRecord.getClientContext(), referenceFalseKryoSerializer);

      return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(perpetualTaskParams)).build();
    } else {
      PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = null;
      try {
        perpetualTaskExecutionBundle =
            PerpetualTaskExecutionBundle.parseFrom(perpetualTaskRecord.getClientContext().getExecutionBundle());
      } catch (InvalidProtocolBufferException e) {
        log.error("Failed to parse perpetual task execution bundle from task parameters", e);
        return null;
      }

      return PerpetualTaskExecutionParams.newBuilder()
          .setReferenceFalseKryoSerializer(perpetualTaskRecord.isReferenceFalseKryoSerializer())
          .setCustomizedParams(perpetualTaskExecutionBundle.getTaskParams())
          .build();
    }
  }

  // TODO: ARPIT remove this method and heartbeat once changes for delegate agent goes in immutable delegate DEL-5026
  @Override
  public boolean triggerCallback(String taskId, long heartbeatMillis, PerpetualTaskResponse perpetualTaskResponse) {
    int responseCode = perpetualTaskResponse.getResponseCode();
    long taskFailedExecutionCount = 0;
    if (responseCode == 500) {
      PerpetualTaskRecord taskRecord = perpetualTaskRecordDao.getTask(taskId);
      taskFailedExecutionCount = taskRecord.getFailedExecutionCount();
      taskFailedExecutionCount++;
      boolean saveHeartbeat = perpetualTaskRecordDao.saveHeartbeat(taskId, heartbeatMillis, taskFailedExecutionCount);
      if (taskFailedExecutionCount >= TASK_FAILED_EXECUTION_LIMIT) {
        perpetualTaskRecordDao.updateTaskStateNonAssignableReason(taskRecord.getUuid(),
            PerpetualTaskUnassignedReason.MULTIPLE_FAILED_PERPETUAL_TASK, taskRecord.getAssignTryCount(),
            TASK_NON_ASSIGNABLE);
      }
      return saveHeartbeat;
    }
    return perpetualTaskRecordDao.saveHeartbeat(taskId, heartbeatMillis, taskFailedExecutionCount);
  }

  public void recordTaskFailure(String taskId, String exceptionMessage) {
    PerpetualTaskRecord taskRecord = perpetualTaskRecordDao.getTask(taskId);
    long taskFailedExecutionCount = taskRecord.getFailedExecutionCount();
    taskFailedExecutionCount++;
    if (taskFailedExecutionCount >= TASK_FAILED_EXECUTION_LIMIT) {
      perpetualTaskRecordDao.updateTaskStateNonAssignableReason(taskRecord.getUuid(),
          PerpetualTaskUnassignedReason.MULTIPLE_FAILED_PERPETUAL_TASK, taskRecord.getAssignTryCount(),
          TASK_NON_ASSIGNABLE);
      taskFailedExecutionCount = 0;
    }
    perpetualTaskRecordDao.saveTaskFailureExceptionAndCount(taskId, exceptionMessage, taskFailedExecutionCount);
  }

  @Override
  public void updateTaskUnassignedReason(String taskId, PerpetualTaskUnassignedReason reason, int assignTryCount) {
    perpetualTaskRecordDao.updateTaskUnassignedReason(taskId, reason, assignTryCount);

    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);
    if (perpetualTaskRecord != null) {
      String accountId = perpetualTaskRecord.getAccountId();
      String perpetualTaskType = perpetualTaskRecord.getPerpetualTaskType();
      delegateMetricsService.recordPerpetualTaskMetrics(accountId, perpetualTaskType, PERPETUAL_TASK_UNASSIGNED);
    }
  }

  @Override
  public void markStateAndNonAssignedReason_OnAssignTryCount(PerpetualTaskRecord perpetualTaskRecord,
      PerpetualTaskUnassignedReason reason, PerpetualTaskState perpetualTaskState, String exception) {
    if (perpetualTaskRecord.getAssignTryCount() < MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT) {
      // if count is below max try, then set state as TASK_UNASSIGNED
      perpetualTaskRecordDao.updateTaskUnassignedReasonAndException(
          perpetualTaskRecord.getUuid(), reason, perpetualTaskRecord.getAssignTryCount(), exception);
      delegateMetricsService.recordPerpetualTaskMetrics(
          perpetualTaskRecord.getAccountId(), perpetualTaskRecord.getPerpetualTaskType(), PERPETUAL_TASK_UNASSIGNED);
    } else {
      // use state passed as a param here. It can be TASK_NON_ASSIGNABLE OR TASK_INVALID
      perpetualTaskRecordDao.updateTaskStateNonAssignableReasonAndException(perpetualTaskRecord.getUuid(), reason,
          perpetualTaskRecord.getAssignTryCount(), perpetualTaskState, exception);
      delegateMetricsService.recordPerpetualTaskMetrics(
          perpetualTaskRecord.getAccountId(), perpetualTaskRecord.getPerpetualTaskType(), PERPETUAL_TASK_NONASSIGNABLE);
    }
  }

  @Override
  public void setTaskUnassigned(String taskId) {
    perpetualTaskRecordDao.setTaskUnassigned(taskId);
  }

  @Override
  public void onAdded(Delegate delegate) {
    // do nothing
  }

  @Override
  public void onDisconnected(String accountId, String delegateId) {
    perpetualTaskRecordDao.markAllTasksOnDelegateForReassignment(accountId, delegateId);
  }

  @Override
  public void onReconnected(Delegate delegate) {
    log.info("Delegate reconnected/added for account {} delegateId {}", delegate.getAccountId(), delegate.getUuid());
    perpetualTaskRecordDao.updateTaskNonAssignableToAssignable(delegate.getAccountId());
  }

  @Override
  public void onDelegateTagsUpdated(String accountId) {
    log.info(
        "Marking all the {} perpetual tasks as {} for accountId: {}", TASK_NON_ASSIGNABLE, TASK_UNASSIGNED, accountId);
    perpetualTaskRecordDao.updateTaskNonAssignableToAssignable(accountId);
  }

  @VisibleForTesting
  long getTaskTimeInterval(PerpetualTaskSchedule schedule, String accountId, String perpetualTaskType) {
    long intervalSeconds = schedule.getInterval().getSeconds();

    PerpetualTaskScheduleConfig perpetualTaskScheduleConfig =
        perpetualTaskScheduleService.getByAccountIdAndPerpetualTaskType(accountId, perpetualTaskType);
    if (perpetualTaskScheduleConfig != null) {
      intervalSeconds = perpetualTaskScheduleConfig.getTimeIntervalInMillis() / 1000;
      log.info("Creating new perpetual task with custom time interval : {} for task type : {}",
          perpetualTaskScheduleConfig.getTimeIntervalInMillis(), perpetualTaskScheduleConfig.getPerpetualTaskType());
    }

    return intervalSeconds;
  }
}
