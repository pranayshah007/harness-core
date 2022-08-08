/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.common.VerificationConstants.CANARY_DAYS_TO_COLLECT;
import static software.wings.delegatetasks.cv.CVConstants.CONTROL_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.TEST_HOST_NAME;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_CURRENT;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.PREDICTIVE;

import io.harness.beans.ExecutionStatus;
import io.harness.entities.CVTask;
import io.harness.entities.CVTask.CVTaskKeys;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.service.intfc.verification.CVTaskService;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class CVTaskServiceImpl implements CVTaskService {
  public static final int CV_TASK_TIMEOUT_MINUTES = 15;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private CVActivityLogService activityLogService;
  @Inject Clock clock;
  @Inject VerificationManagerClientHelper verificationManagerClientHelper;

  @Override
  public void saveCVTask(CVTask cvTask) {
    wingsPersistence.save(cvTask);
  }

  @Override
  public void createCVTasks(AnalysisContext context) {
    List<CVTask> cvTasks = new ArrayList<>();
    long startTime = TimeUnit.MINUTES.toMillis(context.getStartDataCollectionMinute());
    Instant dataCollectionStartTime =
        Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(context.getStartDataCollectionMinute()));
    if (context.getComparisonStrategy() == PREDICTIVE) {
      cvTasks.addAll(createCVTasksForPredictiveComparisionStrategy(context, startTime));
    }

    int timeDuration = context.getTimeDuration();
    if (COMPARE_WITH_CURRENT.equals(context.getComparisonStrategy()) && context.isHistoricalDataCollection()) {
      cvTasks.addAll(createCVTasksForHistoricalDataCollection(context, timeDuration));
      enqueueSequentialTasks(cvTasks);
      return;
    }
    for (int minute = 0; minute < timeDuration; minute++) {
      long startTimeMSForCurrentMinute = startTime + Duration.ofMinutes(minute).toMillis();
      DataCollectionInfoV2 copy = context.getDataCollectionInfov2().deepCopy();
      copy.setStartTime(Instant.ofEpochMilli(startTimeMSForCurrentMinute));
      Duration duration = Duration.ofMinutes(Math.min(timeDuration - minute, 1));
      copy.setEndTime(Instant.ofEpochMilli(startTimeMSForCurrentMinute + duration.toMillis()));
      copy.setDataCollectionStartTime(dataCollectionStartTime);
      CVTask cvTask =
          CVTask.builder()
              .accountId(context.getAccountId())
              .stateExecutionId(context.getStateExecutionId())
              .dataCollectionInfo(copy)
              .correlationId(context.getCorrelationId())
              .status(ExecutionStatus.WAITING)
              .validAfter(startTimeMSForCurrentMinute + Duration.ofSeconds(context.getInitialDelaySeconds()).toMillis())
              .build();
      cvTasks.add(cvTask);
    }
    enqueueSequentialTasks(cvTasks);
  }

  private String getHostNameForTestControl(int i) {
    return i == 0 ? TEST_HOST_NAME : CONTROL_HOST_NAME + "-" + i;
  }

  private List<CVTask> createCVTasksForHistoricalDataCollection(AnalysisContext context, int timeDuration) {
    List<CVTask> cvTasks = new ArrayList<>();
    Instant dataCollectionStartTime =
        Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(context.getStartDataCollectionMinute()));
    long startTime = TimeUnit.MINUTES.toMillis(context.getStartDataCollectionMinute());
    for (int minute = 0; minute < timeDuration; minute++) {
      long startTimeMillis = startTime + Duration.ofMinutes(minute).toMillis();
      long endTimeMillis = startTimeMillis + Duration.ofMinutes(Math.min(timeDuration - minute, 1)).toMillis();
      for (int i = 0; i <= CANARY_DAYS_TO_COLLECT; i++) {
        String hostName = getHostNameForTestControl(i);
        long thisEndTime = endTimeMillis - TimeUnit.DAYS.toMillis(i);
        long thisStartTime = startTimeMillis - TimeUnit.DAYS.toMillis(i);
        DataCollectionInfoV2 copy = context.getDataCollectionInfov2().deepCopy();
        copy.setStartTime(Instant.ofEpochMilli(thisStartTime));
        copy.setEndTime(Instant.ofEpochMilli(thisEndTime));
        copy.setHosts(Sets.newHashSet(hostName));
        copy.setDataCollectionStartTime(dataCollectionStartTime.minus(i, ChronoUnit.DAYS));
        copy.setShouldSendHeartbeat(false);
        CVTask cvTask =
            CVTask.builder()
                .accountId(context.getAccountId())
                .stateExecutionId(context.getStateExecutionId())
                .dataCollectionInfo(copy)
                .correlationId(context.getCorrelationId())
                .status(ExecutionStatus.WAITING)
                .validAfter(startTimeMillis + Duration.ofSeconds(context.getInitialDelaySeconds()).toMillis())
                .build();
        cvTasks.add(cvTask);
      }
      cvTasks.get(cvTasks.size() - 1).getDataCollectionInfo().setShouldSendHeartbeat(true);
    }

    return cvTasks;
  }

  private List<CVTask> createCVTasksForPredictiveComparisionStrategy(AnalysisContext context, long startTimeMillis) {
    List<CVTask> cvTasks = new ArrayList<>();
    DataCollectionInfoV2 predictiveDataCollectionInfo = context.getDataCollectionInfov2().deepCopy();

    Instant endTime = Instant.ofEpochMilli(startTimeMillis);
    Instant startTime = endTime.minus(Duration.ofMinutes(context.getPredictiveHistoryMinutes()));

    predictiveDataCollectionInfo.setHosts(Collections.emptySet());
    while (startTime.compareTo(endTime) < 0) {
      DataCollectionInfoV2 copy = predictiveDataCollectionInfo.deepCopy();
      copy.setStartTime(startTime);
      Instant currentEndTime = ObjectUtils.min(startTime.plus(Duration.ofMinutes(15)), endTime);
      copy.setEndTime(currentEndTime);
      startTime = currentEndTime;
      cvTasks.add(CVTask.builder()
                      .accountId(context.getAccountId())
                      .stateExecutionId(context.getStateExecutionId())
                      .dataCollectionInfo(copy)
                      .correlationId(context.getCorrelationId())
                      .status(ExecutionStatus.WAITING)
                      .build());
    }

    return cvTasks;
  }

  @Override
  public void enqueueSequentialTasks(List<CVTask> cvTasks) {
    CVTask lastCVTask = null;
    for (CVTask cvTask : cvTasks) {
      cvTask.setStatus(ExecutionStatus.WAITING);
      this.saveCVTask(cvTask); // assigns uuid
      if (lastCVTask != null) {
        lastCVTask.setNextTaskId(cvTask.getUuid());
      }
      lastCVTask = cvTask;
    }
    if (cvTasks.size() > 0) {
      cvTasks.get(0).setStatus(ExecutionStatus.QUEUED);
    }
    // TODO - we should do bulk write here
    cvTasks.forEach(cvTask -> this.saveCVTask(cvTask));
  }

  @Override
  public Optional<CVTask> getNextTask(String accountId) {
    CVTask cvTask = wingsPersistence.createQuery(CVTask.class)
                        .filter(CVTaskKeys.accountId, accountId)
                        .filter(CVTaskKeys.status, ExecutionStatus.QUEUED)
                        .filter(CVTaskKeys.validAfter + " <=", clock.millis())
                        .order(CVTaskKeys.lastUpdatedAt)
                        .get();
    if (cvTask == null) {
      return Optional.empty();
    }
    cvTask.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.updateField(CVTask.class, cvTask.getUuid(), CVTaskKeys.status, ExecutionStatus.RUNNING);
    // TODO: add parallel execution support
    return Optional.of(cvTask);
  }

  @Override
  public CVTask getCVTask(String cvTaskId) {
    return wingsPersistence.get(CVTask.class, cvTaskId);
  }

  @Override
  public void updateTaskStatus(String cvTaskId, DataCollectionTaskResult result) {
    log.debug("Updating CVTask status for id: {} and DataCollectionTaskResult: {}", cvTaskId, result);
    CVTask cvTask = getCVTask(cvTaskId);
    if (result.getStatus() == DataCollectionTaskStatus.FAILURE) {
      Map<String, Object> updateMap =
          ImmutableMap.of(CVTaskKeys.status, ExecutionStatus.FAILED, CVTaskKeys.exception, result.getErrorMessage());
      wingsPersistence.updateFields(CVTask.class, cvTaskId, updateMap);
      cvTask.setException(result.getErrorMessage());
      cvTask.setStatus(ExecutionStatus.FAILED);
      markWaitingTasksFailed(cvTask);
      markStateFailed(cvTask);
    } else if (result.getStatus() == DataCollectionTaskStatus.SUCCESS) {
      wingsPersistence.updateField(CVTask.class, cvTaskId, CVTaskKeys.status, ExecutionStatus.SUCCESS);
      enqueueNextTask(cvTask);
    } else {
      throw new IllegalStateException("Not implemented: " + result.getStatus());
    }
    logActivity(result, cvTask);
  }

  private void logActivity(DataCollectionTaskResult result, CVTask cvTask) {
    CVActivityLogger activityLogger = getActivityLogger(cvTask);
    // plus one in the endtime to represent the minute boundary properly in the UI
    if (result.getStatus() == DataCollectionTaskStatus.SUCCESS) {
      activityLogger.info("Data collection successful for time range %t to %t",
          cvTask.getDataCollectionInfo().getStartTime().toEpochMilli(),
          cvTask.getDataCollectionInfo().getEndTime().toEpochMilli() + 1);
    } else {
      activityLogger.error("Data collection failed for time range %t to %t. Error: " + cvTask.getException(),
          cvTask.getDataCollectionInfo().getStartTime().toEpochMilli(),
          cvTask.getDataCollectionInfo().getEndTime().toEpochMilli() + 1);
    }
  }

  private void enqueueNextTask(CVTask cvTask) {
    if (cvTask.getNextTaskId() != null) {
      log.info("Enqueuing next task {}", cvTask.getUuid());
      wingsPersistence.updateField(CVTask.class, cvTask.getNextTaskId(), CVTaskKeys.status, ExecutionStatus.QUEUED);
    }
  }

  @Override
  public void expireLongRunningTasks(String accountId) {
    log.debug("Running expire long running tasks for accountId {}", accountId);
    UpdateOperations<CVTask> updateOperations = wingsPersistence.createUpdateOperations(CVTask.class)
                                                    .set(CVTaskKeys.status, ExecutionStatus.EXPIRED)
                                                    .set(CVTaskKeys.exception, "Task timed out");
    Query<CVTask> query = wingsPersistence.createQuery(CVTask.class)
                              .filter(CVTaskKeys.status, ExecutionStatus.RUNNING)
                              .filter(CVTaskKeys.lastUpdatedAt + " <=",
                                  clock.instant().minus(CV_TASK_TIMEOUT_MINUTES, ChronoUnit.MINUTES).toEpochMilli());
    wingsPersistence.update(query, updateOperations);
    try (HIterator<CVTask> cvTaskHIterator =
             new HIterator<>(queryToFindByCVTaskByExecutionStatus(accountId, ExecutionStatus.EXPIRED).fetch())) {
      cvTaskHIterator.forEach(cvTask -> {
        markWaitingTasksFailed(cvTask);
        wingsPersistence.updateField(CVTask.class, cvTask.getUuid(), CVTaskKeys.status, ExecutionStatus.FAILED);
        markStateFailed(cvTask);
      });
    }
  }

  @Override
  public void retryTasks(String accountId) {
    // TODO
  }

  private void markStateFailed(CVTask cvTask) {
    if (isWorkflowTask(cvTask)) {
      log.info("Marking stateExecutionId {} as failed", cvTask.getStateExecutionId());
      VerificationStateAnalysisExecutionData analysisExecutionData =
          VerificationStateAnalysisExecutionData.builder()
              .correlationId(cvTask.getCorrelationId())
              .stateExecutionInstanceId(cvTask.getStateExecutionId())
              .build();
      analysisExecutionData.setStatus(ExecutionStatus.ERROR);
      analysisExecutionData.setErrorMsg(cvTask.getException());
      VerificationDataAnalysisResponse verificationDataAnalysisResponse =
          VerificationDataAnalysisResponse.builder().stateExecutionData(analysisExecutionData).build();
      verificationDataAnalysisResponse.setExecutionStatus(ExecutionStatus.ERROR);
      verificationManagerClientHelper.notifyManagerForVerificationAnalysis(
          cvTask.getAccountId(), analysisExecutionData.getCorrelationId(), verificationDataAnalysisResponse);
    }
  }

  private void markWaitingTasksFailed(CVTask cvTask) {
    if (isWorkflowTask(cvTask)) {
      String exceptionMsg =
          cvTask.getStatus() == ExecutionStatus.EXPIRED ? "Previous task timed out" : "Previous task failed";
      log.info("Marking queued task failed for stateExecutionId {}", cvTask.getStateExecutionId());
      // TODO: this is simple logic with no retry logic for now. This might change based on on retry logic.
      UpdateOperations<CVTask> updateOperations = wingsPersistence.createUpdateOperations(CVTask.class)
                                                      .set(CVTaskKeys.status, ExecutionStatus.FAILED)
                                                      .set(CVTaskKeys.exception, exceptionMsg);
      Query<CVTask> query =
          wingsPersistence.createQuery(CVTask.class).filter(CVTaskKeys.stateExecutionId, cvTask.getStateExecutionId());
      query.or(query.criteria(CVTaskKeys.status).equal(ExecutionStatus.QUEUED),
          query.criteria(CVTaskKeys.status).equal(ExecutionStatus.WAITING));

      wingsPersistence.update(query, updateOperations);
    }
  }

  private CVActivityLogger getActivityLogger(CVTask cvTask) {
    return activityLogService.getLogger(cvTask.getAccountId(), cvTask.getCvConfigId(),
        cvTask.getDataCollectionInfo().getEndTime().toEpochMilli(), cvTask.getStateExecutionId());
  }

  private Query<CVTask> queryToFindByCVTaskByExecutionStatus(String accountId, ExecutionStatus executionStatus) {
    return wingsPersistence.createQuery(CVTask.class, excludeAuthority)
        .filter(CVTaskKeys.accountId, accountId)
        .filter(CVTaskKeys.status, executionStatus);
  }

  private boolean isWorkflowTask(CVTask cvTask) {
    return cvTask.getStateExecutionId() != null;
  }
}
