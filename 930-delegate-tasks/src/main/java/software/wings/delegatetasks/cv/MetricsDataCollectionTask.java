/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.HARNESS_HEARTBEAT_METRIC_NAME;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;

import software.wings.delegatetasks.MetricDataStoreService;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class MetricsDataCollectionTask<T extends MetricsDataCollectionInfo> extends AbstractDataCollectionTask<T> {
  private MetricsDataCollector<T> metricsDataCollector;
  @Inject private MetricDataStoreService metricStoreService;

  public MetricsDataCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected void collectAndSaveData(MetricsDataCollectionInfo dataCollectionInfo) throws DataCollectionException {
    this.metricsDataCollector = (MetricsDataCollector<T>) getDataCollector();
    final List<NewRelicMetricDataRecord> newRelicMetrics = new ArrayList<>();
    final List<Callable<List<MetricElement>>> callables = new ArrayList<>();

    if (dataCollectionInfo.getHosts().isEmpty()) {
      callables.add(() -> metricsDataCollector.fetchMetrics());
    } else {
      Iterables.partition(dataCollectionInfo.getHosts(), metricsDataCollector.getHostBatchSize())
          .forEach(batch -> callables.add(() -> metricsDataCollector.fetchMetrics(batch)));
    }
    List<Optional<List<MetricElement>>> results = executeParallel(callables);
    results.forEach(result -> {
      if (result.isPresent()) {
        newRelicMetrics.addAll(
            result.get()
                .stream()
                .map(metricElement -> mapToNewRelicMetricDataRecord(dataCollectionInfo, metricElement))
                .collect(Collectors.toList()));
      }
    });
    log.info("Saving " + newRelicMetrics.size() + " metrics");
    Map<String, List<NewRelicMetricDataRecord>> group =
        newRelicMetrics.stream().collect(Collectors.groupingBy(NewRelicMetricDataRecord::getHost, Collectors.toList()));
    group.forEach((host, records) -> {
      log.info("Saving metric for host {}, number of records {}", host, records.size());
      save(dataCollectionInfo, records);
    });
    if (dataCollectionInfo.isShouldSendHeartbeat()) {
      log.info("Saving heartbeat");
      save(dataCollectionInfo, getHeartbeat(dataCollectionInfo));
    }
  }

  private NewRelicMetricDataRecord mapToNewRelicMetricDataRecord(
      DataCollectionInfoV2 dataCollectionInfo, MetricElement metricElement) {
    int dataCollectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(metricElement.getTimestamp());
    if (dataCollectionInfo.getDataCollectionStartTime() != null) {
      dataCollectionMinute = (int) (dataCollectionMinute
          - TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getDataCollectionStartTime().toEpochMilli()));
    }
    return NewRelicMetricDataRecord.builder()
        .name(metricElement.getName())
        .appId(dataCollectionInfo.getApplicationId())
        .workflowId(dataCollectionInfo.getWorkflowId())
        .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
        .serviceId(dataCollectionInfo.getServiceId())
        .stateExecutionId(dataCollectionInfo.getStateExecutionId())
        .stateType(dataCollectionInfo.getStateType())
        .timeStamp(metricElement.getTimestamp())
        .cvConfigId(dataCollectionInfo.getCvConfigId())
        .host(metricElement.getHost())
        .values(metricElement.getValues())
        .groupName(metricElement.getGroupName())
        .tag(metricElement.getTag())
        .dataCollectionMinute(dataCollectionMinute)
        .build();
  }

  private void save(MetricsDataCollectionInfo dataCollectionInfo, List<NewRelicMetricDataRecord> records)
      throws DataCollectionException {
    boolean response = false;
    try {
      response = metricStoreService.saveNewRelicMetrics(dataCollectionInfo.getAccountId(),
          dataCollectionInfo.getApplicationId(), dataCollectionInfo.getStateExecutionId(), getTaskId(), records);
    } catch (Exception e) {
      log.error("error saving new apm metrics StateExecutionId: {}, Size: {}, {}",
          dataCollectionInfo.getStateExecutionId(), records.size(), e);
    }
    if (!response) {
      throw new DataCollectionException("Unable to save metrics elements. Manager API returned false");
    }
  }

  protected List<NewRelicMetricDataRecord> getHeartbeat(MetricsDataCollectionInfo metricsDataCollectionInfo) {
    List<NewRelicMetricDataRecord> newRelicMetrics = new ArrayList<>();
    int heartbeatMin = (int) (TimeUnit.MILLISECONDS.toMinutes(metricsDataCollectionInfo.getEndTime().toEpochMilli()));
    Set<String> groups = new HashSet<>();
    for (Map.Entry<String, String> entry : metricsDataCollectionInfo.getHostsToGroupNameMap().entrySet()) {
      if (!groups.contains(entry.getValue())) {
        int dataCollectionMinute = heartbeatMin;
        log.info("Heartbeat min: " + heartbeatMin);
        if (metricsDataCollectionInfo.getDataCollectionStartTime() != null) {
          // unfortunately there is a inconsistency between how heartbeat is created for workflow and how it's created
          // for service guard.
          dataCollectionMinute = (int) ((dataCollectionMinute
                                            - TimeUnit.MILLISECONDS.toMinutes(
                                                metricsDataCollectionInfo.getDataCollectionStartTime().toEpochMilli()))
              - 1);
          log.info("dataCollectionMinute min: " + dataCollectionMinute);
        }
        newRelicMetrics.add(NewRelicMetricDataRecord.builder()
                                .stateType(metricsDataCollectionInfo.getStateType())
                                .name(HARNESS_HEARTBEAT_METRIC_NAME)
                                .appId(metricsDataCollectionInfo.getApplicationId())
                                .workflowId(metricsDataCollectionInfo.getWorkflowId())
                                .workflowExecutionId(metricsDataCollectionInfo.getWorkflowExecutionId())
                                .serviceId(metricsDataCollectionInfo.getServiceId())
                                .stateExecutionId(metricsDataCollectionInfo.getStateExecutionId())
                                .dataCollectionMinute(dataCollectionMinute)
                                .timeStamp(TimeUnit.MINUTES.toMillis(heartbeatMin))
                                .level(ClusterLevel.H0)
                                .cvConfigId(metricsDataCollectionInfo.getCvConfigId())
                                .groupName(entry.getValue())
                                .build());
        groups.add(entry.getValue());
      }
    }
    return newRelicMetrics;
  }
}
