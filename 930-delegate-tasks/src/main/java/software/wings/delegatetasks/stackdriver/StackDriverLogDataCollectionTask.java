/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.STACKDRIVER_DEFAULT_HOST_NAME_FIELD;
import static software.wings.delegatetasks.cv.CVConstants.STACKDRIVER_DEFAULT_LOG_MESSAGE_FIELD;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;

import com.google.api.services.logging.v2.model.LogEntry;
import com.google.inject.Inject;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class StackDriverLogDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private StackDriverLogDataCollectionInfo dataCollectionInfo;

  @Inject private StackDriverDelegateService stackDriverDelegateService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private GcpHelperService gcpHelperService;
  @Inject private LogAnalysisStoreService logAnalysisStoreService;

  public StackDriverLogDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DelegateStateType getStateType() {
    return DelegateStateType.STACK_DRIVER_LOG;
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (StackDriverLogDataCollectionInfo) parameters;
    log.info("metric collection - dataCollectionInfo: {}", dataCollectionInfo);
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskResult.DataCollectionTaskStatus.SUCCESS)
        .stateType(DelegateStateType.STACK_DRIVER_LOG)
        .build();
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equals(TaskType.STACKDRIVER_COLLECT_24_7_LOG_DATA.name());
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException {
    return new StackDriverMetricCollector(dataCollectionInfo, taskResult, getTaskId());
  }

  private class StackDriverMetricCollector implements Runnable {
    private final StackDriverLogDataCollectionInfo dataCollectionInfo;
    private final DataCollectionTaskResult taskResult;
    private long collectionStartTime;
    private long collectionEndTime;
    private long logCollectionMinute;
    private String delegateTaskId;

    private StackDriverMetricCollector(StackDriverLogDataCollectionInfo dataCollectionInfo,
        DataCollectionTaskResult taskResult, String delegateTaskId) {
      this.dataCollectionInfo = dataCollectionInfo;
      this.taskResult = taskResult;
      this.delegateTaskId = delegateTaskId;

      this.logCollectionMinute = is24X7Task()
          ? (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getEndTime())
          : (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getStartTime());

      this.collectionStartTime = dataCollectionInfo.getStartTime();
      this.collectionEndTime = dataCollectionInfo.getEndTime();
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      encryptionService.decrypt(dataCollectionInfo.getGcpConfig(), dataCollectionInfo.getEncryptedDataDetails(), false);
      int retry = 0;
      log.info("Initiating Stackdriver log Data collection for startTime : {} duration : {}", collectionStartTime,
          dataCollectionInfo.getCollectionTime());
      while (!completed.get() && retry < RETRIES) {
        try {
          log.info("starting log data collection for {} for minute {}", dataCollectionInfo, collectionStartTime);

          List<LogElement> logElements = new ArrayList<>();
          for (String host : dataCollectionInfo.getHosts()) {
            addHeartbeat(host, dataCollectionInfo, logCollectionMinute, logElements);
          }

          try {
            List<LogEntry> entries = stackDriverDelegateService.fetchLogs(
                dataCollectionInfo, collectionStartTime, collectionEndTime, is24X7Task(), true);

            int clusterLabel = 0;

            log.info("Total no. of log records found : {}", entries.size());
            for (LogEntry entry : entries) {
              long timeStamp = new DateTime(entry.getTimestamp()).getMillis();
              String logMessageField = isNotEmpty(dataCollectionInfo.getLogMessageField())
                  ? dataCollectionInfo.getLogMessageField()
                  : STACKDRIVER_DEFAULT_LOG_MESSAGE_FIELD;
              String logMessage = JsonPath.read(entry.toString(), logMessageField);
              String host = JsonPath.read(entry.toString(),
                  isNotEmpty(dataCollectionInfo.getHostnameField()) ? dataCollectionInfo.getHostnameField()
                                                                    : STACKDRIVER_DEFAULT_HOST_NAME_FIELD);
              if (isEmpty(logMessage) || isEmpty(host)) {
                log.error(
                    "either log message or host is empty for stateExId {} cvConfigId {}. Log message field: {} host field: {} entry: {} ",
                    dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getCvConfigId(), logMessageField,
                    dataCollectionInfo.getHostnameField(), entry);
              } else {
                LogElement logElement =
                    LogElement.builder()
                        .query(dataCollectionInfo.getQuery())
                        .logCollectionMinute(
                            is24X7Task() ? (int) TimeUnit.MILLISECONDS.toMinutes(timeStamp) : logCollectionMinute)
                        .clusterLabel(String.valueOf(clusterLabel++))
                        .count(1)
                        .logMessage(logMessage)
                        .timeStamp(timeStamp)
                        .host(host)
                        .build();
                logElements.add(logElement);
              }
            }

          } catch (DataCollectionException e) {
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            taskResult.setErrorMessage(e.getMessage());
            completed.set(true);
            break;
          } catch (Exception e) {
            log.info("Search job was cancelled. Retrying ...", e);
            if (++retry == RETRIES) {
              taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
              taskResult.setErrorMessage(
                  "Stackdriver search job failed " + RETRIES + " times. Error: " + ExceptionUtils.getMessage(e));
              completed.set(true);
              break;
            }
            sleep(DATA_COLLECTION_RETRY_SLEEP);
            continue;
          }
          log.info("sent Stackdriver search records to server. Num of events: " + logElements.size()
              + " application: " + dataCollectionInfo.getApplicationId()
              + " stateExecutionId: " + dataCollectionInfo.getStateExecutionId() + " minute: " + logCollectionMinute);

          boolean response = logAnalysisStoreService.save(DelegateStateType.STACK_DRIVER_LOG,
              dataCollectionInfo.getAccountId(), dataCollectionInfo.getApplicationId(),
              dataCollectionInfo.getCvConfigId(), dataCollectionInfo.getStateExecutionId(),
              dataCollectionInfo.getWorkflowId(), dataCollectionInfo.getWorkflowExecutionId(),
              dataCollectionInfo.getServiceId(), delegateTaskId, logElements);
          if (!response) {
            if (++retry == RETRIES) {
              taskResult.setStatus(DataCollectionTaskResult.DataCollectionTaskStatus.FAILURE);
              taskResult.setErrorMessage("Unable to save Stack driver logs after " + RETRIES + " times");
              completed.set(true);
              break;
            }
            sleep(DATA_COLLECTION_RETRY_SLEEP);
            continue;
          }

          // We are done with all data collection, so setting task status to success and quitting.
          log.info(
              "Completed stack driver collection task. So setting task status to success and quitting. StateExecutionId {}",
              dataCollectionInfo.getStateExecutionId());
          completed.set(true);
          taskResult.setStatus(DataCollectionTaskResult.DataCollectionTaskStatus.SUCCESS);
          break;
        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching stack driver logs for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                logCollectionMinute, ex);
            taskResult.setStatus(DataCollectionTaskResult.DataCollectionTaskStatus.FAILURE);
            completed.set(true);
            break;
          } else {
            if (retry == 1) {
              taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
            }
            log.warn("error fetching stack driver logs for minute " + logCollectionMinute + ". retrying in "
                    + DATA_COLLECTION_RETRY_SLEEP + "s",
                ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }

      if (completed.get()) {
        log.info("Shutting down stack driver data collection");
        shutDownCollection();
        return;
      }
    }
  }

  @Override
  protected int getInitialDelayMinutes() {
    // this state is implemented with per min task
    return 0;
  }
}
