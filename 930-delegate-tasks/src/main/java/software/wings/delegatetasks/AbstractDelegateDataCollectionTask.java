/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.DELAY_MINUTES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.network.Http;

import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.LogDataCollectionInfo;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;

/**
 * Created by rsingh on 9/11/17.
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class AbstractDelegateDataCollectionTask extends AbstractDelegateRunnableTask {
  public static final String HARNESS_HEARTBEAT_METRIC_NAME = "Harness heartbeat metric";
  public static final int PREDECTIVE_HISTORY_MINUTES = 120;
  public static final int RETRIES = 3;
  private static final int COLLECTION_PERIOD_MINS = 1;
  protected final AtomicBoolean completed = new AtomicBoolean(false);
  private final Object lockObject = new Object();
  @Inject protected EncryptionService encryptionService;
  @Inject @Named("asyncExecutor") private ExecutorService executorService;
  @Inject @Named("verificationExecutor") private ScheduledExecutorService verificationExecutor;
  @Inject private MetricDataStoreService metricStoreService;

  private ScheduledFuture future;
  private volatile Future taskFuture;
  private volatile boolean pendingTask;

  public AbstractDelegateDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  public static OkHttpClient getUnsafeHttpClient(String baseUrl) {
    return Http.getUnsafeOkHttpClient(baseUrl, 15, 60);
  }

  private void waitForCompletion() {
    synchronized (lockObject) {
      while (!completed.get()) {
        try {
          lockObject.wait();
        } catch (InterruptedException e) {
          completed.set(true);
          getLogger().info("{} data collection interrupted", getStateType());
          Thread.currentThread().interrupt();
        }
      }
    }
  }
  @SuppressWarnings("PMD")
  protected void shutDownCollection() {
    /* Redundant now, but useful if calling shutDownCollection
     * from the worker threads before the job is aborted
     */
    completed.set(true);
    if (future != null) {
      future.cancel(true);
    }
    synchronized (lockObject) {
      lockObject.notifyAll();
    }
  }

  @Override
  public DataCollectionTaskResult run(Object[] parameters) {
    throw new NotImplementedException("not supported. use DataCollectionTaskResult run(TaskParameters parameters)");
  }

  @Override
  public DataCollectionTaskResult run(TaskParameters parameters) {
    try {
      DataCollectionTaskResult taskResult = initDataCollection(parameters);
      if (taskResult.getStatus() == DataCollectionTaskStatus.FAILURE) {
        getLogger().error("Data collection task Init failed");
        return taskResult;
      }

      final Runnable runnable = getDataCollector(taskResult);
      future = verificationExecutor.scheduleAtFixedRate(() -> {
        if (taskFuture == null || taskFuture.isCancelled() || taskFuture.isDone()) {
          getLogger().debug("submitting data collection to executor service");
          taskFuture = executorService.submit(runnable);
        } else {
          if (!pendingTask) {
            executorService.submit(() -> {
              try {
                getLogger().info("queuing pending task");
                taskFuture.get();
                taskFuture = executorService.submit(runnable);
              } catch (Exception e) {
                getLogger().error("Unable to queue collection task ", e);
                shutDownCollection();
              }
              pendingTask = false;
            });
          } else {
            getLogger().info("task already in queue");
          }
          pendingTask = true;
        }
      }, getInitialDelaySeconds(), getPeriodSeconds(), TimeUnit.SECONDS);
      getLogger().info("going to collect data for " + parameters);
      waitForCompletion();
      getLogger().debug(" finish data collection for " + parameters + ". result is " + taskResult);
      return taskResult;
    } catch (Exception e) {
      getLogger().error("Data collection task   failed : ", e);
      return DataCollectionTaskResult.builder()
          .status(DataCollectionTaskStatus.FAILURE)
          .stateType(getStateType())
          .errorMessage("Data collection task failed : " + ExceptionUtils.getMessage(e))
          .build();
    }
  }

  public boolean saveMetrics(
      String accountId, String appId, String stateExecutionId, List<NewRelicMetricDataRecord> records) {
    if (records.isEmpty()) {
      return true;
    }
    Exception exception;
    int retrySave = 0;
    do {
      try {
        return metricStoreService.saveNewRelicMetrics(accountId, appId, stateExecutionId, getTaskId(), records);
      } catch (Exception e) {
        getLogger().error(
            "error saving new apm metrics StateExecutionId: {}, Size: {}, {}", stateExecutionId, records.size(), e);
        sleep(DATA_COLLECTION_RETRY_SLEEP);
        exception = e;
      }
    } while (++retrySave != RETRIES);

    throw new IllegalStateException(exception);
  }

  protected List<NewRelicMetricDataRecord> getAllMetricRecords(
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> records) {
    List<NewRelicMetricDataRecord> rv = new ArrayList<>();
    records.cellSet().forEach(cell -> rv.add(cell.getValue()));
    return rv;
  }

  protected abstract DelegateStateType getStateType();

  protected abstract DataCollectionTaskResult initDataCollection(TaskParameters parameters);

  protected abstract Logger getLogger();

  protected abstract Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException;

  protected int getInitialDelayMinutes() {
    if (is24X7Task()) {
      return 0;
    }
    return DELAY_MINUTES;
  }

  protected int getInitialDelaySeconds() {
    return (int) TimeUnit.MINUTES.toSeconds(getInitialDelayMinutes());
  }

  protected abstract boolean is24X7Task();

  protected int getPeriodSeconds() {
    return (int) TimeUnit.MINUTES.toSeconds(COLLECTION_PERIOD_MINS);
  }

  protected void addHeartbeat(String host, LogDataCollectionInfo logDataCollectionInfo, long logCollectionMinute,
      List<LogElement> logElements) {
    if (!is24X7Task()) {
      logElements.add(LogElement.builder()
                          .query(logDataCollectionInfo.getQuery())
                          .clusterLabel("-3")
                          .host(host)
                          .count(0)
                          .logMessage("")
                          .timeStamp(0)
                          .logCollectionMinute(logCollectionMinute)
                          .build());
    } else {
      for (long heartbeatMin = TimeUnit.MILLISECONDS.toMinutes(logDataCollectionInfo.getStartTime());
           heartbeatMin <= TimeUnit.MILLISECONDS.toMinutes(logDataCollectionInfo.getEndTime()); heartbeatMin++) {
        logElements.add(LogElement.builder()
                            .query(logDataCollectionInfo.getQuery())
                            .clusterLabel("-3")
                            .host(host)
                            .count(0)
                            .logMessage("")
                            .timeStamp(TimeUnit.MINUTES.toMillis(heartbeatMin))
                            .logCollectionMinute((int) heartbeatMin)
                            .build());
      }
    }
  }
}
