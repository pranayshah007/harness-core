/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logstreaming;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.COMMAND_UNIT_PLACEHOLDER;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogHelper.doneColoring;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.network.SafeHttpCall;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * There are certain assumptions for this client to work
 * 1. Each delegate task creates a separate task client
 * 2. the usage of this client is
 *    -> open stream
 *    -> write line
 *    -> close stream
 * concurrent usage of open and close stream will result in loss of logs
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Builder
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class LogStreamingTaskClient implements ILogStreamingTaskClient {
  private final DelegateLogService logService;
  private final LogStreamingClient logStreamingClient;
  private final LogStreamingSanitizer logStreamingSanitizer;
  private final ExecutorService taskProgressExecutor;
  private final String token;
  private final String accountId;
  private final String baseLogKey;
  private static ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(10,
      new ThreadFactoryBuilder().setNameFormat("log-streaming-client-%d").setPriority(Thread.NORM_PRIORITY).build());
  @Deprecated private final String appId;
  @Deprecated private final String activityId;
  private ScheduledFuture scheduledFuture;
  private final ITaskProgressClient taskProgressClient;

  @Default private final Map<String, List<LogLine>> logCache = new HashMap<>();

  private Set<String> markers;

  @Override
  public void openStream(String baseLogKeySuffix) {
    String logKey = getLogKey(baseLogKeySuffix);

    try {
      SafeHttpCall.executeWithExceptions(logStreamingClient.openLogStream(token, accountId, logKey));
    } catch (Exception ex) {
      log.error("Unable to open log stream for account {} and key {}", accountId, logKey, ex);
    }
    scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(this::dispatchLogs, 0, 100, TimeUnit.MILLISECONDS);
  }

  @Override
  public void closeStream(String baseLogKeySuffix) {
    String logKey = getLogKey(baseLogKeySuffix);

    synchronized (logCache) {
      // We can mark this task to be completed. Log upload can happen asynchronously.
      scheduledExecutorService.submit(() -> closeStreamAsync(logKey));
    }
  }

  private void closeStreamAsync(String logKey) {
    // Waiting for finite time allow log upload to finish.
    long startTime = currentTimeMillis();
    synchronized (logCache) {
      while (logCache.containsKey(logKey) && currentTimeMillis() < startTime + TimeUnit.SECONDS.toMillis(5)) {
        log.debug("For {} the logs are not drained yet. sleeping...", logKey);
        try {
          logCache.wait(100);
        } catch (InterruptedException e) {
          log.warn("Log upload didn't completed successfully for {} ", logKey);
        }
      }
    }
    if (logCache.containsKey(logKey)) {
      log.error("log cache was not drained for {}. num of keys in map {}. This will result in missing logs", logKey,
          logCache.size());
    }
    try {
      SafeHttpCall.executeWithExceptions(logStreamingClient.closeLogStream(token, accountId, logKey, true));
    } catch (Exception ex) {
      log.error("Unable to close log stream for account {} and key {}", accountId, logKey, ex);
    } finally {
      if (scheduledFuture != null) {
        scheduledFuture.cancel(false);
      } else {
        log.error("Scheduled future is missing for logkey {}", logKey);
      }
    }
  }

  @Override
  public void writeLogLine(LogLine logLine, String baseLogKeySuffix) {
    if (logLine == null) {
      throw new InvalidArgumentsException("Log line parameter is mandatory.");
    }

    String logKey = getLogKey(baseLogKeySuffix);

    logStreamingSanitizer.sanitizeLogMessage(logLine, getMarkers());
    colorLog(logLine);

    synchronized (logCache) {
      if (!logCache.containsKey(logKey)) {
        logCache.put(logKey, new ArrayList<>());
      }
      logCache.get(logKey).add(logLine);
    }
  }

  @Override
  public void dispatchLogs() {
    synchronized (logCache) {
      for (Iterator<Map.Entry<String, List<LogLine>>> iterator = logCache.entrySet().iterator(); iterator.hasNext();) {
        Map.Entry<String, List<LogLine>> next = iterator.next();
        try {
          SafeHttpCall.executeWithExceptions(
              logStreamingClient.pushMessage(token, accountId, next.getKey(), next.getValue()));
        } catch (Exception ex) {
          log.error("Unable to push message to log stream for account {} and key {}", accountId, next.getKey(), ex);
        }
        iterator.remove();
      }
      logCache.notifyAll();
    }
  }

  @NotNull
  private String getLogKey(String baseLogKeySuffix) {
    return baseLogKey + (isBlank(baseLogKeySuffix) ? "" : String.format(COMMAND_UNIT_PLACEHOLDER, baseLogKeySuffix));
  }

  private void colorLog(LogLine logLine) {
    String message = logLine.getMessage();
    if (logLine.getLevel() == LogLevel.ERROR) {
      message = color(message, Red, Bold);
    } else if (logLine.getLevel() == LogLevel.WARN) {
      message = color(message, Yellow, Bold);
    }
    message = doneColoring(message);
    logLine.setMessage(message);
  }

  @Override
  public LogCallback obtainLogCallback(String commandName) {
    if (isBlank(appId) || isBlank(activityId)) {
      throw new InvalidArgumentsException(
          "Application id and activity id were not available as part of task params. Please make sure that task params class implements Cd1ApplicationAccess and ActivityAccess interfaces.");
    }

    return new ExecutionLogCallback(logService, accountId, appId, activityId, commandName);
  }

  @Override
  public ITaskProgressClient obtainTaskProgressClient() {
    return taskProgressClient;
  }

  @Override
  public ExecutorService obtainTaskProgressExecutor() {
    return taskProgressExecutor;
  }

  @Override
  public Set<String> getMarkers() {
    if (markers == null) {
      markers = new HashSet<>();
    }
    return markers;
  }
}
