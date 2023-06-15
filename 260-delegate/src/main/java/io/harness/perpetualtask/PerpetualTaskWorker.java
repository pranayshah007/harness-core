/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.service.DelegateAgentServiceImpl.getDelegateId;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.flow.BackoffScheduler;
import io.harness.logging.AutoLogContext;
import io.harness.logging.DelayLogContext;
import io.harness.logging.LoggingListener;
import io.harness.threading.Schedulable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@Slf4j
@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class PerpetualTaskWorker {
  private static final Marker THROTTLED = MarkerFactory.getMarker("THROTTLED");
  @Getter private final Map<PerpetualTaskId, PerpetualTaskAssignRecord> runningTaskMap = new ConcurrentHashMap<>();

  private final TimeLimiter perpetualTaskTimeLimiter;
  private final ScheduledExecutorService perpetualTaskTimeoutExecutor;

  private final AtomicBoolean firstFillUp = new AtomicBoolean(true);
  private final BackoffScheduler backoffScheduler;
  private final PerpetualTaskServiceAgentClient perpetualTaskServiceAgentClient;
  private final Map<String, PerpetualTaskExecutor> factoryMap;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<PerpetualTaskWorkerService> svcHolder = new AtomicReference<>();
  private String accountId;
  @Getter private final AtomicInteger currentlyExecutingPerpetualTasksCount = new AtomicInteger();

  private class PerpetualTaskWorkerService extends AbstractScheduledService {
    PerpetualTaskWorkerService() {
      addListener(new LoggingListener(this), MoreExecutors.directExecutor());
    }

    @Override
    protected void runOneIteration() {
      handleTasks();
    }

    @Override
    protected Scheduler scheduler() {
      return backoffScheduler;
    }
  }

  @Inject
  public PerpetualTaskWorker(PerpetualTaskServiceAgentClient perpetualTaskServiceAgentClient,
      Map<String, PerpetualTaskExecutor> factoryMap, @Named("taskExecutor") ThreadPoolExecutor perpetualTaskExecutor,
      @Named("perpetualTaskTimeoutExecutor") ScheduledExecutorService perpetualTaskTimeoutExecutor) {
    this.perpetualTaskServiceAgentClient = perpetualTaskServiceAgentClient;
    this.factoryMap = factoryMap;
    this.perpetualTaskTimeLimiter = HTimeLimiter.create(perpetualTaskExecutor);
    this.perpetualTaskTimeoutExecutor = perpetualTaskTimeoutExecutor;
    backoffScheduler = new BackoffScheduler(getClass().getSimpleName(), Duration.ofMinutes(4), Duration.ofMinutes(14));
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  private void handleTasks() {
    try {
      List<PerpetualTaskAssignDetails> assignedTasks = fetchAssignedTask();
      Set<PerpetualTaskId> stopTasks = new HashSet<>();
      List<PerpetualTaskAssignDetails> startTasks = new ArrayList<>();
      List<PerpetualTaskAssignDetails> updatedTasks = new ArrayList<>();
      synchronized (runningTaskMap) {
        splitTasks(runningTaskMap, assignedTasks, stopTasks, startTasks, updatedTasks);
      }

      for (PerpetualTaskId taskId : stopTasks) {
        log.info("Stopping the task with id: {}", taskId.getId());
        stopTask(taskId);
      }

      for (PerpetualTaskAssignDetails task : startTasks) {
        if (!firstFillUp.get()) {
          logPullDelay(task, "first poll from this delegate for task");
        }
        startTask(task);
      }
      firstFillUp.set(false);

      for (PerpetualTaskAssignDetails task : updatedTasks) {
        logPullDelay(task, "update for task");
        stopTask(task.getTaskId());
        startTask(task);
      }

      backoffScheduler.recordSuccess();
    } catch (StatusRuntimeException ex) {
      if (ex.getStatus().getCode() == Status.Code.UNAVAILABLE
          || ex.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
        log.debug(THROTTLED, "Grpc status time out exception in perpetual task worker for account:{}. Backing off...",
            accountId, ex);
      } else {
        log.error(
            THROTTLED, "Grpc status exception in perpetual task worker for account:{}. Backing off...", accountId, ex);
        backoffScheduler.recordFailure();
      }
    } catch (Exception ex) {
      log.error("Exception in perpetual task worker ", ex);
    }
  }

  private void logPullDelay(PerpetualTaskAssignDetails task, String message) {
    long lastContextUpdated = Timestamps.toMillis(task.getLastContextUpdated());
    long startTime = currentTimeMillis();
    long delay = startTime - lastContextUpdated;

    try (DelayLogContext ignore = new DelayLogContext(delay, OVERRIDE_ERROR)) {
      log.info(message);
    }
  }

  protected void splitTasks(Map<PerpetualTaskId, PerpetualTaskAssignRecord> runningTaskMap,
      List<PerpetualTaskAssignDetails> assignedTasks, Set<PerpetualTaskId> stopTasks,
      List<PerpetualTaskAssignDetails> startTasks, List<PerpetualTaskAssignDetails> updatedTasks) {
    stopTasks.addAll(runningTaskMap.keySet());

    for (PerpetualTaskAssignDetails assignDetails : assignedTasks) {
      if (!stopTasks.remove(assignDetails.getTaskId())) {
        startTasks.add(assignDetails);
      } else {
        PerpetualTaskAssignRecord runningTask = runningTaskMap.get(assignDetails.getTaskId());
        long runningTaskLastContextUpdated =
            Timestamps.toMillis(runningTask.getPerpetualTaskAssignDetails().getLastContextUpdated());
        long assignDetailsLastContextUpdated = Timestamps.toMillis(assignDetails.getLastContextUpdated());
        if (runningTaskLastContextUpdated < assignDetailsLastContextUpdated) {
          updatedTasks.add(assignDetails);
        }
      }
    }
  }

  List<PerpetualTaskAssignDetails> fetchAssignedTask() {
    String delegateId = getDelegateId().orElse("UNREGISTERED");
    if (accountId == null || delegateId.equals("UNREGISTERED")) {
      log.warn("While fetching Assigned PT tasks, Account id is {} and delegateId is {}", accountId, delegateId);
    }
    List<PerpetualTaskAssignDetails> assignedTasks =
        perpetualTaskServiceAgentClient.perpetualTaskList(delegateId, accountId);
    if (isNotEmpty(assignedTasks)) {
      List<String> taskIdList = assignedTasks.stream().map(at -> at.getTaskId().getId()).collect(Collectors.toList());
      log.info("Refreshed list of assigned perpetual tasks for accountId {}, {} ", accountId, taskIdList);
    } else {
      log.info("Refreshed list of assigned perpetual tasks for accountId {} is empty ", accountId);
    }
    return assignedTasks;
  }

  @VisibleForTesting
  void startTask(PerpetualTaskAssignDetails task) {
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(task.getTaskId().getId(), OVERRIDE_ERROR)) {
      PerpetualTaskExecutionContext context =
          perpetualTaskServiceAgentClient.perpetualTaskContext(task.getTaskId(), accountId);
      PerpetualTaskSchedule schedule = context.getTaskSchedule();
      long intervalSeconds = Durations.toSeconds(schedule.getInterval());

      PerpetualTaskLifecycleManager perpetualTaskLifecycleManager =
          new PerpetualTaskLifecycleManager(task.getTaskId(), context, factoryMap, perpetualTaskServiceAgentClient,
              perpetualTaskTimeLimiter, currentlyExecutingPerpetualTasksCount, accountId);

      synchronized (runningTaskMap) {
        runningTaskMap.computeIfAbsent(task.getTaskId(), k -> {
          log.info("Starting perpetual task with id: {}.", task.getTaskId().getId());
          ScheduledFuture<?> taskHandle = perpetualTaskTimeoutExecutor.scheduleWithFixedDelay(
              new Schedulable("Throwable while executing perpetual task", perpetualTaskLifecycleManager::startTask),
              new SecureRandom().nextInt(120), intervalSeconds, TimeUnit.SECONDS);

          PerpetualTaskHandle perpetualTaskHandle = new PerpetualTaskHandle(taskHandle, perpetualTaskLifecycleManager);

          return PerpetualTaskAssignRecord.builder()
              .perpetualTaskHandle(perpetualTaskHandle)
              .perpetualTaskAssignDetails(task)
              .build();
        });
      }
    } catch (Exception ex) {
      log.error("Exception in starting perpetual task id " + task.getTaskId(), ex);
    }
  }

  @VisibleForTesting
  void stopTask(PerpetualTaskId taskId) {
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId.getId(), OVERRIDE_ERROR)) {
      PerpetualTaskAssignRecord perpetualTaskAssignRecord;
      synchronized (runningTaskMap) {
        perpetualTaskAssignRecord = runningTaskMap.get(taskId);
        if (perpetualTaskAssignRecord == null) {
          return;
        }
        runningTaskMap.remove(taskId);
      }
      log.info("Stopping perpetual task with id: {}.", taskId.getId());
      perpetualTaskAssignRecord.getPerpetualTaskHandle().getTaskLifecycleManager().stopTask();
      perpetualTaskAssignRecord.getPerpetualTaskHandle().getTaskHandle().cancel(true);
    }
  }

  public void updateTasks() {
    handleTasks();
  }

  public void start() {
    if (running.compareAndSet(false, true)) {
      PerpetualTaskWorkerService perpetualTaskWorkerService = new PerpetualTaskWorkerService();
      perpetualTaskWorkerService.startAsync();
      this.svcHolder.set(perpetualTaskWorkerService);
    }
  }
  public void stop() {
    if (running.compareAndSet(true, false)) {
      PerpetualTaskWorkerService perpetualTaskWorkerService = this.svcHolder.get();
      perpetualTaskWorkerService.stopAsync().awaitTerminated();
      while (true) {
        synchronized (runningTaskMap) {
          Iterator<PerpetualTaskId> iterator = runningTaskMap.keySet().iterator();
          if (!iterator.hasNext()) {
            break;
          }
          stopTask(iterator.next());
        }
      }
    }
  }
}
