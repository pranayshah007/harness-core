/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.DUMMY_HOST_NAME;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.UnexpectedException;
import io.harness.exception.VerificationOperationException;
import io.harness.serializer.JsonUtils;
import io.harness.time.Timestamp;

import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.CVConstants;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.LogDataCollectionInfo;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.elk.ElkDataCollectionInfo;
import software.wings.service.impl.elk.ElkDelegateServiceImpl;
import software.wings.service.impl.elk.ElkLogFetchRequest;
import software.wings.service.impl.logz.LogzDataCollectionInfo;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.logz.LogzDelegateService;

import com.google.inject.Inject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

/**
 * Created by rsingh on 5/18/17.
 */

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ElkLogzDataCollectionTask extends AbstractDelegateDataCollectionTask {
  @Inject private ElkDelegateService elkDelegateService;
  @Inject private LogzDelegateService logzDelegateService;
  @Inject private LogAnalysisStoreService logAnalysisStoreService;
  private LogDataCollectionInfo dataCollectionInfo;

  public ElkLogzDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    this.dataCollectionInfo = (LogDataCollectionInfo) parameters;
    log.info("log collection - dataCollectionInfo: {}", dataCollectionInfo);
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskStatus.SUCCESS)
        .stateType(dataCollectionInfo.getStateType())
        .build();
  }

  @Override
  protected int getInitialDelayMinutes() {
    return dataCollectionInfo.getInitialDelayMinutes();
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equals(TaskType.ELK_COLLECT_24_7_LOG_DATA.name());
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) {
    return new ElkLogzDataCollector(getTaskId(), dataCollectionInfo, taskResult);
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected DelegateStateType getStateType() {
    return dataCollectionInfo.getStateType();
  }

  private class ElkLogzDataCollector implements Runnable {
    private final LogDataCollectionInfo dataCollectionInfo;
    private long collectionStartTime;
    private int logCollectionMinute;
    private DataCollectionTaskResult taskResult;
    private String delegateTaskId;

    private ElkLogzDataCollector(
        String delegateTaskId, LogDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult) {
      this.delegateTaskId = delegateTaskId;
      this.dataCollectionInfo = dataCollectionInfo;
      this.logCollectionMinute = is24X7Task() ? (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getEndTime())
                                              : dataCollectionInfo.getStartMinute();

      // Condition needed as ELK follows absolute minute model and LogZ follows relative minute
      switch (getStateType()) {
        case ELK:
          this.collectionStartTime =
              is24X7Task() ? dataCollectionInfo.getStartTime() : logCollectionMinute * TimeUnit.MINUTES.toMillis(1);
          break;
        case LOGZ:
          this.collectionStartTime = is24X7Task() ? dataCollectionInfo.getStartTime()
                                                  : Timestamp.minuteBoundary(dataCollectionInfo.getStartTime())
                  + logCollectionMinute * TimeUnit.MINUTES.toMillis(1);
          break;

        default:
          throw new VerificationOperationException(
              ErrorCode.ELK_CONFIGURATION_ERROR, "Invalid StateType : " + getStateType());
      }
      this.taskResult = taskResult;
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      int retry = 0;
      while (!completed.get() && retry < RETRIES) {
        try {
          final List<LogElement> logElements = new ArrayList<>();
          for (String hostName : dataCollectionInfo.getHosts()) {
            addHeartbeat(hostName, dataCollectionInfo, logCollectionMinute, logElements);
            ThirdPartyApiCallLog apiCallLog =
                ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId()));

            Object searchResponse;
            String hostnameField;
            String messageField;
            String timestampField;
            String timestampFieldFormat;
            switch (dataCollectionInfo.getStateType()) {
              case ELK:
                final ElkDataCollectionInfo elkDataCollectionInfo = (ElkDataCollectionInfo) dataCollectionInfo;
                final ElkLogFetchRequest elkFetchRequest =
                    ElkLogFetchRequest.builder()
                        .query(dataCollectionInfo.getQuery())
                        .indices(elkDataCollectionInfo.getIndices())
                        .hostnameField(elkDataCollectionInfo.getHostnameField())
                        .messageField(elkDataCollectionInfo.getMessageField())
                        .timestampField(elkDataCollectionInfo.getTimestampField())
                        .hosts(
                            hostName.equals(DUMMY_HOST_NAME) ? Collections.emptySet() : Collections.singleton(hostName))
                        .startTime(collectionStartTime)
                        .endTime(is24X7Task() ? dataCollectionInfo.getEndTime()
                                              : collectionStartTime + TimeUnit.MINUTES.toMillis(1))
                        .queryType(elkDataCollectionInfo.getQueryType())
                        .build();
                log.info("running elk query: " + JsonUtils.asJson(elkFetchRequest.toElasticSearchJsonObject()));
                searchResponse = elkDelegateService.search(elkDataCollectionInfo.getElkConfig(),
                    elkDataCollectionInfo.getEncryptedDataDetails(), elkFetchRequest, apiCallLog,
                    ElkDelegateServiceImpl.MAX_RECORDS);
                hostnameField = elkDataCollectionInfo.getHostnameField();
                messageField = elkDataCollectionInfo.getMessageField();
                timestampField = elkDataCollectionInfo.getTimestampField();
                timestampFieldFormat = elkDataCollectionInfo.getTimestampFieldFormat();
                break;
              case LOGZ:
                final LogzDataCollectionInfo logzDataCollectionInfo = (LogzDataCollectionInfo) dataCollectionInfo;
                final ElkLogFetchRequest logzFetchRequest =
                    ElkLogFetchRequest.builder()
                        .query(dataCollectionInfo.getQuery())
                        .indices("")
                        .hostnameField(logzDataCollectionInfo.getHostnameField())
                        .messageField(logzDataCollectionInfo.getMessageField())
                        .timestampField(logzDataCollectionInfo.getTimestampField())
                        .hosts(
                            hostName.equals(DUMMY_HOST_NAME) ? Collections.emptySet() : Collections.singleton(hostName))
                        .startTime(collectionStartTime)
                        .endTime(is24X7Task() ? dataCollectionInfo.getEndTime()
                                              : collectionStartTime + TimeUnit.MINUTES.toMillis(1))
                        .queryType(logzDataCollectionInfo.getQueryType())
                        .build();

                log.info("running logz query: " + JsonUtils.asJson(logzFetchRequest.toElasticSearchJsonObject()));
                searchResponse = logzDelegateService.search(logzDataCollectionInfo.getLogzConfig(),
                    logzDataCollectionInfo.getEncryptedDataDetails(), logzFetchRequest, apiCallLog);
                hostnameField = logzDataCollectionInfo.getHostnameField();
                messageField = logzDataCollectionInfo.getMessageField();
                timestampField = logzDataCollectionInfo.getTimestampField();
                timestampFieldFormat = logzDataCollectionInfo.getTimestampFieldFormat();
                break;
              default:
                throw new IllegalStateException("Invalid collection attempt." + dataCollectionInfo);
            }

            JSONObject responseObject = new JSONObject(JsonUtils.asJson(searchResponse));
            JSONObject hits = responseObject.getJSONObject("hits");
            if (hits == null) {
              continue;
            }
            if (!is24X7Task()) {
              long totalHitsPerMinute = getTotalHitsPerMinute(hits);
              if (totalHitsPerMinute > getDelegateTotalHitsVerificationThreshold()) {
                String reason = "Number of logs returned per minute are above the threshold. Please refine your query.";
                throw new VerificationOperationException(ErrorCode.ELK_CONFIGURATION_ERROR, reason);
              }
            }
            try {
              List<LogElement> logRecords = parseElkResponse(searchResponse, dataCollectionInfo.getQuery(),
                  timestampField, timestampFieldFormat, hostnameField, hostName, messageField, logCollectionMinute,
                  is24X7Task(), dataCollectionInfo.getStartTime(), dataCollectionInfo.getEndTime());
              logElements.addAll(logRecords);
              log.info("Added {} records to logElements", logRecords.size());
            } catch (Exception pe) {
              log.info("Exception occured while parsing elk response");
              if (++retry == RETRIES) {
                taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
                taskResult.setErrorMessage("ELK failed search job " + RETRIES + " times");
                completed.set(true);
                break;
              }
              sleep(DATA_COLLECTION_RETRY_SLEEP);
              continue;
            }
          }
          logAnalysisStoreService.save(dataCollectionInfo.getStateType(), dataCollectionInfo.getAccountId(),
              dataCollectionInfo.getApplicationId(), dataCollectionInfo.getCvConfigId(),
              dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getWorkflowId(),
              dataCollectionInfo.getWorkflowExecutionId(), dataCollectionInfo.getServiceId(), delegateTaskId,
              logElements);
          log.info("sent " + dataCollectionInfo.getStateType() + "search records to server. Num of events: "
              + logElements.size() + " application: " + dataCollectionInfo.getApplicationId()
              + " stateExecutionId: " + dataCollectionInfo.getStateExecutionId() + " minute: " + logCollectionMinute);
          break;
        } catch (Throwable ex) {
          /*
           * Save the exception from the first attempt. This is usually
           * more meaningful to trouble shoot.
           */
          if (retry == 0) {
            taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
          }
          if (ex instanceof VerificationOperationException || !(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching logs for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                logCollectionMinute, ex);
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            completed.set(true);
          } else {
            log.warn("error fetching elk/logz logs. retrying in " + DATA_COLLECTION_RETRY_SLEEP + "s", ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }
      if (taskResult.getStatus() == DataCollectionTaskStatus.FAILURE) {
        log.info("Failed Data collection for ELK collection task so quitting the task with StateExecutionId {}",
            dataCollectionInfo.getStateExecutionId());
        completed.set(true);
      } else {
        collectionStartTime += TimeUnit.MINUTES.toMillis(1);
        logCollectionMinute++;
        dataCollectionInfo.setCollectionTime(dataCollectionInfo.getCollectionTime() - 1);
        if (dataCollectionInfo.getCollectionTime() <= 0) {
          // We are done with all data collection, so setting task status to success and quitting.
          log.info("Completed ELK collection task. So setting task status to success and quitting. StateExecutionId {}",
              dataCollectionInfo.getStateExecutionId());
          completed.set(true);
          taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
        }
      }

      if (completed.get()) {
        log.info("Shutting down ELK/LOGZ collection " + dataCollectionInfo.getStateExecutionId());
        shutDownCollection();
      }
    }

    private long getTotalHitsPerMinute(JSONObject hits) {
      long totalHits = 0;
      String totalKeyword = "total";
      if (hits.has(totalKeyword)) {
        if (hits.get(totalKeyword) instanceof JSONObject) {
          JSONObject totalObject = hits.getJSONObject(totalKeyword);
          totalHits = totalObject.getLong("value");
        } else {
          totalHits = hits.getLong(totalKeyword);
        }
      }
      long collectionEndTime =
          is24X7Task() ? dataCollectionInfo.getEndTime() : collectionStartTime + TimeUnit.MINUTES.toMillis(1);
      double intervalInMinutes = (collectionEndTime - collectionStartTime) / (1000 * 60.0);
      if (intervalInMinutes != 0) {
        return (long) (totalHits / intervalInMinutes);
      } else {
        return totalHits;
      }
    }
  }

  private long getDelegateTotalHitsVerificationThreshold() {
    // The multiplier is added to reduce number of runtime exception when running workflow.
    // Varying little bit from configure time threshold is acceptable.
    return 2 * CVConstants.TOTAL_HITS_PER_MIN_THRESHOLD;
  }

  public static List<LogElement> parseElkResponse(Object searchResponse, String query, String timestampField,
      String timestampFieldFormat, String hostnameField, String hostName, String messageField, int logCollectionMinute,
      boolean is24x7Task, long collectionStartTime, long collectionEndTime) {
    List<LogElement> logElements = new ArrayList<>();
    JSONObject responseObject = new JSONObject(JsonUtils.asJson(searchResponse));
    JSONObject hits = responseObject.getJSONObject("hits");
    if (hits == null) {
      return logElements;
    }

    SimpleDateFormat timeFormatter = new SimpleDateFormat(timestampFieldFormat);
    JSONArray logHits = hits.getJSONArray("hits");

    for (int i = 0; i < logHits.length(); i++) {
      JSONObject source = logHits.optJSONObject(i).getJSONObject("_source");
      if (source == null) {
        continue;
      }

      final String host = parseAndGetValue(source, hostnameField);

      // if this elkResponse doesn't belong to this host, ignore it.
      // We ignore case because we don't know if elasticsearch might just lowercase everything in the index.
      if (!is24x7Task && !hostName.trim().equalsIgnoreCase(host.trim())) {
        continue;
      }

      final String logMessage = parseAndGetValue(source, messageField);

      final String timeStamp = parseAndGetValue(source, timestampField);
      long timeStampValue;
      try {
        timeStampValue = timeFormatter.parse(timeStamp).getTime();
      } catch (ParseException pe) {
        throw new VerificationOperationException(ErrorCode.ELK_CONFIGURATION_ERROR,
            "Failed to parse time stamp : " + timeStamp + ", with format: " + timestampFieldFormat);
      }

      if (is24x7Task && (timeStampValue < collectionStartTime || timeStampValue > collectionEndTime)) {
        log.info("received response outside the time range");
        continue;
      }

      final LogElement elkLogElement = new LogElement();
      elkLogElement.setQuery(query);
      elkLogElement.setClusterLabel(String.valueOf(i));
      elkLogElement.setHost(host);
      elkLogElement.setCount(1);
      elkLogElement.setLogMessage(logMessage);
      elkLogElement.setTimeStamp(timeStampValue);
      elkLogElement.setLogCollectionMinute(
          is24x7Task ? TimeUnit.MILLISECONDS.toMinutes(timeStampValue) : logCollectionMinute);
      logElements.add(elkLogElement);
    }

    return logElements;
  }

  public static String parseAndGetValue(JSONObject source, String field) {
    Object messageObject = source;
    String[] messagePaths = field.split("\\.");
    for (int j = 0; j < messagePaths.length; ++j) {
      if (messageObject instanceof JSONObject) {
        messageObject = ((JSONObject) messageObject).get(messagePaths[j]);
      } else if (messageObject instanceof JSONArray) {
        messageObject = ((JSONArray) messageObject).get(Integer.parseInt(messagePaths[j]));
      }
    }
    if (messageObject instanceof String) {
      return (String) messageObject;
    }
    throw new UnexpectedException("Unable to parse JSON response " + source.toString() + " and field " + field);
  }
}
