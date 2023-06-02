/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl.METRIC_NAME_NON_SPECIAL_CHARS;
import static software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl.METRIC_NAME_SPECIAL_CHARS;
import static software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl.batchMetricsToCollect;
import static software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl.getApdexMetricNames;
import static software.wings.service.impl.newrelic.NewRelicDelgateServiceImpl.getErrorMetricNames;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.APDEX_SCORE;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.AVERAGE_RESPONSE_TIME;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.CALL_COUNT;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.ERROR;
import static software.wings.service.impl.newrelic.NewRelicMetricValueDefinition.REQUSET_PER_MINUTE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;
import io.harness.time.Timestamp;

import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.newrelic.NewRelicApdex;
import software.wings.service.impl.newrelic.NewRelicApplicationInstance;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicErrors;
import software.wings.service.impl.newrelic.NewRelicMetric;
import software.wings.service.impl.newrelic.NewRelicMetricData;
import software.wings.service.impl.newrelic.NewRelicMetricData.NewRelicMetricSlice;
import software.wings.service.impl.newrelic.NewRelicMetricData.NewRelicMetricTimeSlice;
import software.wings.service.impl.newrelic.NewRelicWebTransactions;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;

import com.google.common.collect.Table.Cell;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by rsingh on 5/18/17.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NewRelicDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private static final String ALL_WEB_TXN_NAME = "WebTransaction/all";
  private static final String ALL_ERRORS_TXN_NAME = "Errors/all";
  private static final String OVERALL_APDEX_TXN_NAME = "Apdex";
  private static final int PERIOD_MINS = 1;

  @Inject private NewRelicDelegateService newRelicDelegateService;
  @Inject private MetricDataStoreService metricStoreService;
  private NewRelicDataCollectionInfo dataCollectionInfo;
  private boolean is247Task;

  public NewRelicDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (NewRelicDataCollectionInfo) parameters;
    is247Task = this.getTaskType().equals(TaskType.NEWRELIC_COLLECT_24_7_METRIC_DATA.name());
    log.info("metric collection - dataCollectionInfo: {}", dataCollectionInfo);
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskStatus.SUCCESS)
        .stateType(DelegateStateType.NEW_RELIC)
        .build();
  }

  @Override
  protected DelegateStateType getStateType() {
    return DelegateStateType.NEW_RELIC;
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException {
    return new NewRelicMetricCollector(dataCollectionInfo, taskResult, is247Task);
  }

  @Override
  protected boolean is24X7Task() {
    return is247Task;
  }

  @Override
  protected int getPeriodSeconds() {
    return (int) TimeUnit.MINUTES.toSeconds(1) * PERIOD_MINS;
  }

  private class NewRelicMetricCollector implements Runnable {
    private final NewRelicDataCollectionInfo dataCollectionInfo;
    private long windowStartTimeManager;
    private int dataCollectionMinute;
    private DataCollectionTaskResult taskResult;
    private final Set<NewRelicMetric> allTxns;
    private long managerAnalysisStartTime;
    private TimeSeriesMlAnalysisType analysisType;
    private boolean is247Task;
    private int maxDataCollectionMin24x7;

    private NewRelicMetricCollector(NewRelicDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult,
        boolean is247Task) throws IOException {
      this.dataCollectionInfo = dataCollectionInfo;
      this.managerAnalysisStartTime = Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
      this.windowStartTimeManager = Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
      this.dataCollectionMinute = 0;
      this.is247Task = is247Task;
      this.taskResult = taskResult;
      this.allTxns = newRelicDelegateService.getTxnNameToCollect(dataCollectionInfo.getNewRelicConfig(),
          dataCollectionInfo.getEncryptedDataDetails(), dataCollectionInfo.getNewRelicAppId(),
          ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
      this.analysisType = dataCollectionInfo.getTimeSeriesMlAnalysisType();
      log.info("NewRelic collector initialized : managerAnalysisStartTime - {}, windowStartTimeManager {}",
          managerAnalysisStartTime, windowStartTimeManager);
    }

    private TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricData(
        NewRelicApplicationInstance node, Set<String> metricNames, long endTime, boolean failOnException) {
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = TreeBasedTable.create();

      log.debug("Fetching for host {} for stateExecutionId {} for metrics {}", node,
          dataCollectionInfo.getStateExecutionId(), metricNames);
      getWebTransactionMetrics(node, metricNames, endTime, records, failOnException);
      getErrorMetrics(node, metricNames, endTime, records, failOnException);
      getApdexMetrics(node, metricNames, endTime, records, failOnException);
      log.debug("Fetching done for host {} for stateExecutionId {} for metrics {}", node,
          dataCollectionInfo.getStateExecutionId(), metricNames);

      log.debug(records.toString());
      return records;
    }

    private int getCollectionMinute(long timeStamp) {
      if (is247Task) {
        return (int) TimeUnit.MILLISECONDS.toMinutes(timeStamp);
      }
      long analysisStartTime = isPredictiveAnalysis()
          ? managerAnalysisStartTime - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES)
          : managerAnalysisStartTime;
      return (int) ((timeStamp - analysisStartTime) / TimeUnit.MINUTES.toMillis(1));
    }

    private long getStartTime() {
      long startTime = windowStartTimeManager;
      if (isPredictiveAnalysis() && dataCollectionMinute == 0 && !is247Task) {
        startTime = windowStartTimeManager - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES);
      }
      return startTime;
    }

    private NewRelicMetricData getPredictiveOrComparativeMetricData(
        final Set<String> metricNames, long endTime, NewRelicApplicationInstance node) throws IOException {
      NewRelicMetricData metricData = null;
      if (isPredictiveAnalysis()) {
        metricData = newRelicDelegateService.getMetricDataApplication(dataCollectionInfo.getNewRelicConfig(),
            dataCollectionInfo.getEncryptedDataDetails(), dataCollectionInfo.getNewRelicAppId(), metricNames,
            getStartTime(), endTime, false,
            ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
      } else {
        metricData = newRelicDelegateService.getMetricDataApplicationInstance(dataCollectionInfo.getNewRelicConfig(),
            dataCollectionInfo.getEncryptedDataDetails(), dataCollectionInfo.getNewRelicAppId(), node.getId(),
            metricNames, getStartTime(), endTime,
            ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
      }
      return metricData;
    }

    private void getWebTransactionMetrics(NewRelicApplicationInstance node, Set<String> metricNames, long endTime,
        TreeBasedTable<String, Long, NewRelicMetricDataRecord> records, boolean failOnException) {
      int retry = 0;
      while (retry < RETRIES) {
        try {
          log.debug("For datacollectionMinute {}, start and end times are {} and {}", dataCollectionMinute,
              windowStartTimeManager, endTime);
          NewRelicMetricData metricData = getPredictiveOrComparativeMetricData(metricNames, endTime, node);

          for (NewRelicMetricSlice metric : metricData.getMetrics()) {
            for (NewRelicMetricTimeSlice timeSlice : metric.getTimeslices()) {
              // set from time to the timestamp
              long timeStamp = TimeUnit.SECONDS.toMillis(OffsetDateTime.parse(timeSlice.getFrom()).toEpochSecond());
              Optional<NewRelicMetricDataRecord> optionalNewRelicMetricDataRecord =
                  createNewDataRecord(node, metric.getName(), timeStamp);
              optionalNewRelicMetricDataRecord.ifPresent(metricDataRecord -> {
                final String webTxnJson = JsonUtils.asJson(timeSlice.getValues());
                NewRelicWebTransactions webTransactions = JsonUtils.asObject(webTxnJson, NewRelicWebTransactions.class);
                metricDataRecord.getValues().put(AVERAGE_RESPONSE_TIME, webTransactions.getAverage_response_time());
                metricDataRecord.getValues().put(CALL_COUNT, (double) webTransactions.getCall_count());
                metricDataRecord.getValues().put(REQUSET_PER_MINUTE, (double) webTransactions.getRequests_per_minute());
                records.put(metric.getName(), timeStamp, metricDataRecord);
              });
            }
          }
          break;
        } catch (Exception e) {
          log.info("Error fetching metrics for node: " + node + ", retry: " + retry + ", metrics: " + metricNames, e);
          if (!failOnException) {
            log.info("no retrying or failing for node {}, metrics {}", node, metricNames);
            return;
          }
          retry++;
          if (retry >= RETRIES) {
            throw new WingsException(
                "Fetching for web transaction metrics failed. reason " + ExceptionUtils.getMessage(e));
          }
        }
      }
    }

    private Optional<NewRelicMetricDataRecord> createNewDataRecord(
        NewRelicApplicationInstance node, String metricName, long timeStamp) {
      String hostname = isPredictiveAnalysis() ? DEFAULT_GROUP_NAME : node.getHost();
      String groupName = isEmpty(dataCollectionInfo.getHosts().get(node.getHost()))
          ? DEFAULT_GROUP_NAME
          : dataCollectionInfo.getHosts().get(node.getHost());
      final NewRelicMetricDataRecord metricDataRecord =
          NewRelicMetricDataRecord.builder()
              .name(metricName)
              .appId(dataCollectionInfo.getApplicationId())
              .workflowId(dataCollectionInfo.getWorkflowId())
              .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
              .serviceId(dataCollectionInfo.getServiceId())
              .stateExecutionId(dataCollectionInfo.getStateExecutionId())
              .stateType(getStateType())
              .timeStamp(timeStamp)
              .cvConfigId(dataCollectionInfo.getCvConfigId())
              .host(hostname)
              .values(new HashMap<>())
              .groupName(groupName)
              .build();

      int dataCollectionMinForRecord = getCollectionMinute(timeStamp);
      if (is247Task && dataCollectionMinForRecord > maxDataCollectionMin24x7) {
        maxDataCollectionMin24x7 = dataCollectionMinForRecord;
      }
      metricDataRecord.setDataCollectionMinute(dataCollectionMinForRecord);

      if (metricDataRecord.getDataCollectionMinute() < 0
          || (is247Task
              && metricDataRecord.getDataCollectionMinute()
                  < TimeUnit.MILLISECONDS.toMinutes(Timestamp.minuteBoundary(windowStartTimeManager)))) {
        log.info("New relic sending us data in the past. request start time {}, received time {}",
            managerAnalysisStartTime, timeStamp);
        return Optional.empty();
      }
      return Optional.of(metricDataRecord);
    }

    private void getErrorMetrics(NewRelicApplicationInstance node, Set<String> metricNames, long endTime,
        TreeBasedTable<String, Long, NewRelicMetricDataRecord> records, boolean failOnException) {
      // get error metrics
      int retry = 0;
      while (retry < RETRIES) {
        try {
          NewRelicMetricData metricData =
              getPredictiveOrComparativeMetricData(getErrorMetricNames(metricNames), endTime, node);

          for (NewRelicMetricSlice metric : metricData.getMetrics()) {
            for (NewRelicMetricTimeSlice timeslice : metric.getTimeslices()) {
              long timeStamp = TimeUnit.SECONDS.toMillis(OffsetDateTime.parse(timeslice.getFrom()).toEpochSecond());
              String metricName = metric.getName().equals(ALL_ERRORS_TXN_NAME)
                  ? metric.getName()
                  : metric.getName().replace("Errors/", "");

              NewRelicMetricDataRecord metricDataRecord = records.get(metricName, timeStamp);
              if (metricDataRecord == null) {
                Optional<NewRelicMetricDataRecord> optionalRecord = createNewDataRecord(node, metricName, timeStamp);
                if (optionalRecord.isPresent()) {
                  metricDataRecord = optionalRecord.get();
                }
              }
              if (metricDataRecord != null) {
                final String errorsJson = JsonUtils.asJson(timeslice.getValues());
                NewRelicErrors errors = JsonUtils.asObject(errorsJson, NewRelicErrors.class);
                metricDataRecord.getValues().put(ERROR, (double) errors.getError_count());
                records.put(metricName, timeStamp, metricDataRecord);
              }
            }
          }

          break;
        } catch (Exception e) {
          log.warn("Error fetching metrics for node: " + node + ", retry: " + retry + ", metrics: " + metricNames, e);
          if (!failOnException) {
            log.info("no retrying or failing for node {}, metrics {}", node, metricNames);
            return;
          }
          retry++;
          if (retry >= RETRIES) {
            throw new WingsException("Fetching for error metrics failed. reason " + ExceptionUtils.getMessage(e));
          }
        }
      }
    }

    private void getApdexMetrics(NewRelicApplicationInstance node, Set<String> metricNames, long endTime,
        TreeBasedTable<String, Long, NewRelicMetricDataRecord> records, boolean failOnException) {
      // get apdex metrics
      int retry = 0;
      while (retry < RETRIES) {
        try {
          Set<String> apdexMetricNames = getApdexMetricNames(metricNames);
          if (isEmpty(apdexMetricNames)) {
            return;
          }
          NewRelicMetricData metricData =
              getPredictiveOrComparativeMetricData(getApdexMetricNames(metricNames), endTime, node);
          for (NewRelicMetricSlice metric : metricData.getMetrics()) {
            for (NewRelicMetricTimeSlice timeslice : metric.getTimeslices()) {
              long timeStamp = TimeUnit.SECONDS.toMillis(OffsetDateTime.parse(timeslice.getFrom()).toEpochSecond());
              String metricName = metric.getName().equals(OVERALL_APDEX_TXN_NAME)
                  ? metric.getName()
                  : metric.getName().replace("Apdex", "WebTransaction");

              NewRelicMetricDataRecord metricDataRecord = records.get(metricName, timeStamp);
              if (metricDataRecord == null) {
                Optional<NewRelicMetricDataRecord> optionalRecord = createNewDataRecord(node, metricName, timeStamp);
                if (optionalRecord.isPresent()) {
                  metricDataRecord = optionalRecord.get();
                }
              }
              if (metricDataRecord != null) {
                final String apdexJson = JsonUtils.asJson(timeslice.getValues());
                NewRelicApdex apdex = JsonUtils.asObject(apdexJson, NewRelicApdex.class);
                metricDataRecord.getValues().put(APDEX_SCORE, apdex.getScore());
                records.put(metricName, timeStamp, metricDataRecord);
              }
            }
          }

          break;
        } catch (Exception e) {
          log.warn("Error fetching metrics for node: " + node + ", retry: " + retry + ", metrics: " + metricNames, e);
          if (!failOnException) {
            log.info("no retrying or failing for node {}, metrics {}", node, metricNames);
            return;
          }
          retry++;
          if (retry >= RETRIES) {
            throw new WingsException("Fetching for apdex metrics failed. reason " + ExceptionUtils.getMessage(e));
          }
        }
      }
    }

    private Set<NewRelicMetric> getTxnsToCollect(boolean checkNotAllowedStrings) throws IOException {
      log.debug("Collecting txn names for {}", dataCollectionInfo);
      log.debug("all txns so far {} for {}", allTxns.size(), dataCollectionInfo.getStateExecutionId());
      Set<NewRelicMetric> newTxns = newRelicDelegateService.getTxnNameToCollect(dataCollectionInfo.getNewRelicConfig(),
          dataCollectionInfo.getEncryptedDataDetails(), dataCollectionInfo.getNewRelicAppId(),
          ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
      newTxns.removeAll(allTxns);
      log.debug("new txns {} for {}", newTxns.size(), dataCollectionInfo.getStateExecutionId());
      Set<NewRelicMetric> txnsWithData = newRelicDelegateService.getTxnsWithDataInLastHour(allTxns,
          dataCollectionInfo.getNewRelicConfig(), dataCollectionInfo.getEncryptedDataDetails(),
          dataCollectionInfo.getNewRelicAppId(), checkNotAllowedStrings,
          ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
      log.debug("txns with data {} for {}", txnsWithData.size(), dataCollectionInfo.getStateExecutionId());
      txnsWithData.addAll(newTxns);
      log.debug("txns to collect {} for {}", txnsWithData.size(), dataCollectionInfo.getStateExecutionId());
      return txnsWithData;
    }

    private int timeDeltaInMins(long t2Millis, long t1Millis) {
      return (int) ((t2Millis - t1Millis) / TimeUnit.MINUTES.toMillis(1));
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      int retry = 0;
      long startTime = System.currentTimeMillis();
      final long windowEndTimeManager = windowStartTimeManager + TimeUnit.MINUTES.toMillis(PERIOD_MINS);
      int collectionLength = timeDeltaInMins(windowEndTimeManager, windowStartTimeManager);
      int dataCollectionMinuteEnd = dataCollectionMinute + collectionLength - 1;
      log.info("Running new relic data collection for minute {}, state execution {}", dataCollectionMinuteEnd,
          dataCollectionInfo.getStateExecutionId());

      while (!completed.get() && retry < RETRIES) {
        try {
          Set<NewRelicMetric> txnsToCollect = getTxnsToCollect(dataCollectionInfo.isCheckNotAllowedStrings());
          log.debug("Found total new relic metrics " + txnsToCollect.size());
          List<NewRelicApplicationInstance> instances =
              newRelicDelegateService.getApplicationInstances(dataCollectionInfo.getNewRelicConfig(),
                  dataCollectionInfo.getEncryptedDataDetails(), dataCollectionInfo.getNewRelicAppId(),
                  ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId())));
          log.debug("Got {} new relic nodes.", instances.size());

          Map<String, List<Set<String>>> metricBatches =
              batchMetricsToCollect(txnsToCollect, dataCollectionInfo.isCheckNotAllowedStrings());
          log.debug("Found total new relic metric batches " + metricBatches.size());

          List<Callable<Boolean>> callables = new ArrayList<>();
          if (isPredictiveAnalysis()) {
            final long endTimeForCollection = is247Task
                ? windowStartTimeManager + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getCollectionTime())
                : windowEndTimeManager;

            log.debug("AnalysisType is Predictive. So we're collecting metrics by application instead of host/node");
            callables.add(()
                              -> fetchAndSaveMetricsForNode(
                                  NewRelicApplicationInstance.builder().host(DEFAULT_GROUP_NAME).build(), metricBatches,
                                  endTimeForCollection));
          } else {
            for (NewRelicApplicationInstance node : instances) {
              if (!dataCollectionInfo.getHosts().keySet().contains(node.getHost())) {
                log.debug("Skipping host {} for stateExecutionId {} ", node.getHost(),
                    dataCollectionInfo.getStateExecutionId());
                continue;
              }

              log.debug("Going to collect for host {} for stateExecutionId {}, for metrics {}", node.getHost(),
                  dataCollectionInfo.getStateExecutionId(), metricBatches);
              callables.add(() -> fetchAndSaveMetricsForNode(node, metricBatches, windowEndTimeManager));
            }
          }
          log.debug("submitting parallel tasks {}", callables.size());
          List<Optional<Boolean>> results = executeParallel(callables);
          for (Optional<Boolean> result : results) {
            if (!result.isPresent() || !result.get()) {
              log.error("Error saving metrics to the database. DatacollectionMin: {} StateexecutionId: {}",
                  dataCollectionMinute, dataCollectionInfo.getStateExecutionId());
            }
          }

          int dataCollectionMinForHeartbeat = dataCollectionMinuteEnd;
          if (is247Task) {
            dataCollectionMinForHeartbeat = maxDataCollectionMin24x7 != 0
                ? maxDataCollectionMin24x7
                : (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getStartTime())
                    + dataCollectionInfo.getCollectionTime();
          }

          if (!saveHeartBeats(dataCollectionMinForHeartbeat)) {
            log.error("Error saving heartbeat to the database. DatacollectionMin: {} StateexecutionId: {}",
                dataCollectionMinute, dataCollectionInfo.getStateExecutionId());
          }

          log.info("done processing parallel tasks {}", callables.size());
          windowStartTimeManager = windowEndTimeManager;

          dataCollectionMinute = dataCollectionMinuteEnd + 1;

          if (is247Task || dataCollectionMinute >= dataCollectionInfo.getCollectionTime()) {
            // We are done with all data collection, so setting task status to success and quitting.
            log.info("Completed NewRelic collection task. So setting task status to success and quitting");
            completed.set(true);
            taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
          }
          log.info("Time take for data collection: " + (System.currentTimeMillis() - startTime));
          break;
        } catch (Throwable ex) {
          if (!(ex instanceof Exception) || ++retry >= RETRIES) {
            log.error("error fetching metrics for {} for minute {}", dataCollectionInfo.getStateExecutionId(),
                dataCollectionMinute, ex);
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
            log.warn("error fetching new relic metrics for {} for minute {}. retrying in {}s",
                dataCollectionInfo.getStateExecutionId(), dataCollectionMinute, DATA_COLLECTION_RETRY_SLEEP, ex);
            sleep(DATA_COLLECTION_RETRY_SLEEP);
          }
        }
      }

      if (completed.get()) {
        log.info("Shutting down new relic data collection for {}", dataCollectionInfo);
        shutDownCollection();
        return;
      }
    }

    private boolean saveHeartBeats(int dataCollectionMinuteEnd) {
      Set<String> groups = new HashSet<>();
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = TreeBasedTable.create();
      for (Map.Entry<String, String> entry : dataCollectionInfo.getHosts().entrySet()) {
        if (!groups.contains(entry.getValue())) {
          records.put(HARNESS_HEARTBEAT_METRIC_NAME, 0l,
              NewRelicMetricDataRecord.builder()
                  .stateType(getStateType())
                  .name(HARNESS_HEARTBEAT_METRIC_NAME)
                  .appId(dataCollectionInfo.getApplicationId())
                  .workflowId(dataCollectionInfo.getWorkflowId())
                  .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                  .serviceId(dataCollectionInfo.getServiceId())
                  .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                  .dataCollectionMinute(dataCollectionMinuteEnd)
                  .timeStamp(windowStartTimeManager)
                  .level(ClusterLevel.H0)
                  .cvConfigId(dataCollectionInfo.getCvConfigId())
                  .groupName(entry.getValue())
                  .build());
          log.debug("adding heartbeat new relic metric record for group {} for minute {}", entry.getValue(),
              dataCollectionMinuteEnd);
          groups.add(entry.getValue());
        }
      }
      List<NewRelicMetricDataRecord> metricRecords = getAllMetricRecords(records);
      log.debug(
          "Sending {} new relic heart  beat records for minute {}", records.cellSet().size(), dataCollectionMinuteEnd);
      return saveMetrics(dataCollectionInfo.getNewRelicConfig().getAccountId(), dataCollectionInfo.getApplicationId(),
          dataCollectionInfo.getStateExecutionId(), metricRecords);
    }

    private boolean fetchAndSaveMetricsForNode(
        NewRelicApplicationInstance node, Map<String, List<Set<String>>> metricBatches, long endTime) {
      TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = TreeBasedTable.create();
      final long startTime = System.currentTimeMillis();
      metricBatches.forEach((metricNamesType, batchedMetrics) -> {
        if (metricNamesType.equals(METRIC_NAME_NON_SPECIAL_CHARS)) {
          batchedMetrics.forEach(metrics -> records.putAll(getMetricData(node, metrics, endTime, true)));
          return;
        }

        if (metricNamesType.equals(METRIC_NAME_SPECIAL_CHARS)) {
          batchedMetrics.forEach(metrics -> records.putAll(getMetricData(node, metrics, endTime, false)));
          return;
        }

        throw new IllegalStateException("Invalid metric batch type " + metricNamesType);
      });

      List<NewRelicMetricDataRecord> metricRecords = getAllMetricRecords(records);
      if (log.isDebugEnabled()) {
        log.debug("Sending {} new relic metric records for node {} for minute {}. Time taken: {}",
            records.cellSet().size(), node, dataCollectionMinute, System.currentTimeMillis() - startTime);
      }
      return saveMetrics(dataCollectionInfo.getNewRelicConfig().getAccountId(), dataCollectionInfo.getApplicationId(),
          dataCollectionInfo.getStateExecutionId(), metricRecords);
    }

    private List<NewRelicMetricDataRecord> getAllMetricRecords(
        TreeBasedTable<String, Long, NewRelicMetricDataRecord> records) {
      List<NewRelicMetricDataRecord> rv = new ArrayList<>();
      for (Cell<String, Long, NewRelicMetricDataRecord> cell : records.cellSet()) {
        NewRelicMetricDataRecord value = cell.getValue();
        value.setName(value.getName().equals("WebTransaction") ? ALL_WEB_TXN_NAME
                                                               : value.getName().replace("WebTransaction/", ""));
        rv.add(value);
      }

      return rv;
    }
    private boolean isPredictiveAnalysis() {
      return analysisType == TimeSeriesMlAnalysisType.PREDICTIVE;
    }
  }
}
