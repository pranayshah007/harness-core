/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.platform;

import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_EXECUTION_TIME;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_TIMEOUT;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.threading.Morpheus.sleep;
import static io.harness.utils.MemoryPerformanceUtils.memoryUsage;

import static software.wings.beans.TaskType.SCRIPT;
import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;

import static com.google.common.collect.ImmutableList.of;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.time.Duration.ofSeconds;

import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.service.common.AbstractDelegateAgentService;
import io.harness.delegate.service.common.DelegateTaskExecutionData;
import io.harness.logging.AutoLogContext;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.bash.BashScriptTask;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Precision;

@Singleton
@Slf4j
public class DelegatePlatformService extends AbstractDelegateAgentService {
  private final AtomicBoolean rejectRequest = new AtomicBoolean();

  private final AtomicInteger maxValidatingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingFuturesCount = new AtomicInteger();

  private final Map<String, DelegateTaskPackage> currentlyExecutingTasks = new ConcurrentHashMap<>();
  private final Map<String, DelegateTaskExecutionData> currentlyExecutingFutures = new ConcurrentHashMap<>();

  @Inject @Named("timeoutExecutor") private ThreadPoolExecutor timeoutEnforcement;

  @Inject private Injector injector;
  private TimeLimiter delegateTaskTimeLimiter;

  @Getter(lazy = true)
  private final ImmutableMap<String, ThreadPoolExecutor> logExecutors =
      NullSafeImmutableMap.<String, ThreadPoolExecutor>builder()
          .putIfNotNull("taskExecutor", getTaskExecutor())
          .putIfNotNull("timeoutEnforcement", timeoutEnforcement)
          .build();

  @Override
  protected void executeTask(@NonNull final DelegateTaskPackage delegateTaskPackage) {
    TaskData taskData = delegateTaskPackage.getData();

    log.debug("DelegateTask acquired - accountId: {}, taskType: {}", getDelegateConfiguration().getAccountId(),
        taskData.getTaskType());

    final TaskType taskType = TaskType.valueOf(taskData.getTaskType());
    if (taskType != SCRIPT && taskType != SHELL_SCRIPT_TASK_NG) {
      throw new IllegalArgumentException("PlatformDelegate can only take shel script tasks");
    }

    final BooleanSupplier preExecutionFunction = getPreExecutionFunction(delegateTaskPackage);
    final Consumer<DelegateTaskResponse> postExecutionFunction =
        response -> sendTaskResponse(delegateTaskPackage.getDelegateTaskId(), response);

    final BashScriptTask delegateRunnableTask =
        new BashScriptTask(delegateTaskPackage, preExecutionFunction, postExecutionFunction);

    injector.injectMembers(delegateRunnableTask);
    currentlyExecutingFutures.get(delegateTaskPackage.getDelegateTaskId()).setExecutionStartTime(getClock().millis());

    // Submit execution for watching this task execution.
    timeoutEnforcement.submit(() -> enforceDelegateTaskTimeout(delegateTaskPackage.getDelegateTaskId(), taskData));

    // Start task execution in same thread and measure duration.
    if (getDelegateConfiguration().isImmutable()) {
      getMetricRegistry().recordGaugeDuration(
          TASK_EXECUTION_TIME, new String[] {DELEGATE_NAME, taskData.getTaskType()}, delegateRunnableTask);
    } else {
      delegateRunnableTask.run();
    }
  }

  private BooleanSupplier getPreExecutionFunction(@NotNull DelegateTaskPackage delegateTaskPackage) {
    return () -> {
      if (!currentlyExecutingTasks.containsKey(delegateTaskPackage.getDelegateTaskId())) {
        log.debug("Adding task to executing tasks");
        currentlyExecutingTasks.put(delegateTaskPackage.getDelegateTaskId(), delegateTaskPackage);
        updateCounterIfLessThanCurrent(maxExecutingTasksCount, currentlyExecutingTasks.size());
        return true;
      } else {
        // We should have already checked this before acquiring this task. If we here, than we
        // should log an error and abort execution.
        log.error("Task is already being executed");
        return false;
      }
    };
  }

  private void enforceDelegateTaskTimeout(String taskId, TaskData taskData) {
    long startingTime = currentlyExecutingFutures.get(taskId).getExecutionStartTime();
    boolean stillRunning = true;
    long timeout = taskData.getTimeout() + TimeUnit.SECONDS.toMillis(30L);
    Future<?> taskFuture = null;
    while (stillRunning && getClock().millis() - startingTime < timeout) {
      log.info("Task time remaining for {}, taskype {}: {} ms", taskId, taskData.getTaskType(),
          startingTime + timeout - getClock().millis());
      sleep(ofSeconds(5));
      taskFuture = currentlyExecutingFutures.get(taskId).getTaskFuture();
      if (taskFuture != null) {
        log.info("Task future: {} - done:{}, cancelled:{}", taskId, taskFuture.isDone(), taskFuture.isCancelled());
      }
      stillRunning = taskFuture != null && !taskFuture.isDone() && !taskFuture.isCancelled();
    }
    if (stillRunning) {
      log.error("Task {} of taskType {} timed out after {} milliseconds", taskId, taskData.getTaskType(), timeout);
      getMetricRegistry().recordGaugeInc(TASK_TIMEOUT, new String[] {DELEGATE_NAME, taskData.getTaskType()});
      Optional.ofNullable(currentlyExecutingFutures.get(taskId).getTaskFuture())
          .ifPresent(future -> future.cancel(true));
    }
    if (taskFuture != null) {
      try {
        HTimeLimiter.callInterruptible21(delegateTaskTimeLimiter, Duration.ofSeconds(5), taskFuture::get);
      } catch (UncheckedTimeoutException e) {
        ignoredOnPurpose(e);
        log.error("Timed out getting task future");
      } catch (CancellationException e) {
        ignoredOnPurpose(e);
        log.error("Task {} was cancelled", taskId);
      } catch (Exception e) {
        log.error("Error from task future {}", taskId, e);
      }
    }
    currentlyExecutingTasks.remove(taskId);
    if (currentlyExecutingFutures.remove(taskId) != null) {
      log.info("Removed {} from executing futures on timeout", taskId);
    }
  }

  @Override
  protected void abortTask(DelegateTaskAbortEvent delegateTaskEvent) {
    log.info("Aborting task {}", delegateTaskEvent);

    Optional.ofNullable(currentlyExecutingFutures.get(delegateTaskEvent.getDelegateTaskId()).getTaskFuture())
        .ifPresent(future -> future.cancel(true));
    currentlyExecutingTasks.remove(delegateTaskEvent.getDelegateTaskId());
    if (currentlyExecutingFutures.remove(delegateTaskEvent.getDelegateTaskId()) != null) {
      log.info("Removed from executing futures on abort");
    }
  }

  @Override
  protected boolean onPreExecute(final DelegateTaskEvent delegateTaskEvent, final String delegateTaskId) {
    if (rejectRequest.get()) {
      log.info("Delegate running out of resources, dropping this request [{}] " + delegateTaskId);
      return true;
    }

    if (currentlyExecutingFutures.containsKey(delegateTaskId)) {
      log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected void onPreResponseSent(final DelegateTaskResponse response) {}

  @Override
  protected void onResponseSent(final String taskId) {
    currentlyExecutingTasks.remove(taskId);
    if (currentlyExecutingFutures.remove(taskId) != null) {
      log.debug("Removed from executing futures on post execution");
    }
  }

  @Override
  protected void onPostExecute(final String delegateTaskId, final Future<?> taskFuture) {
    updateCounterIfLessThanCurrent(maxExecutingFuturesCount, currentlyExecutingFutures.size());
    final DelegateTaskExecutionData taskExecutionData =
        DelegateTaskExecutionData.builder().taskFuture(taskFuture).build();
    currentlyExecutingFutures.put(delegateTaskId, taskExecutionData);
  }

  @Override
  protected ImmutableList<String> getCurrentlyExecutingTaskIds() {
    return currentlyExecutingTasks.values()
        .stream()
        .map(DelegateTaskPackage::getDelegateTaskId)
        .collect(toImmutableList());
  }

  @Override
  protected ImmutableList<TaskType> getSupportedTasks() {
    return of(SCRIPT, SHELL_SCRIPT_TASK_NG);
  }

  @Override
  protected void onDelegateStart() {
    delegateTaskTimeLimiter = HTimeLimiter.create(getTaskExecutor());
  }

  @Override
  protected void onDelegateRegistered() {}

  @Override
  protected void onHeartbeat() {
    // Log delegate performance after every 60 sec i.e. heartbeat interval.
    logCurrentTasks();
  }

  @Override
  protected void onPostExecute(final String delegateTaskId) {
    currentlyExecutingFutures.remove(delegateTaskId);
  }

  private void updateCounterIfLessThanCurrent(AtomicInteger counter, int current) {
    counter.updateAndGet(value -> Math.max(value, current));
  }

  private void logCurrentTasks() {
    try (AutoLogContext ignore = new AutoLogContext(obtainPerformance(), OVERRIDE_NESTS)) {
      log.info("Current performance");
    }
  }

  private Map<String, String> obtainPerformance() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put("maxValidatingTasksCount", Integer.toString(maxValidatingTasksCount.getAndSet(0)));
    builder.put("maxExecutingTasksCount", Integer.toString(maxExecutingTasksCount.getAndSet(0)));
    builder.put("maxExecutingFuturesCount", Integer.toString(maxExecutingFuturesCount.getAndSet(0)));

    OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    builder.put("cpu-process", Double.toString(Precision.round(osBean.getProcessCpuLoad() * 100, 2)));
    builder.put("cpu-system", Double.toString(Precision.round(osBean.getSystemCpuLoad() * 100, 2)));

    for (Map.Entry<String, ThreadPoolExecutor> executorEntry : getLogExecutors().entrySet()) {
      builder.put(executorEntry.getKey(), Integer.toString(executorEntry.getValue().getActiveCount()));
    }
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    memoryUsage(builder, "heap-", memoryMXBean.getHeapMemoryUsage());

    memoryUsage(builder, "non-heap-", memoryMXBean.getNonHeapMemoryUsage());

    return builder.build();
  }
}
