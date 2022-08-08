/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static software.wings.delegatetasks.cv.CVConstants.DUMMY_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.TOTAL_HITS_PER_MIN_THRESHOLD;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;

import software.wings.delegatetasks.LogAnalysisStoreService;
import software.wings.service.impl.analysis.LogDataCollectionInfoV2;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class LogDataCollectionTask<T extends LogDataCollectionInfoV2> extends AbstractDataCollectionTask<T> {
  private LogDataCollector<T> logDataCollector;
  private LogDataCollectionInfoV2 dataCollectionInfo;
  @Inject private LogAnalysisStoreService logAnalysisStoreService;

  public LogDataCollectionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected void collectAndSaveData(LogDataCollectionInfoV2 dataCollectionInfo) throws DataCollectionException {
    this.logDataCollector = (LogDataCollector<T>) getDataCollector();
    this.dataCollectionInfo = dataCollectionInfo;
    final List<LogElement> logElements = new ArrayList<>();
    final List<Callable<List<LogElement>>> callables = new ArrayList<>();
    if (dataCollectionInfo.getHosts().isEmpty()) {
      if (dataCollectionInfo.isShouldSendHeartbeat()) {
        addHeartbeats(Optional.empty(), dataCollectionInfo, logElements);
      }
      callables.add(() -> logDataCollector.fetchLogs());
    } else {
      Iterables.partition(dataCollectionInfo.getHosts(), logDataCollector.getHostBatchSize())
          .forEach(batch -> callables.add(() -> logDataCollector.fetchLogs(batch)));
      if (dataCollectionInfo.isShouldSendHeartbeat()) {
        dataCollectionInfo.getHosts().forEach(
            host -> addHeartbeats(Optional.of(host), dataCollectionInfo, logElements));
      }
    }
    List<Optional<List<LogElement>>> results = executeParallel(callables);
    List<LogElement> allLogs = new ArrayList<>();

    results.forEach(result -> {
      if (result.isPresent()) {
        allLogs.addAll(result.get());
      }
    });
    validateLogCounts(allLogs);
    logElements.addAll(allLogs);
    save(dataCollectionInfo, logElements);
  }
  private void validateLogCounts(List<LogElement> allLogs) {
    long limitPerMinute = TOTAL_HITS_PER_MIN_THRESHOLD;
    long limitForTheDuration = limitPerMinute * getDuration().toMinutes();
    Map<String, Long> hostsCount =
        allLogs.stream().collect(Collectors.groupingBy(LogElement::getHost, Collectors.counting()));
    hostsCount.forEach((host, count) -> {
      if (count > limitForTheDuration) {
        String errorMsg = "Too many logs(" + count + ") for host " + host
            + ", Please refine your query. The threshold per minute is " + TOTAL_HITS_PER_MIN_THRESHOLD;
        getActivityLogger().error(errorMsg);
        throw new DataCollectionException(errorMsg);
      }
    });
  }

  private Duration getDuration() {
    return Duration.between(dataCollectionInfo.getStartTime(), dataCollectionInfo.getEndTime());
  }
  private void save(LogDataCollectionInfoV2 dataCollectionInfo, List<LogElement> logElements)
      throws DataCollectionException {
    try {
      boolean response = logAnalysisStoreService.save(dataCollectionInfo.getStateType(),
          dataCollectionInfo.getAccountId(), dataCollectionInfo.getApplicationId(), dataCollectionInfo.getCvConfigId(),
          dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getWorkflowId(),
          dataCollectionInfo.getWorkflowExecutionId(), dataCollectionInfo.getServiceId(), getTaskId(), logElements);
      if (!response) {
        throw new DataCollectionException("Unable to save log elements. Manager API returned false.");
      }
    } catch (IOException e) {
      throw new DataCollectionException(e);
    }
  }

  protected void addHeartbeats(
      Optional<String> host, LogDataCollectionInfoV2 logDataCollectionInfo, List<LogElement> logElements) {
    // end time is excluded for heartbeat creation.
    for (long heartbeatMin = TimeUnit.MILLISECONDS.toMinutes(logDataCollectionInfo.getStartTime().toEpochMilli());
         heartbeatMin <= TimeUnit.MILLISECONDS.toMinutes(logDataCollectionInfo.getEndTime().toEpochMilli() - 1);
         heartbeatMin++) {
      logElements.add(
          LogElement.builder()
              .query(logDataCollectionInfo.getQuery())
              .clusterLabel(String.valueOf(ClusterLevel.H2.getLevel()))
              .host(host.isPresent() ? host.get() : DUMMY_HOST_NAME) // TODO: we should get rid of this requirement and
                                                                     // everything should work without setting the host.
              .count(0)
              .logMessage("")
              .timeStamp(TimeUnit.MINUTES.toMillis(heartbeatMin))
              .logCollectionMinute((int) heartbeatMin)
              .build());
    }
  }
}
