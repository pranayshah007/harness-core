/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;

import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.sumo.SumoDataCollectionInfo;
import software.wings.service.impl.sumo.SumoDelegateServiceImpl;

import com.google.inject.Inject;
import com.sumologic.client.SumoLogicClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

/**
 * Created by sriram_parthasarathy on 9/12/17.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class SumoDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private SumoDataCollectionInfo dataCollectionInfo;
  private SumoLogicClient sumoClient;

  @Inject private LogAnalysisStoreService logAnalysisStoreService;
  @Inject private SumoDelegateServiceImpl sumoDelegateService;

  public SumoDataCollectionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DelegateStateType getStateType() {
    return DelegateStateType.SUMO;
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    DataCollectionTaskResult taskResult = DataCollectionTaskResult.builder()
                                              .status(DataCollectionTaskStatus.SUCCESS)
                                              .stateType(DelegateStateType.SUMO)
                                              .build();
    this.dataCollectionInfo = (SumoDataCollectionInfo) parameters;
    log.info("log collection - dataCollectionInfo: {}", dataCollectionInfo);
    sumoClient = sumoDelegateService.getSumoClient(
        dataCollectionInfo.getSumoConfig(), dataCollectionInfo.getEncryptedDataDetails(), encryptionService);
    return taskResult;
  }

  @Override
  protected int getInitialDelayMinutes() {
    return dataCollectionInfo.getInitialDelayMinutes();
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equals(TaskType.SUMO_COLLECT_24_7_LOG_DATA.name());
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) {
    return new SumoDataCollector(getTaskId(), dataCollectionInfo, logAnalysisStoreService, is24X7Task(), taskResult);
  }

  private class SumoDataCollector implements Runnable {
    private String delegateTaskId;
    private final SumoDataCollectionInfo dataCollectionInfo;
    private final LogAnalysisStoreService logAnalysisStoreService;
    private final boolean is247Task;
    private long collectionStartTime;
    private int logCollectionMinute;
    private DataCollectionTaskResult taskResult;

    private SumoDataCollector(String delegateTaskId, SumoDataCollectionInfo dataCollectionInfo,
        LogAnalysisStoreService logAnalysisStoreService, boolean is247Task, DataCollectionTaskResult taskResult) {
      this.delegateTaskId = delegateTaskId;
      this.dataCollectionInfo = dataCollectionInfo;
      this.logAnalysisStoreService = logAnalysisStoreService;
      this.is247Task = is247Task;
      this.logCollectionMinute = is247Task ? (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getEndTime())
                                           : dataCollectionInfo.getStartMinute();
      this.taskResult = taskResult;
      this.collectionStartTime =
          is247Task ? dataCollectionInfo.getStartTime() : logCollectionMinute * TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      int retry = 0;
      while (!completed.get() && retry < RETRIES) {
        try {
          final List<LogElement> logElements = new ArrayList<>();
          for (String host : dataCollectionInfo.getHosts()) {
            addHeartbeat(host, dataCollectionInfo, logCollectionMinute, logElements);

            ThirdPartyApiCallLog apiCallLog =
                ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId()));
            final long collectionEndTime =
                is247Task ? dataCollectionInfo.getEndTime() : collectionStartTime + TimeUnit.MINUTES.toMillis(1) - 1;

            try {
              List<LogElement> logElementsResponse =
                  sumoDelegateService.getResponse(dataCollectionInfo, "1m", host, collectionStartTime,
                      collectionEndTime, is247Task, is247Task ? 10000 : 1000, logCollectionMinute, apiCallLog);
              logElements.addAll(logElementsResponse);
            } catch (CancellationException e) {
              log.info("Ugh. Search job was cancelled. Retrying ...", e);
              if (++retry == RETRIES) {
                taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
                taskResult.setErrorMessage(ExceptionUtils.getMessage(e));
                completed.set(true);
                break;
              }
              sleep(DATA_COLLECTION_RETRY_SLEEP);
              continue;
            }
          }

          boolean response = logAnalysisStoreService.save(DelegateStateType.SUMO, dataCollectionInfo.getAccountId(),
              dataCollectionInfo.getApplicationId(), dataCollectionInfo.getCvConfigId(),
              dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getWorkflowId(),
              dataCollectionInfo.getWorkflowExecutionId(), dataCollectionInfo.getServiceId(), delegateTaskId,
              logElements);
          if (!response) {
            if (++retry == RETRIES) {
              taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
              taskResult.setErrorMessage("Cannot save new sumo log elements records to Harness. Server returned error");
              completed.set(true);
              break;
            }
            sleep(DATA_COLLECTION_RETRY_SLEEP);
            continue;
          }

          log.info("sent sumo search records to server. Num of events: " + logElements.size()
              + " application: " + dataCollectionInfo.getApplicationId()
              + " stateExecutionId: " + dataCollectionInfo.getStateExecutionId() + " minute: " + logCollectionMinute);
          break;
        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching logs for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                logCollectionMinute, ex);
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            completed.set(true);
            break;
          } else {
            /*
             * Save the exception from the first attempt. This is usually
             * more meaningful to trouble shoot.
             */
            if (retry == 1) {
              taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
            }
            log.warn("error fetching sumo logs for stateExecutionId {}. retrying in {}s",
                dataCollectionInfo.getStateExecutionId(), DATA_COLLECTION_RETRY_SLEEP, ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }
      if (taskResult.getStatus() == DataCollectionTaskStatus.FAILURE) {
        log.info("Failed Data collection for SumoLogic collection task so quitting the task with StateExecutionId {}",
            dataCollectionInfo.getStateExecutionId());
        completed.set(true);
      } else {
        collectionStartTime += TimeUnit.MINUTES.toMillis(1);
        logCollectionMinute++;
        dataCollectionInfo.setCollectionTime(dataCollectionInfo.getCollectionTime() - 1);
        if (dataCollectionInfo.getCollectionTime() <= 0) {
          // We are done with all data collection, so setting task status to success and quitting.
          log.info(
              "Completed SumoLogic collection task. So setting task status to success and quitting. StateExecutionId {}",
              dataCollectionInfo.getStateExecutionId());
          completed.set(true);
          taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
        }
      }

      if (completed.get()) {
        log.info("Shutting down sumo data collection " + dataCollectionInfo.getStateExecutionId());
        shutDownCollection();
        return;
      }
    }
  }
}
