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

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.DURATION_TO_ASK_MINUTES;
import static software.wings.service.impl.analysis.TimeSeriesMlAnalysisType.PREDICTIVE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.stackdriver.StackDriverDataCollectionInfo;
import software.wings.service.impl.stackdriver.StackdriverDataFetchParameters;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;

/**
 * Created by Pranjal on 11/30/18.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class StackDriverDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private StackDriverDataCollectionInfo dataCollectionInfo;

  @Inject private StackDriverDelegateService stackDriverDelegateService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private GcpHelperService gcpHelperService;
  @Inject private EncryptionService encryptionService;

  public StackDriverDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (StackDriverDataCollectionInfo) parameters;
    log.info("metric collection - dataCollectionInfo: {}", dataCollectionInfo);
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskStatus.SUCCESS)
        .stateType(DelegateStateType.STACK_DRIVER)
        .build();
  }

  @Override
  protected DelegateStateType getStateType() {
    return DelegateStateType.STACK_DRIVER;
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
    return getTaskType().equals(TaskType.STACKDRIVER_COLLECT_24_7_METRIC_DATA.name());
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException {
    return new StackDriverMetricCollector(dataCollectionInfo, taskResult, is24X7Task());
  }

  private class StackDriverMetricCollector implements Runnable {
    private final StackDriverDataCollectionInfo dataCollectionInfo;
    private final DataCollectionTaskResult taskResult;
    private long collectionStartTime;
    private long dataCollectionStartMinute;
    private int dataCollectionCurrentMinute;
    private boolean is247Task;

    private StackDriverMetricCollector(
        StackDriverDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult, boolean is247Task) {
      this.dataCollectionInfo = dataCollectionInfo;
      this.taskResult = taskResult;
      this.is247Task = is247Task;

      this.dataCollectionCurrentMinute = dataCollectionInfo.getStartMinute();
      this.dataCollectionStartMinute = dataCollectionInfo.getStartMinute();

      this.collectionStartTime = dataCollectionCurrentMinute * TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      encryptionService.decrypt(dataCollectionInfo.getGcpConfig(), dataCollectionInfo.getEncryptedDataDetails(), false);
      int retry = 0;
      while (!completed.get() && retry < RETRIES) {
        try {
          log.info(
              "starting metric data collection for {} for minute {}", dataCollectionInfo, dataCollectionCurrentMinute);

          dataCollectionInfo.setDataCollectionMinute(dataCollectionCurrentMinute);

          TreeBasedTable<String, Long, NewRelicMetricDataRecord> metricDataRecords = getMetricsData();
          // HeartBeat
          metricDataRecords.put(HARNESS_HEARTBEAT_METRIC_NAME, 0L,
              NewRelicMetricDataRecord.builder()
                  .stateType(getStateType())
                  .name(HARNESS_HEARTBEAT_METRIC_NAME)
                  .appId(dataCollectionInfo.getApplicationId())
                  .workflowId(dataCollectionInfo.getWorkflowId())
                  .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                  .serviceId(dataCollectionInfo.getServiceId())
                  .cvConfigId(dataCollectionInfo.getCvConfigId())
                  .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                  .dataCollectionMinute(getDataCollectionMinuteForHeartbeat(is247Task))
                  .timeStamp(collectionStartTime)
                  .level(ClusterLevel.H0)
                  .groupName(DEFAULT_GROUP_NAME)
                  .build());

          List<NewRelicMetricDataRecord> recordsToSave = getAllMetricRecords(metricDataRecords);
          if (!saveMetrics(dataCollectionInfo.getGcpConfig().getAccountId(), dataCollectionInfo.getApplicationId(),
                  dataCollectionInfo.getStateExecutionId(), recordsToSave)) {
            retry = RETRIES;
            taskResult.setErrorMessage("Cannot save new stack driver metric records to Harness. Server returned error");
            throw new RuntimeException("Cannot save new stack driver metric records to Harness. Server returned error");
          }
          log.debug("Sent {} stack driver metric records to the server for minute {}", recordsToSave.size(),
              dataCollectionCurrentMinute);

          dataCollectionCurrentMinute++;
          collectionStartTime += TimeUnit.MINUTES.toMillis(1);

          if (dataCollectionCurrentMinute - dataCollectionStartMinute >= dataCollectionInfo.getCollectionTime()) {
            // We are done with all data collection, so setting task status to success and quitting.
            log.info(
                "Completed stack driver collection task. So setting task status to success and quitting. StateExecutionId {}",
                dataCollectionInfo.getStateExecutionId());
            completed.set(true);
            taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
          }
          break;
        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching metrics for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                dataCollectionCurrentMinute, ex);
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            completed.set(true);
            break;
          } else {
            if (retry == 1) {
              taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
            }
            log.warn("error fetching stack driver metrics for minute " + dataCollectionCurrentMinute + ". retrying in "
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

    private int getDataCollectionMinuteForHeartbeat(boolean is247Task) {
      int collectionMin = dataCollectionCurrentMinute;
      if (is247Task) {
        collectionMin = (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getStartTime())
            + dataCollectionInfo.getCollectionTime();
      }
      return collectionMin;
    }

    public TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricDataRecords(
        StackdriverDataFetchParameters dataFetchParameters) {
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> rv = TreeBasedTable.create();

      String projectId = isNotEmpty(dataCollectionInfo.getProjectId())
          ? dataCollectionInfo.getProjectId()
          : stackDriverDelegateService.getProjectId(dataCollectionInfo.getGcpConfig());
      encryptionService.decrypt(dataCollectionInfo.getGcpConfig(), dataCollectionInfo.getEncryptedDataDetails(), false);
      Monitoring monitoring =
          gcpHelperService.getMonitoringService(dataCollectionInfo.getGcpConfig().getServiceAccountKeyFileContent(),
              projectId, dataCollectionInfo.getGcpConfig().isUseDelegateSelectors());
      dataFetchParameters.setProjectId(projectId);
      dataFetchParameters.setMonitoring(monitoring);

      switch (dataCollectionInfo.getTimeSeriesMlAnalysisType()) {
        case COMPARATIVE:
          fetchMetrics(dataFetchParameters, dataCollectionInfo.getApplicationId(), dataCollectionInfo, rv,
              dataCollectionInfo.getTimeSeriesMlAnalysisType());
          break;
        case PREDICTIVE:
          int periodToCollect = 0;
          if (is247Task) {
            periodToCollect = dataCollectionInfo.getCollectionTime();
          } else {
            periodToCollect = (dataFetchParameters.getDataCollectionMinute() == 0)
                ? PREDECTIVE_HISTORY_MINUTES + DURATION_TO_ASK_MINUTES
                : DURATION_TO_ASK_MINUTES;
          }
          if (is247Task) {
            dataFetchParameters.setEndTime(
                dataFetchParameters.getStartTime() + TimeUnit.MINUTES.toMillis(periodToCollect));
            dataFetchParameters.setDimensionValue("dummyHost");
            fetchMetrics(dataFetchParameters, dataCollectionInfo.getApplicationId(), dataCollectionInfo, rv,
                dataCollectionInfo.getTimeSeriesMlAnalysisType());
          } else {
            dataFetchParameters.setStartTime(
                dataFetchParameters.getEndTime() - TimeUnit.MINUTES.toMillis(periodToCollect));
            fetchMetrics(dataFetchParameters, dataCollectionInfo.getApplicationId(), dataCollectionInfo, rv,
                dataCollectionInfo.getTimeSeriesMlAnalysisType());
          }
          break;
        default:
          throw new WingsException("Invalid strategy " + dataCollectionInfo.getTimeSeriesMlAnalysisType());
      }
      return rv;
    }

    private void setupThridPartyCallLogs(ThirdPartyApiCallLog apiCallLog, String filter, long startTime, long endTime,
        String projectId, String metricName) {
      apiCallLog.setTitle("Fetching data from project " + projectId + " for metric " + metricName);
      apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());

      apiCallLog.addFieldToRequest(
          ThirdPartyApiCallField.builder().name("body").value(JsonUtils.asJson(filter)).type(FieldType.JSON).build());
      apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                       .name("Start Time")
                                       .value(stackDriverDelegateService.getDateFormatTime(startTime))
                                       .type(FieldType.TIMESTAMP)
                                       .build());
      apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                       .name("End Time")
                                       .value(stackDriverDelegateService.getDateFormatTime(endTime))
                                       .type(FieldType.TIMESTAMP)
                                       .build());
    }
    public void fetchMetrics(StackdriverDataFetchParameters dataFetchParameters, String appId,
        StackDriverDataCollectionInfo dataCollectionInfo, TreeBasedTable<String, Long, NewRelicMetricDataRecord> rv,
        TimeSeriesMlAnalysisType analysisType) {
      String projectResource = "projects/" + dataFetchParameters.getProjectId();
      setupThridPartyCallLogs(dataFetchParameters.getApiCallLog(), dataFetchParameters.getFilter(),
          dataFetchParameters.getStartTime(), dataFetchParameters.getEndTime(), dataFetchParameters.getProjectId(),
          dataFetchParameters.getMetric());

      ListTimeSeriesResponse response;
      ThirdPartyApiCallLog apiCallLog = dataFetchParameters.getApiCallLog();
      try {
        List<String> groupBy = dataFetchParameters.getGroupByFields().isPresent()
            ? dataFetchParameters.getGroupByFields().get()
            : new ArrayList<>();
        Monitoring.Projects.TimeSeries.List list =
            dataFetchParameters.getMonitoring()
                .projects()
                .timeSeries()
                .list(projectResource)
                .setFilter(dataFetchParameters.getFilter())
                .setAggregationGroupByFields(groupBy)
                .setAggregationAlignmentPeriod("60s")
                .setIntervalStartTime(stackDriverDelegateService.getDateFormatTime(dataFetchParameters.getStartTime()))
                .setIntervalEndTime(stackDriverDelegateService.getDateFormatTime(dataFetchParameters.getEndTime()));
        if (dataFetchParameters.getPerSeriesAligner().isPresent()) {
          list = list.setAggregationPerSeriesAligner(dataFetchParameters.getPerSeriesAligner().get());
        }
        if (dataFetchParameters.getCrossSeriesReducer().isPresent()) {
          list.setAggregationCrossSeriesReducer(dataFetchParameters.getCrossSeriesReducer().get());
        }
        response = list.execute();
      } catch (Exception e) {
        apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
        apiCallLog.addFieldToResponse(HttpStatus.SC_BAD_REQUEST, ExceptionUtils.getStackTrace(e), FieldType.TEXT);
        delegateLogService.save(dataCollectionInfo.getGcpConfig().getAccountId(), apiCallLog);
        throw new WingsException(
            "Unsuccessful response while fetching data from StackDriver. Error message: " + e.getMessage());
      }
      apiCallLog.setResponseTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      apiCallLog.addFieldToResponse(HttpStatus.SC_OK, response, FieldType.JSON);

      delegateLogService.save(dataCollectionInfo.getGcpConfig().getAccountId(), apiCallLog);

      List<TimeSeries> dataPoints = response.getTimeSeries();

      if (!isEmpty(dataPoints)) {
        dataPoints.forEach(datapoint -> datapoint.getPoints().forEach(point -> {
          long timeStamp = stackDriverDelegateService.getTimeStamp(point.getInterval().getEndTime());
          NewRelicMetricDataRecord newRelicMetricDataRecord =
              NewRelicMetricDataRecord.builder()
                  .stateType(DelegateStateType.STACK_DRIVER)
                  .appId(appId)
                  .name(dataFetchParameters.getNameSpace())
                  .workflowId(dataCollectionInfo.getWorkflowId())
                  .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                  .serviceId(dataCollectionInfo.getServiceId())
                  .cvConfigId(dataCollectionInfo.getCvConfigId())
                  .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                  .timeStamp(timeStamp)
                  .dataCollectionMinute(
                      getCollectionMinute(timeStamp, analysisType, false, is247Task, dataFetchParameters.getStartTime(),
                          dataCollectionInfo.getDataCollectionMinute(), dataCollectionInfo.getCollectionTime()))
                  .host(dataFetchParameters.getDimensionValue())
                  .groupName(dataFetchParameters.getGroupName())
                  .tag(is247Task ? null : dataFetchParameters.getNameSpace())
                  .values(new HashMap<>())
                  .build();
          Double value = point.getValue().getDoubleValue();

          if (value == null) {
            value = 1.0 * point.getValue().getInt64Value();
          }
          newRelicMetricDataRecord.getValues().put(dataFetchParameters.getMetric(), value);

          rv.put(dataFetchParameters.getNameSpace() + dataFetchParameters.getDimensionValue(), timeStamp,
              newRelicMetricDataRecord);
        }));
      }
    }

    private int getCollectionMinute(long metricTimeStamp, TimeSeriesMlAnalysisType analysisType, boolean isHeartbeat,
        boolean is247Task, long startTime, int dataCollectionMinute, int collectionTime) {
      boolean isPredictiveAnalysis = analysisType == PREDICTIVE;
      int collectionMinute;
      if (isHeartbeat) {
        if (is247Task) {
          collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(startTime) + collectionTime;
        } else if (isPredictiveAnalysis) {
          collectionMinute = dataCollectionMinute + PREDECTIVE_HISTORY_MINUTES + DURATION_TO_ASK_MINUTES;
        } else {
          collectionMinute = dataCollectionMinute;
        }
      } else {
        if (is247Task) {
          collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp);
        } else {
          long collectionStartTime;
          if (isPredictiveAnalysis) {
            collectionStartTime = startTime - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES);
          } else {
            return (int) TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp);
          }
          collectionMinute = (int) (TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp - collectionStartTime));
        }
      }
      return collectionMinute;
    }

    private StackdriverDataFetchParameters createDataFetchParameters(long startTime, long endTime) {
      return StackdriverDataFetchParameters.builder()
          .startTime(startTime)
          .endTime(endTime)
          .is247Task(is247Task)
          .apiCallLog(ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())))
          .groupByFields(Optional.empty())
          .perSeriesAligner(Optional.empty())
          .dataCollectionMinute(dataCollectionCurrentMinute)
          .build();
    }

    public TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricsData() throws IOException {
      final TreeBasedTable<String, Long, NewRelicMetricDataRecord> metricDataResponses = TreeBasedTable.create();
      List<Callable<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> callables = new ArrayList<>();

      long startTime = collectionStartTime;
      long endTime = startTime + TimeUnit.MINUTES.toMillis(1);

      if (!isEmpty(dataCollectionInfo.getTimeSeriesToCollect())) {
        Map<String, String> hostToGroupNameMap = new HashMap<>();
        if (is24X7Task()) {
          hostToGroupNameMap.put("dummyHost", DEFAULT_GROUP_NAME);
        } else {
          hostToGroupNameMap = dataCollectionInfo.getHosts();
        }
        hostToGroupNameMap.forEach(
            (host, groupName) -> dataCollectionInfo.getTimeSeriesToCollect().forEach(timeSeriesDefinition -> {
              StackdriverDataFetchParameters dataFetchParameters = createDataFetchParameters(startTime, endTime);
              dataFetchParameters.setGroupName(groupName);
              dataFetchParameters.setDimensionValue(host);
              dataFetchParameters.setMetric(timeSeriesDefinition.getMetricName());
              dataFetchParameters.setNameSpace(timeSeriesDefinition.getTxnName());
              dataFetchParameters.setFilter(
                  CustomDataCollectionUtils.resolveField(timeSeriesDefinition.getFilter(), "${host}", host));
              dataFetchParameters.setGroupByFields(
                  Optional.ofNullable(timeSeriesDefinition.getAggregation().getGroupByFields()));
              dataFetchParameters.setPerSeriesAligner(
                  Optional.ofNullable(timeSeriesDefinition.getAggregation().getPerSeriesAligner()));
              dataFetchParameters.setCrossSeriesReducer(
                  Optional.ofNullable(timeSeriesDefinition.getAggregation().getCrossSeriesReducer()));
              if (is24X7Task()) {
                dataFetchParameters.setStartTime(dataCollectionInfo.getStartTime());
                dataFetchParameters.setEndTime(dataCollectionInfo.getStartTime()
                    + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getCollectionTime()));
              }
              callables.add(() -> getMetricDataRecords(dataFetchParameters));
            }));
      }

      log.debug("fetching stackdriver metrics for {} strategy {} for min {}", dataCollectionInfo.getStateExecutionId(),
          dataCollectionInfo.getTimeSeriesMlAnalysisType(), dataCollectionCurrentMinute);
      List<Optional<TreeBasedTable<String, Long, NewRelicMetricDataRecord>>> results = executeParallel(callables);
      log.debug("done fetching stackdriver metrics for {} strategy {} for min {}",
          dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getTimeSeriesMlAnalysisType(),
          dataCollectionCurrentMinute);
      results.forEach(result -> {
        if (result.isPresent()) {
          TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = result.get();
          for (Cell<String, Long, NewRelicMetricDataRecord> cell : records.cellSet()) {
            NewRelicMetricDataRecord metricDataRecord = metricDataResponses.get(cell.getRowKey(), cell.getColumnKey());
            if (metricDataRecord != null) {
              metricDataRecord.getValues().putAll(cell.getValue().getValues());
            } else {
              metricDataResponses.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
            }
          }
        }
      });
      return metricDataResponses;
    }
  }
}
