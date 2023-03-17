/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.cv.CVConstants.CANARY_DAYS_TO_COLLECT;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.DURATION_TO_ASK_MINUTES;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_CURRENT;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.time.Timestamp;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.CVConstants;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.dynatrace.DynaTraceDataCollectionInfo;
import software.wings.service.impl.dynatrace.DynaTraceMetricDataRequest;
import software.wings.service.impl.dynatrace.DynaTraceMetricDataResponse;
import software.wings.service.impl.dynatrace.DynaTraceTimeSeries;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;

import com.google.common.base.Preconditions;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.io.IOException;
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
import org.slf4j.Logger;

/**
 * Created by rsingh on 2/6/18.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class DynaTraceDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private DynaTraceDataCollectionInfo dataCollectionInfo;

  @Inject private DynaTraceDelegateService dynaTraceDelegateService;
  @Inject private MetricDataStoreService metricStoreService;

  public DynaTraceDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (DynaTraceDataCollectionInfo) parameters;
    log.info("metric collection - dataCollectionInfo: {}", dataCollectionInfo);
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskStatus.SUCCESS)
        .stateType(DelegateStateType.DYNA_TRACE)
        .build();
  }

  @Override
  protected DelegateStateType getStateType() {
    return DelegateStateType.DYNA_TRACE;
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equals(TaskType.DYNATRACE_COLLECT_24_7_METRIC_DATA.name());
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException {
    return new DynaTraceMetricCollector(dataCollectionInfo, taskResult, is24X7Task());
  }

  private class DynaTraceMetricCollector implements Runnable {
    private final DynaTraceDataCollectionInfo dataCollectionInfo;
    private final DataCollectionTaskResult taskResult;
    private long collectionStartTime;
    private int dataCollectionMinute;
    private Map<String, Long> hostStartTimeMap;
    private DynaTraceConfig dynaTraceConfig;
    boolean is247Task;
    boolean isMultiService;

    private DynaTraceMetricCollector(
        DynaTraceDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult, boolean is247Task) {
      this.dataCollectionInfo = dataCollectionInfo;
      this.taskResult = taskResult;
      this.collectionStartTime = Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
      this.dataCollectionMinute = dataCollectionInfo.getDataCollectionMinute();
      hostStartTimeMap = new HashMap<>();
      dynaTraceConfig = dataCollectionInfo.getDynaTraceConfig();
      this.is247Task = is247Task;
      if (dataCollectionInfo.getDynatraceServiceIds().size() > 1) {
        isMultiService = true;
      }
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      int retry = 0;
      while (!completed.get() && retry < RETRIES) {
        try {
          List<DynaTraceMetricDataResponse> metricsData = getMetricsData();
          TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = TreeBasedTable.create();
          if (isMultiService) {
            log.info("Collecting multiservice data from dynatrace for stateExecutionId: {}",
                dataCollectionInfo.getStateExecutionId());
          }
          // Heartbeat
          records.put(HARNESS_HEARTBEAT_METRIC_NAME, 0L,
              NewRelicMetricDataRecord.builder()
                  .stateType(getStateType())
                  .name(HARNESS_HEARTBEAT_METRIC_NAME)
                  .appId(dataCollectionInfo.getApplicationId())
                  .workflowId(dataCollectionInfo.getWorkflowId())
                  .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                  .serviceId(dataCollectionInfo.getServiceId())
                  .cvConfigId(dataCollectionInfo.getCvConfigId())
                  .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                  .dataCollectionMinute(getCollectionMinute(System.currentTimeMillis(), null, true))
                  .timeStamp(collectionStartTime)
                  .level(ClusterLevel.H0)
                  .groupName(DEFAULT_GROUP_NAME)
                  .build());

          records.putAll(processMetricData(metricsData));
          List<NewRelicMetricDataRecord> recordsToSave = getAllMetricRecords(records);
          if (!saveMetrics(dynaTraceConfig.getAccountId(), dataCollectionInfo.getApplicationId(),
                  dataCollectionInfo.getStateExecutionId(), recordsToSave)) {
            log.error("Error saving metrics to the database. DatacollectionMin: {} StateexecutionId: {}",
                dataCollectionMinute, dataCollectionInfo.getStateExecutionId());
          } else {
            log.info("Sent {} Dynatrace metric records to the server for minute {}", recordsToSave.size(),
                dataCollectionMinute);
          }

          dataCollectionMinute++;
          collectionStartTime += TimeUnit.MINUTES.toMillis(1);
          if (dataCollectionMinute >= dataCollectionInfo.getCollectionTime() || is247Task) {
            // We are done with all data collection, so setting task status to success and quitting.
            log.info(
                "Completed Dynatrace collection task. So setting task status to success and quitting. StateExecutionId {}",
                dataCollectionInfo.getStateExecutionId());
            completed.set(true);
            taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
          }
          break;

        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching metrics for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                dataCollectionMinute, ex);
            taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
            completed.set(true);
            break;
          } else {
            if (retry == 1) {
              if (ex instanceof WingsException) {
                taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
              }
            }
            log.warn("error fetching Dynatrace metrics for minute " + dataCollectionMinute + ". retrying in "
                    + DATA_COLLECTION_RETRY_SLEEP + "s",
                ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }

      if (completed.get()) {
        log.info("Shutting down Dynatrace data collection");
        shutDownCollection();
        return;
      }
    }

    private int getCollectionMinute(final long metricTimeStamp, String host, boolean isHeartbeat) {
      boolean isPredictiveAnalysis =
          dataCollectionInfo.getAnalysisComparisonStrategy() == AnalysisComparisonStrategy.PREDICTIVE;
      int collectionMinute;
      if (isHeartbeat) {
        if (is247Task) {
          collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getStartTime())
              + dataCollectionInfo.getCollectionTime();
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
            collectionStartTime =
                dataCollectionInfo.getStartTime() - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES);
          } else {
            // This condition is needed as in case of COMPARE_WITH_CURRENT we keep track of startTime for each host.
            if (hostStartTimeMap.containsKey(host)) {
              collectionStartTime = hostStartTimeMap.get(host);
            } else {
              collectionStartTime = dataCollectionInfo.getStartTime();
            }
          }
          collectionMinute = (int) (TimeUnit.MILLISECONDS.toMinutes(metricTimeStamp - collectionStartTime));
        }
      }
      return collectionMinute;
    }
    /**
     * Method to fetch metric data
     *
     * @return List of DynaTraceMetricDataResponse
     * @throws IOException
     */
    public List<DynaTraceMetricDataResponse> getMetricsData() throws IOException {
      final List<EncryptedDataDetail> encryptionDetails = dataCollectionInfo.getEncryptedDataDetails();
      final List<DynaTraceMetricDataResponse> metricDataResponses = new ArrayList<>();
      List<Callable<DynaTraceMetricDataResponse>> callables = new ArrayList<>();
      long endTimeForCollection = System.currentTimeMillis();

      switch (dataCollectionInfo.getAnalysisComparisonStrategy()) {
        case COMPARE_WITH_PREVIOUS:
          for (DynaTraceTimeSeries timeSeries : dataCollectionInfo.getTimeSeriesDefinitions()) {
            callables.add(() -> {
              DynaTraceMetricDataRequest dataRequest =
                  DynaTraceMetricDataRequest.builder()
                      .timeseriesId(timeSeries.getTimeseriesId())
                      .entities(dataCollectionInfo.getDynatraceServiceIds() == null
                              ? null
                              : dataCollectionInfo.getDynatraceServiceIds())
                      .aggregationType(timeSeries.getAggregationType())
                      .percentile(timeSeries.getPercentile())
                      .startTimestamp(collectionStartTime)
                      .endTimestamp(collectionStartTime + TimeUnit.MINUTES.toMillis(2))
                      .build();

              DynaTraceMetricDataResponse metricDataResponse =
                  dynaTraceDelegateService.fetchMetricData(dynaTraceConfig, dataRequest, encryptionDetails,
                      ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
              metricDataResponse.getResult().setHost(CVConstants.TEST_HOST_NAME);
              return metricDataResponse;
            });
          }
          break;
        case COMPARE_WITH_CURRENT:
          final long startTime = collectionStartTime;
          final long endTime = collectionStartTime + TimeUnit.MINUTES.toMillis(2);

          for (int i = 0; i <= CANARY_DAYS_TO_COLLECT; i++) {
            String hostName = i == 0 ? CVConstants.TEST_HOST_NAME : CVConstants.CONTROL_HOST_NAME + i;
            long startTimeStamp = startTime - TimeUnit.DAYS.toMillis(i);
            long endTimeStamp = endTime - TimeUnit.DAYS.toMillis(i);
            hostStartTimeMap.put(hostName, startTimeStamp);
            for (DynaTraceTimeSeries timeSeries : dataCollectionInfo.getTimeSeriesDefinitions()) {
              callables.add(() -> {
                DynaTraceMetricDataRequest dataRequest =
                    DynaTraceMetricDataRequest.builder()
                        .timeseriesId(timeSeries.getTimeseriesId())
                        .entities(dataCollectionInfo.getDynatraceServiceIds() == null
                                ? null
                                : dataCollectionInfo.getDynatraceServiceIds())
                        .aggregationType(timeSeries.getAggregationType())
                        .percentile(timeSeries.getPercentile())
                        .startTimestamp(startTimeStamp)
                        .endTimestamp(endTimeStamp)
                        .build();

                DynaTraceMetricDataResponse metricDataResponse =
                    dynaTraceDelegateService.fetchMetricData(dynaTraceConfig, dataRequest, encryptionDetails,
                        ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
                metricDataResponse.getResult().setHost(hostName);
                return metricDataResponse;
              });
            }
          }
          break;
        case PREDICTIVE:
          long startTimeStamp;
          long endTimeStamp;
          long periodToCollect = is247Task  ? dataCollectionInfo.getCollectionTime()
              : (dataCollectionMinute == 0) ? PREDECTIVE_HISTORY_MINUTES + DURATION_TO_ASK_MINUTES
                                            : DURATION_TO_ASK_MINUTES;
          periodToCollect = TimeUnit.MINUTES.toMillis(periodToCollect);

          if (is247Task) {
            startTimeStamp = collectionStartTime;
            endTimeStamp = startTimeStamp + periodToCollect;
          } else {
            startTimeStamp = endTimeForCollection - periodToCollect;
            endTimeStamp = endTimeForCollection;
          }
          for (DynaTraceTimeSeries timeSeries : dataCollectionInfo.getTimeSeriesDefinitions()) {
            callables.add(() -> {
              DynaTraceMetricDataRequest dataRequest = DynaTraceMetricDataRequest.builder()
                                                           .timeseriesId(timeSeries.getTimeseriesId())
                                                           .entities(dataCollectionInfo.getDynatraceServiceIds() == null
                                                                   ? null
                                                                   : dataCollectionInfo.getDynatraceServiceIds())
                                                           .aggregationType(timeSeries.getAggregationType())
                                                           .percentile(timeSeries.getPercentile())
                                                           .startTimestamp(startTimeStamp)
                                                           .endTimestamp(endTimeStamp)
                                                           .build();
              return dynaTraceDelegateService.fetchMetricData(dynaTraceConfig, dataRequest, encryptionDetails,
                  ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
            });
          }
          break;
        default:
          throw new WingsException("invalid strategy " + dataCollectionInfo.getAnalysisComparisonStrategy());
      }

      log.info("fetching dynatrace metrics for {} strategy {} for min {}", dataCollectionInfo.getStateExecutionId(),
          dataCollectionInfo.getAnalysisComparisonStrategy(), dataCollectionMinute);
      List<Optional<DynaTraceMetricDataResponse>> results = executeParallel(callables);
      log.info("done fetching dynatrace metrics for {} strategy {} for min {}",
          dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getAnalysisComparisonStrategy(),
          dataCollectionMinute);
      results.forEach(result -> {
        if (result.isPresent()) {
          metricDataResponses.add(result.get());
        }
      });
      return metricDataResponses;
    }

    private TreeBasedTable<String, Long, NewRelicMetricDataRecord> processMetricData(
        List<DynaTraceMetricDataResponse> metricsData) {
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = TreeBasedTable.create();
      metricsData.forEach(dataResponse -> {
        String timeSeriesId = dataResponse.getResult().getTimeseriesId();
        dataResponse.getResult().getEntities().forEach((serviceMethodName, serviceMethodDesc) -> {
          String btName = serviceMethodDesc + ":" + serviceMethodName;

          List<List<Double>> dataPoints = dataResponse.getResult().getDataPoints().get(serviceMethodName);

          dataPoints.forEach(dataPoint -> {
            Double timeStamp = dataPoint.get(0);
            Double value = dataPoint.get(1);

            if (value != null) {
              DynaTraceTimeSeries timeSeries = DynaTraceTimeSeries.getTimeSeries(timeSeriesId);
              Preconditions.checkNotNull(timeSeries, "could not find timeSeries " + timeSeriesId);

              NewRelicMetricDataRecord metricDataRecord = records.get(btName, timeStamp.longValue());
              if (metricDataRecord == null) {
                metricDataRecord =
                    NewRelicMetricDataRecord.builder()
                        .name(btName)
                        .appId(dataCollectionInfo.getApplicationId())
                        .workflowId(dataCollectionInfo.getWorkflowId())
                        .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                        .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                        .serviceId(dataCollectionInfo.getServiceId())
                        .cvConfigId(dataCollectionInfo.getCvConfigId())
                        .dataCollectionMinute(getCollectionMinute(
                            Timestamp.minuteBoundary(timeStamp.longValue()), dataResponse.getResult().getHost(), false))
                        .timeStamp(timeStamp.longValue())
                        .stateType(DelegateStateType.DYNA_TRACE)
                        .host(dataResponse.getResult().getHost())
                        .values(new HashMap<>())
                        .groupName(DEFAULT_GROUP_NAME)
                        .build();
                if (metricDataRecord.getTimeStamp() >= dataCollectionInfo.getStartTime()
                    || dataCollectionInfo.getAnalysisComparisonStrategy() == COMPARE_WITH_CURRENT) {
                  records.put(btName, timeStamp.longValue(), metricDataRecord);
                } else {
                  log.info("Metric record for stateExecutionId {} is before the startTime. Ignoring.",
                      dataCollectionInfo.getStateExecutionId());
                }
              }

              metricDataRecord.getValues().put(timeSeries.getSavedFieldName(), value);
            }
          });
        });
      });
      return records;
    }
  }
}
