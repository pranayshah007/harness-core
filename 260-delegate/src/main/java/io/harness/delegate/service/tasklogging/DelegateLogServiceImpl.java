/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.tasklogging;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.network.SafeHttpCall.execute;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogHelper.doneColoring;
import static software.wings.beans.LogWeight.Bold;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.right;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.managerclient.VerificationServiceClient;
import io.harness.observer.Subject;
import io.harness.rest.RestResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.verificationclient.CVNextGenServiceClient;

import software.wings.beans.dto.CVActivityLog;
import software.wings.beans.dto.Log;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.LogSanitizer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateLogServiceImpl implements DelegateLogService {
  /**
   * The Size Limit For Final LogLine
   */
  static final int ACTIVITY_STATUS_LOGLINE_LIMIT = 1024 * 10;
  /**
   * Total Permissible Size Limit for LogLines for a Particular ActivityId
   */
  static final int ACTIVITY_LOGS_TOTAL_SIZE = 1024 * 10 * 2500;
  /**
   * Maximum number of entries to keep in the #activityLogSize Map 10^6
   */
  static final int MAX_ACTIVITIES = 1000000;
  public static final String TRUNCATION_MESSAGE = "\nThe Above Log Message Has Been Truncated Due To Size Limit\n";

  private final String LOGS_COMMON_MESSAGE_ERROR = "Unexpected Cache eviction accountId={}, logs={}, removalCause={}";

  private Cache<String, List<Log>> cache;
  private Cache<String, List<ThirdPartyApiCallLog>> apiCallLogCache;
  private Cache<String, List<CVNGLogDTO>> cvngLogCache;
  private Cache<String, List<CVActivityLog>> cvActivityLogCache;
  private DelegateAgentManagerClient delegateAgentManagerClient;
  private final Subject<LogSanitizer> logSanitizerSubject = new Subject<>();
  private VerificationServiceClient verificationServiceClient;
  private final KryoSerializer kryoSerializer;
  private HashMap<String, Integer> activityLogSize = new LinkedHashMap<String, Integer>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
      return size() > MAX_ACTIVITIES;
    }
  };
  @Inject private CVNextGenServiceClient cvNextGenServiceClient;

  @Inject
  public DelegateLogServiceImpl(DelegateAgentManagerClient delegateAgentManagerClient,
      @Named("asyncExecutor") ExecutorService executorService, VerificationServiceClient verificationServiceClient,
      KryoSerializer kryoSerializer) {
    this.delegateAgentManagerClient = delegateAgentManagerClient;
    this.verificationServiceClient = verificationServiceClient;
    this.cache = Caffeine.newBuilder()
                     .executor(executorService)
                     .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
                     .removalListener(this::dispatchCommandExecutionLogs)
                     .build();
    this.apiCallLogCache = Caffeine.newBuilder()
                               .executor(executorService)
                               .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
                               .removalListener(this::dispatchApiCallLogs)
                               .build();
    this.cvActivityLogCache = Caffeine.newBuilder()
                                  .executor(executorService)
                                  .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
                                  .removalListener(this::dispatchCVActivityLogs)
                                  .build();
    this.cvngLogCache = Caffeine.newBuilder()
                            .executor(executorService)
                            .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
                            .removalListener(this::dispatchCVNGLogs)
                            .build();

    this.kryoSerializer = kryoSerializer;

    Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("delegate-log-service").build())
        .scheduleAtFixedRate(
            ()
                -> {
              this.cache.cleanUp();
              this.apiCallLogCache.cleanUp();
              this.cvActivityLogCache.cleanUp();
            },
            1000, 1000,
            TimeUnit.MILLISECONDS); // periodic cleanup for expired keys
  }

  @Override
  public synchronized void save(String accountId, Log logObject) {
    if (isNotEmpty(accountId)) {
      logObject.setAccountId(accountId);
    }

    if (isBlank(logObject.getActivityId()) || isBlank(logObject.getCommandUnitName())) {
      log.info("Logging stack while saving the execution logObject ", new Exception(""));
    }

    String line =
        logSanitizerSubject.fireProcess(LogSanitizer::sanitizeLog, logObject.getActivityId(), logObject.getLogLine());
    if (logObject.getLogLevel() == LogLevel.ERROR) {
      line = color(line, Red, Bold);
    } else if (logObject.getLogLevel() == LogLevel.WARN) {
      line = color(line, Yellow, Bold);
    }
    line = doneColoring(line);
    logObject.setLogLine(line);

    saveLogsBounded(accountId, logObject);
  }

  private void saveLogsBounded(String accountId, Log logObject) {
    int currentSize = activityLogSize.getOrDefault(logObject.getActivityId(), 0);
    if (logObject.getCommandExecutionStatus() == RUNNING) {
      if (currentSize >= ACTIVITY_LOGS_TOTAL_SIZE) {
        log.warn("Not Saving LogLine because of size overflow for ActivityId {}", logObject.getActivityId());
        return;
      }
      activityLogSize.put(logObject.getActivityId(), currentSize + StringUtils.length(logObject.getLogLine()));
    } else {
      if (currentSize >= ACTIVITY_LOGS_TOTAL_SIZE) {
        String right = right(logObject.getLogLine(), ACTIVITY_STATUS_LOGLINE_LIMIT);
        logObject.setLogLine(new StringBuilder().append(right).append(TRUNCATION_MESSAGE).toString());
      }
      activityLogSize.remove(logObject.getActivityId());
    }
    insertLogToCache(accountId, logObject);
  }

  @VisibleForTesting
  void insertLogToCache(String accountId, Log logObject) {
    Optional.ofNullable(cache.get(accountId, s -> new ArrayList<>())).ifPresent(logs -> logs.add(logObject));
  }

  @Override
  public synchronized void save(String accountId, ThirdPartyApiCallLog thirdPartyApiCallLog) {
    thirdPartyApiCallLog.setUuid(null);

    Optional.ofNullable(apiCallLogCache.get(accountId, s -> new ArrayList<>()))
        .ifPresent(logs -> logs.add(thirdPartyApiCallLog));
  }

  @Override
  public synchronized void save(String accountId, CVNGLogDTO cvngLogDTO) {
    Optional.ofNullable(cvngLogCache.get(accountId, s -> new ArrayList<>())).ifPresent(logs -> logs.add(cvngLogDTO));
  }

  @Override
  public synchronized void save(String accountId, CVActivityLog cvActivityLog) {
    Optional.ofNullable(cvActivityLogCache.get(accountId, s -> new ArrayList<>()))
        .ifPresent(cvActivityLogs -> cvActivityLogs.add(cvActivityLog));
  }

  @Override
  public void registerLogSanitizer(LogSanitizer sanitizer) {
    logSanitizerSubject.register(sanitizer);
  }

  @Override
  public void unregisterLogSanitizer(LogSanitizer sanitizer) {
    logSanitizerSubject.unregister(sanitizer);
  }

  private void dispatchCommandExecutionLogs(String accountId, List<Log> logs, RemovalCause removalCause) {
    if (accountId == null || logs.isEmpty()) {
      log.error(LOGS_COMMON_MESSAGE_ERROR, accountId, logs, removalCause);
      return;
    }
    logs.stream()
        .collect(groupingBy(Log::getActivityId, toList()))
        .forEach((activityId, logsList) -> dispatchActivityLogs(accountId, activityId, logsList));
  }

  private void dispatchActivityLogs(String accountId, String activityId, List<Log> logsList) {
    logsList.stream()
        .collect(groupingBy(Log::getCommandUnitName, toList()))
        .forEach((unitName, commandLogs) -> dispatchCommandUnitLogs(accountId, activityId, unitName, commandLogs));
  }

  private void dispatchCommandUnitLogs(String accountId, String activityId, String unitName, List<Log> commandLogs) {
    List<List<Log>> batchedLogs = new ArrayList<>();
    List<Log> batch = new ArrayList<>();
    LogLevel batchLogLevel = LogLevel.INFO;

    for (Log logObject : commandLogs) {
      if (logObject.getLogLevel() != batchLogLevel) {
        if (isNotEmpty(batch)) {
          batchedLogs.add(batch);
          batch = new ArrayList<>();
        }
        batchLogLevel = logObject.getLogLevel();
      }
      batch.add(logObject);
    }
    if (isNotEmpty(batch)) {
      batchedLogs.add(batch);
    }

    for (List<Log> logBatch : batchedLogs) {
      try {
        CommandExecutionStatus commandUnitStatus = logBatch.stream()
                                                       .filter(Objects::nonNull)
                                                       .map(Log::getCommandExecutionStatus)
                                                       .filter(asList(SUCCESS, FAILURE)::contains)
                                                       .findFirst()
                                                       .orElse(RUNNING);

        String logText = logBatch.stream().map(Log::getLogLine).collect(joining("\n"));
        Log logObject = logBatch.get(0);
        logObject.setLogLine(logText);
        logObject.setLinesCount(logBatch.size());
        logObject.setCommandExecutionStatus(commandUnitStatus);
        logObject.setCreatedAt(System.currentTimeMillis());

        byte[] logSerialized = kryoSerializer.asBytes(logObject);

        log.debug("Dispatched logObject status- [{}] [{}] for activityId [{}]", logObject.getCommandUnitName(),
            logObject.getCommandExecutionStatus(), activityId);
        RestResponse restResponse = execute(delegateAgentManagerClient.saveCommandUnitLogs(activityId,
            URLEncoder.encode(unitName, StandardCharsets.UTF_8.toString()), accountId,
            RequestBody.create(MediaType.parse("application/octet-stream"), logSerialized)));
        log.debug("{} logObject lines dispatched for accountId: {}, activityId: {}",
            restResponse.getResource() != null ? logBatch.size() : 0, accountId, activityId);
      } catch (Exception e) {
        log.error("Dispatch log failed. lost logs[{}]", logBatch.size(), e);
      }
    }
  }

  private void dispatchApiCallLogs(String accountId, List<ThirdPartyApiCallLog> logs, RemovalCause removalCause) {
    if (accountId == null || logs.isEmpty()) {
      log.error(LOGS_COMMON_MESSAGE_ERROR, accountId, logs, removalCause);
      return;
    }
    logs.stream()
        .collect(groupingBy(ThirdPartyApiCallLog::getStateExecutionId, toList()))
        .forEach((activityId, logsList) -> {
          if (isEmpty(logsList)) {
            return;
          }
          String stateExecutionId = logsList.get(0).getStateExecutionId();
          String delegateId = DelegateAgentCommonVariables.getDelegateId();
          logsList.forEach(logObject -> logObject.setDelegateId(delegateId));
          try {
            log.debug("Dispatching {} api call logs for [{}] [{}]", logsList.size(), stateExecutionId, accountId);

            log.debug("Converting the logs into a byte array.");
            byte[] logsListAsBytes = kryoSerializer.asBytes(logsList);
            RequestBody logsAsRequestBody =
                RequestBody.create(MediaType.parse("application/octet-stream"), logsListAsBytes);
            log.debug("Logs successfully converted!");

            RestResponse restResponse =
                execute(delegateAgentManagerClient.saveApiCallLogs(delegateId, accountId, logsAsRequestBody));
            log.debug("Dispatched {} api call logs for [{}] [{}]",
                restResponse == null || restResponse.getResource() != null ? logsList.size() : 0, stateExecutionId,
                accountId);
          } catch (IOException e) {
            log.error("Dispatch log failed for {}. printing lost logs[{}]", stateExecutionId, logsList.size(), e);
            logsList.forEach(logObject -> log.error(logObject.toString()));
            log.error("Finished printing lost logs");
          }
        });
  }

  private void dispatchCVNGLogs(String accountId, List<CVNGLogDTO> logs, RemovalCause removalCause) {
    if (accountId == null || logs.isEmpty()) {
      log.error(LOGS_COMMON_MESSAGE_ERROR, accountId, logs, removalCause);
      return;
    }
    logs.stream().collect(groupingBy(CVNGLogDTO::getTraceableId, toList())).forEach((activityId, logsList) -> {
      if (isEmpty(logsList)) {
        return;
      }
      String traceableId = logsList.get(0).getTraceableId();
      try {
        log.debug("Dispatching {} api call logs to  CVNG for [{}] [{}]", logsList.size(), traceableId, accountId);

        RestResponse<Void> restResponse = execute(cvNextGenServiceClient.saveCVNGLogRecords(accountId, logsList));
        log.debug("Dispatched {} api call logs to CVNG for [{}] [{}]", logsList.size(), traceableId, accountId);
      } catch (IOException e) {
        log.error("Dispatch log failed for {}. printing lost logs[{}]", traceableId, logsList.size(), e);
        logsList.forEach(logObject -> log.error(logObject.toString()));
        log.error("Finished printing lost logs");
      }
    });
  }

  private void dispatchCVActivityLogs(String accountId, List<CVActivityLog> logs, RemovalCause removalCause) {
    if (accountId == null || logs.isEmpty()) {
      log.error(LOGS_COMMON_MESSAGE_ERROR, accountId, logs, removalCause);
      return;
    }
    Iterables.partition(logs, 100).forEach(batch -> {
      try {
        safeExecute(verificationServiceClient.saveActivityLogs(accountId, logs));
        log.debug("Dispatched {} cv activity logs [{}]", batch.size(), accountId);
      } catch (Exception e) {
        log.error("Dispatch log failed. printing lost activity logs[{}]", batch.size(), e);
        batch.forEach(logObject -> log.error(logObject.toString()));
        log.error("Finished printing lost activity logs");
      }
    });
  }
  private void safeExecute(retrofit2.Call<?> call) throws IOException {
    Response<?> response = call.execute();
    if (!response.isSuccessful()) {
      throw new RuntimeException("Response code: " + response.code() + ", error body: " + response.errorBody());
    }
  }
}
