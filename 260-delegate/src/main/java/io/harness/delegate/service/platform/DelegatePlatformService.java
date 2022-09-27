/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.platform;

import static com.google.common.collect.ImmutableList.*;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_EXECUTION_TIME;
import static io.harness.delegate.metrics.DelegateMetricsConstants.TASK_TIMEOUT;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.REVOKED_TOKEN;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.logging.Misc.getDurationString;
import static io.harness.network.Localhost.getLocalHostAddress;
import static io.harness.threading.Morpheus.sleep;
import static io.harness.utils.MemoryPerformanceUtils.memoryUsage;

import static software.wings.beans.TaskType.SCRIPT;
import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.concurrent.HTimeLimiter;
import io.harness.configuration.DeployMode;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.logging.DelegateStackdriverLogAppender;
import io.harness.delegate.service.common.AbstractDelegateAgentServiceImpl;
import io.harness.delegate.service.common.DelegateTaskExecutionData;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.exception.UnexpectedException;
import io.harness.logging.AutoLogContext;
import io.harness.network.FibonacciBackOff;
import io.harness.rest.RestResponse;
import io.harness.security.TokenGenerator;
import io.harness.serializer.JsonUtils;
import io.harness.threading.Schedulable;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.bash.BashScriptTask;
import software.wings.misc.MemoryHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.http.client.utils.URIBuilder;
import org.asynchttpclient.AsyncHttpClient;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.transport.TransportNotSupported;
import retrofit2.Response;

@Singleton
@Slf4j
public class DelegatePlatformService extends AbstractDelegateAgentServiceImpl {
  private static final String DELEGATE_INSTANCE_ID = generateUuid();

  private final AtomicBoolean rejectRequest = new AtomicBoolean();

  private final AtomicInteger maxValidatingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingTasksCount = new AtomicInteger();
  private final AtomicInteger maxExecutingFuturesCount = new AtomicInteger();

  private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();
  private final Map<String, DelegateTaskPackage> currentlyValidatingTasks = new ConcurrentHashMap<>();
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
  public void stop() {
    log.info("Stopping delegate platform service, nothing to do!");
  }

  private void executeTask(@NotNull DelegateTaskPackage delegateTaskPackage) {
    TaskData taskData = delegateTaskPackage.getData();

    log.debug("DelegateTask acquired - accountId: {}, taskType: {}", getDelegateConfiguration().getAccountId(),
        taskData.getTaskType());

    final TaskType taskType = TaskType.valueOf(taskData.getTaskType());
    if (taskType != SCRIPT && taskType != SHELL_SCRIPT_TASK_NG) {
      throw new IllegalArgumentException("PlatformDelegate can only take shel script tasks");
    }

    final BooleanSupplier preExecutionFunction = getPreExecutionFunction(delegateTaskPackage);
    final Consumer<DelegateTaskResponse> postExecutionFunction =
        getPostExecutionFunction(delegateTaskPackage.getDelegateTaskId());

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

  private Consumer<DelegateTaskResponse> getPostExecutionFunction(String taskId) {
    return taskResponse -> {
      Response<ResponseBody> response = null;
      try {
        int retries = 5;
        for (int attempt = 0; attempt < retries; attempt++) {
          response = getDelegateAgentManagerClient()
                         .sendTaskStatus(DelegateAgentCommonVariables.getDelegateId(), taskId,
                             getDelegateConfiguration().getAccountId(), taskResponse)
                         .execute();
          if (response.code() >= 200 && response.code() <= 299) {
            log.info("Task {} response sent to manager", taskId);
            break;
          }
          log.warn("Failed to send response for task {}: {}. error: {}. requested url: {} {}", taskId, response.code(),
              response.errorBody() == null ? "null" : response.errorBody().string(), response.raw().request().url(),
              attempt < (retries - 1) ? "Retrying." : "Giving up.");
          if (attempt < retries - 1) {
            // Do not sleep for last loop round, as we are going to fail.
            sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
          }
        }
      } catch (Exception e) {
        log.error("Unable to send response to manager", e);
      } finally {
        currentlyExecutingTasks.remove(taskId);
        if (currentlyExecutingFutures.remove(taskId) != null) {
          log.debug("Removed from executing futures on post execution");
        }
        if (response != null && response.errorBody() != null && !response.isSuccessful()) {
          response.errorBody().close();
        }
        if (response != null && response.body() != null && response.isSuccessful()) {
          response.body().close();
        }
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
    currentlyValidatingTasks.remove(delegateTaskEvent.getDelegateTaskId());
    log.info("Removed from validating futures on abort");

    Optional.ofNullable(currentlyExecutingFutures.get(delegateTaskEvent.getDelegateTaskId()).getTaskFuture())
            .ifPresent(future -> future.cancel(true));
    currentlyExecutingTasks.remove(delegateTaskEvent.getDelegateTaskId());
    if (currentlyExecutingFutures.remove(delegateTaskEvent.getDelegateTaskId()) != null) {
      log.info("Removed from executing futures on abort");
    }
  }

  @Override
  protected void dispatchTaskAsync(DelegateTaskEvent delegateTaskEvent) {
    String delegateTaskId = delegateTaskEvent.getDelegateTaskId();
    if (delegateTaskId == null) {
      log.warn("Delegate task id cannot be null");
      return;
    }

    if (!shouldContactManager()) {
      log.info("Dropping task, self destruct in progress: " + delegateTaskId);
      return;
    }

    if (rejectRequest.get()) {
      log.info("Delegate running out of resources, dropping this request [{}] " + delegateTaskId);
      return;
    }

    if (currentlyExecutingFutures.containsKey(delegateTaskEvent.getDelegateTaskId())) {
      log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
      return;
    }

    DelegateTaskExecutionData taskExecutionData = DelegateTaskExecutionData.builder().build();
    if (currentlyExecutingFutures.putIfAbsent(delegateTaskId, taskExecutionData) == null) {
      final Future<?> taskFuture = getTaskExecutor().submit(() -> dispatchDelegateTask(delegateTaskEvent));
      log.info("TaskId: {} submitted for execution", delegateTaskId);
      taskExecutionData.setTaskFuture(taskFuture);
      updateCounterIfLessThanCurrent(maxExecutingFuturesCount, currentlyExecutingFutures.size());
      return;
    }

    log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
  }

  @Override
  protected ImmutableList<String> getCurrentlyExecutingTaskIds() {
    return currentlyExecutingTasks.values().stream().map(DelegateTaskPackage::getDelegateTaskId).collect(toImmutableList());
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

  private void dispatchDelegateTask(DelegateTaskEvent delegateTaskEvent) {
    try (TaskLogContext ignore = new TaskLogContext(delegateTaskEvent.getDelegateTaskId(), OVERRIDE_ERROR)) {
      log.info("DelegateTaskEvent received - {}", delegateTaskEvent);
      String delegateTaskId = delegateTaskEvent.getDelegateTaskId();

      try {
        if (getFrozen().get()) {
          log.info(
                  "Delegate process with detected time out of sync or with revoked token is running. Won't acquire tasks.");
          return;
        }

        if (!getAcquireTasks().get()) {
          log.info("[Old] Upgraded process is running. Won't acquire task while completing other tasks");
          return;
        }

        if (currentlyAcquiringTasks.contains(delegateTaskId)) {
          log.info("Task [DelegateTaskEvent: {}] currently acquiring. Don't acquire again", delegateTaskEvent);
          return;
        }

        if (currentlyValidatingTasks.containsKey(delegateTaskId)) {
          log.info("Task [DelegateTaskEvent: {}] already validating. Don't validate again", delegateTaskEvent);
          return;
        }

        currentlyAcquiringTasks.add(delegateTaskId);

        log.debug("Try to acquire DelegateTask - accountId: {}", getDelegateConfiguration().getAccountId());

        DelegateTaskPackage delegateTaskPackage =
                executeRestCall(getDelegateAgentManagerClient().acquireTask(DelegateAgentCommonVariables.getDelegateId(),
                        delegateTaskId, getDelegateConfiguration().getAccountId(), DELEGATE_INSTANCE_ID));
        if (delegateTaskPackage == null || delegateTaskPackage.getData() == null) {
          if (delegateTaskPackage == null) {
            log.warn("Delegate task package is null for task: {} - accountId: {}", delegateTaskId,
                    delegateTaskEvent.getAccountId());
          } else {
            log.warn("Delegate task data not available for task: {} - accountId: {}", delegateTaskId,
                    delegateTaskEvent.getAccountId());
          }
          return;
        } else {
          log.info("received task package {} for delegateInstance {}", delegateTaskPackage, DELEGATE_INSTANCE_ID);
        }

        if (isEmpty(delegateTaskPackage.getDelegateInstanceId())
                || DELEGATE_INSTANCE_ID.equals(delegateTaskPackage.getDelegateInstanceId())) {
          // Whitelisted. Proceed immediately.
          log.info("Delegate {} whitelisted for task and accountId: {}", DelegateAgentCommonVariables.getDelegateId(),
                  getDelegateConfiguration().getAccountId());
          executeTask(delegateTaskPackage);
        } else {
          throw new IllegalArgumentException("Delegate received a task intended for delegate with instanceId "
                  + delegateTaskPackage.getDelegateInstanceId());
        }

      } catch (IOException e) {
        log.error("Unable to get task for validation", e);
      } catch (Exception e) {
        log.error("Unable to execute task", e);
      } finally {
        currentlyAcquiringTasks.remove(delegateTaskId);
        currentlyExecutingFutures.remove(delegateTaskId);
      }
    }
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
