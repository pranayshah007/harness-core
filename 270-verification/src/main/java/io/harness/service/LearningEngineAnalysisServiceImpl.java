/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;
import static software.wings.service.impl.newrelic.LearningEngineAnalysisTask.TIME_SERIES_ANALYSIS_TASK_TIME_OUT;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.entity.ServiceSecretKey.ServiceType;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.service.intfc.LearningEngineService;
import io.harness.time.Timestamp;
import io.harness.version.ServiceApiVersion;

import software.wings.api.PhaseElement;
import software.wings.beans.Base.BaseKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.SupervisedTrainingStatus;
import software.wings.service.impl.analysis.SupervisedTrainingStatus.SupervisedTrainingStatusKeys;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask.LearningEngineExperimentalAnalysisTaskKeys;
import software.wings.service.impl.newrelic.MLExperiments;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionInstance;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by rsingh on 1/9/18.
 */
@Slf4j
public class LearningEngineAnalysisServiceImpl implements LearningEngineService {
  private static final String SERVICE_VERSION_FILE = "/service_version.properties";
  private static final int BACKOFF_TIME_MINS = 5;
  public static final int BACKOFF_LIMIT = 10;
  private static int[] FIBONACCI_SERIES;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private HarnessMetricRegistry metricRegistry;
  @Inject private VerificationManagerClientHelper managerClientHelper;
  @Inject private VerificationManagerClient managerClient;
  @Inject private CVActivityLogService cvActivityLogService;
  @Inject private DataStoreService dataStoreService;

  private final ServiceApiVersion learningEngineApiVersion;

  static {
    FIBONACCI_SERIES = new int[BACKOFF_LIMIT + 2];
    int i;

    // 0th and 1st number of the series are 0 and 1
    FIBONACCI_SERIES[0] = 1;
    FIBONACCI_SERIES[1] = 2;

    for (i = 2; i <= BACKOFF_LIMIT; i++) {
      FIBONACCI_SERIES[i] = FIBONACCI_SERIES[i - 1] + FIBONACCI_SERIES[i - 2];
    }
  }

  public LearningEngineAnalysisServiceImpl() throws IOException {
    Properties messages = new Properties();
    InputStream in = null;
    try {
      in = getClass().getResourceAsStream(SERVICE_VERSION_FILE);
      messages.load(in);
    } finally {
      if (in != null) {
        in.close();
      }
    }
    String apiVersion = messages.getProperty(ServiceType.LEARNING_ENGINE.name());
    Preconditions.checkState(!StringUtils.isEmpty(apiVersion));
    learningEngineApiVersion = ServiceApiVersion.valueOf(apiVersion.toUpperCase());
  }

  private ClusterLevel getDefaultClusterLevel() {
    return ClusterLevel.HF;
  }

  @Override
  public LearningEngineAnalysisTask getTaskById(String taskId) {
    return wingsPersistence.get(LearningEngineAnalysisTask.class, taskId);
  }

  @Override
  public boolean addLearningEngineAnalysisTask(LearningEngineAnalysisTask analysisTask) {
    analysisTask.setVersion(learningEngineApiVersion);
    analysisTask.setExecutionStatus(ExecutionStatus.QUEUED);
    analysisTask.setRetry(0);
    if (analysisTask.getCluster_level() == null) {
      analysisTask.setCluster_level(getDefaultClusterLevel().getLevel());
    }
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, analysisTask.getState_execution_id())
            .field(LearningEngineAnalysisTaskKeys.analysis_minute)
            .lessThanOrEq(analysisTask.getAnalysis_minute())
            .field(LearningEngineAnalysisTaskKeys.executionStatus)
            .in(Lists.newArrayList(ExecutionStatus.RUNNING, ExecutionStatus.QUEUED, ExecutionStatus.SUCCESS))
            .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, analysisTask.getMl_analysis_type())
            .filter(LearningEngineAnalysisTaskKeys.cluster_level, analysisTask.getCluster_level())
            .filter(LearningEngineAnalysisTaskKeys.group_name, analysisTask.getGroup_name())
            .filter(LearningEngineAnalysisTaskKeys.version, learningEngineApiVersion)
            .order("-createdAt");
    if (!analysisTask.is24x7Task()) {
      query = query.filter(LearningEngineAnalysisTaskKeys.control_nodes, analysisTask.getControl_nodes());
    }
    if (isNotEmpty(analysisTask.getTag())) {
      query = query.filter(LearningEngineAnalysisTaskKeys.tag, analysisTask.getTag());
    }
    LearningEngineAnalysisTask learningEngineAnalysisTask = query.get();

    boolean isTaskCreated = false;
    if (learningEngineAnalysisTask == null) {
      wingsPersistence.save(analysisTask);
      isTaskCreated = true;
    } else if (learningEngineAnalysisTask.getExecutionStatus() == ExecutionStatus.SUCCESS) {
      if (learningEngineAnalysisTask.getAnalysis_minute() < analysisTask.getAnalysis_minute()) {
        wingsPersistence.save(analysisTask);
        isTaskCreated = true;
      } else {
        log.warn("task is already marked success for min {}. task {}", analysisTask.getAnalysis_minute(),
            learningEngineAnalysisTask);
      }
    } else {
      log.warn("task is already {}. Will not queue for minute {}, {}", learningEngineAnalysisTask.getExecutionStatus(),
          analysisTask.getAnalysis_minute(), learningEngineAnalysisTask);
    }
    if (isTaskCreated) {
      log.info("LE task queued for analysis type: {} and minute: {}", analysisTask.getMl_analysis_type(),
          analysisTask.getAnalysis_minute());
    }
    return isTaskCreated;
  }

  @Override
  public boolean addLearningEngineExperimentalAnalysisTask(LearningEngineExperimentalAnalysisTask analysisTask) {
    LearningEngineExperimentalAnalysisTask experimentalAnalysisTask =
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.state_execution_id, analysisTask.getState_execution_id())
            .filter(LearningEngineAnalysisTaskKeys.analysis_minute, analysisTask.getAnalysis_minute())
            .get();
    if (experimentalAnalysisTask != null) {
      log.info("task already queued for experiment {}", analysisTask.getState_execution_id());
      return false;
    }
    analysisTask.setVersion(learningEngineApiVersion);
    analysisTask.setExecutionStatus(ExecutionStatus.QUEUED);
    analysisTask.setRetry(0);
    wingsPersistence.save(analysisTask);
    return true;
  }

  @Override
  public LearningEngineAnalysisTask getNextLearningEngineAnalysisTask(ServiceApiVersion serviceApiVersion) {
    List<MLAnalysisType> taskTypesOtherThanFeedback =
        Arrays.asList(MLAnalysisType.LOG_ML, MLAnalysisType.TIME_SERIES, MLAnalysisType.LOG_CLUSTER);
    return getNextLearningEngineAnalysisTask(
        serviceApiVersion, Optional.empty(), Optional.of(taskTypesOtherThanFeedback));
  }

  @Override
  public LearningEngineAnalysisTask getNextLearningEngineAnalysisTask(
      ServiceApiVersion serviceApiVersion, Optional<String> is24x7Task, Optional<List<MLAnalysisType>> taskTypes) {
    Query<LearningEngineAnalysisTask> query = wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                                                  .filter(LearningEngineAnalysisTaskKeys.version, serviceApiVersion)
                                                  .field(LearningEngineAnalysisTaskKeys.retry)
                                                  .lessThanOrEq(LearningEngineAnalysisTask.RETRIES);

    if (is24x7Task.isPresent()) {
      boolean task24x7 = Boolean.parseBoolean(is24x7Task.get());
      query.filter(LearningEngineAnalysisTaskKeys.is24x7Task, task24x7);
    }

    if (taskTypes.isPresent() && isNotEmpty(taskTypes.get())) {
      query.field(LearningEngineAnalysisTaskKeys.ml_analysis_type).in(taskTypes.get());
    }

    query.or(query.criteria(LearningEngineAnalysisTaskKeys.executionStatus).equal(ExecutionStatus.QUEUED),
        query.and(query.criteria(LearningEngineAnalysisTaskKeys.executionStatus).equal(ExecutionStatus.RUNNING),
            query.criteria(LearningEngineAnalysisTask.LAST_UPDATED_AT_KEY2)
                .lessThan(System.currentTimeMillis() - TIME_SERIES_ANALYSIS_TASK_TIME_OUT)));
    query = query.order(LearningEngineAnalysisTaskKeys.priority + "," + BaseKeys.createdAt);
    UpdateOperations<LearningEngineAnalysisTask> updateOperations =
        wingsPersistence.createUpdateOperations(LearningEngineAnalysisTask.class)
            .set(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
            .inc(LearningEngineAnalysisTaskKeys.retry)
            .set(LearningEngineAnalysisTask.LAST_UPDATED_AT_KEY2, System.currentTimeMillis());
    LearningEngineAnalysisTask task =
        wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
    if (task != null && task.getRetry() > LearningEngineAnalysisTask.RETRIES) {
      // If some task has failed for more than 3 times, mark status as failed.
      log.info("LearningEngine task {} has failed 3 or more times. Setting the status to FAILED", task.getUuid());
      try {
        wingsPersistence.updateField(
            LearningEngineAnalysisTask.class, task.getUuid(), "executionStatus", ExecutionStatus.FAILED);
      } catch (DuplicateKeyException e) {
        log.info("task {} for state {} is already marked successful", task.getUuid(), task.getState_execution_id());
      }
      return null;
    }
    logActivityOnLETaskPickup(task);
    return task;
  }

  private void logActivityOnLETaskPickup(LearningEngineAnalysisTask task) {
    if (task != null && task.getMl_analysis_type() == MLAnalysisType.TIME_SERIES) {
      if (isAnalysisMinuteAbsoluteTimestamp(task.getAnalysis_minute())) {
        if (task.is24x7Task()) {
          cvActivityLogService
              .getLogger(
                  task.getAccountId(), task.getCvConfigId(), task.getAnalysis_minute(), task.getState_execution_id())
              .info("Time series analysis started for time range %t to %t",
                  TimeUnit.MINUTES.toMillis(task.getAnalysis_minute() - CRON_POLL_INTERVAL_IN_MINUTES
                      + 1), // both startTime and endTime are inclusive
                  TimeUnit.MINUTES.toMillis(task.getAnalysis_minute()));
        } else {
          cvActivityLogService
              .getLogger(
                  task.getAccountId(), task.getCvConfigId(), task.getAnalysis_minute(), task.getState_execution_id())
              .info("Time series analysis started for time range %t to %t",
                  TimeUnit.MINUTES.toMillis(task.getAnalysis_minute()),
                  TimeUnit.MINUTES.toMillis(task.getAnalysis_minute() + 1));
        }

      } else {
        cvActivityLogService
            .getLogger(
                task.getAccountId(), task.getCvConfigId(), task.getAnalysis_minute(), task.getState_execution_id())
            .info("Time series analysis started for minute " + task.getAnalysis_minute() + ".");
      }
    }
  }

  private boolean isAnalysisMinuteAbsoluteTimestamp(long analysisMinute) {
    // this is extremely hacky solution to generate proper activity logs regardless of if this is absolute minute of
    // relative minute. We are already moving to absolute analysis minute and need to remove this after that.
    return analysisMinute > 10000;
  }

  @Override
  public LearningEngineExperimentalAnalysisTask getNextLearningEngineExperimentalAnalysisTask(
      ServiceApiVersion serviceApiVersion, String experimentName, Optional<List<MLAnalysisType>> taskTypes) {
    Query<LearningEngineExperimentalAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.version, serviceApiVersion)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.experiment_name, experimentName)
            .field(LearningEngineExperimentalAnalysisTaskKeys.retry)
            .lessThan(LearningEngineExperimentalAnalysisTask.RETRIES);

    if (taskTypes.isPresent() && isNotEmpty(taskTypes.get())) {
      query.field(LearningEngineAnalysisTaskKeys.ml_analysis_type).in(taskTypes.get());
    }

    query.or(query.criteria(LearningEngineExperimentalAnalysisTaskKeys.executionStatus).equal(ExecutionStatus.QUEUED),
        query.and(
            query.criteria(LearningEngineExperimentalAnalysisTaskKeys.executionStatus).equal(ExecutionStatus.RUNNING),
            query.criteria(LearningEngineExperimentalAnalysisTask.LAST_UPDATED_AT_KEY2)
                .lessThan(System.currentTimeMillis() - TIME_SERIES_ANALYSIS_TASK_TIME_OUT)));
    UpdateOperations<LearningEngineExperimentalAnalysisTask> updateOperations =
        wingsPersistence.createUpdateOperations(LearningEngineExperimentalAnalysisTask.class)
            .set(LearningEngineExperimentalAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
            .inc(LearningEngineExperimentalAnalysisTaskKeys.retry)
            .set(LearningEngineExperimentalAnalysisTask.LAST_UPDATED_AT_KEY2, System.currentTimeMillis());
    return wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
  }

  @Override
  public boolean hasAnalysisTimedOut(String appId, String workflowExecutionId, String stateExecutionId) {
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
            .field(LearningEngineAnalysisTaskKeys.retry)
            .greaterThanOrEq(LearningEngineAnalysisTask.RETRIES);
    return query.count() > 0;
  }

  @Override
  public void markCompleted(String accountId, String workflowExecutionId, String stateExecutionId, long analysisMinute,
      MLAnalysisType type, ClusterLevel level) {
    long currentTimeMillis = Instant.now().toEpochMilli();
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.workflow_execution_id, workflowExecutionId)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
            .filter(LearningEngineAnalysisTaskKeys.analysis_minute, analysisMinute)
            .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, type)
            .filter(LearningEngineAnalysisTaskKeys.cluster_level, level.getLevel());

    LearningEngineAnalysisTask task = query.get();
    if (task != null) {
      task.setExecutionStatus(ExecutionStatus.SUCCESS);
      task.setTimeTaken(currentTimeMillis - task.getLastUpdatedAt());
      wingsPersistence.save(task);

      logActivityOnAnalysisComplete(cvActivityLogService.getLoggerByStateExecutionId(accountId, stateExecutionId),
          level, type, analysisMinute, false);
    }
  }

  @Override
  public void markCompleted(String taskId) {
    long currentTimeMillis = Instant.now().toEpochMilli();
    if (taskId == null) {
      log.warn("taskId is null");
      return;
    }
    LearningEngineAnalysisTask task = wingsPersistence.get(LearningEngineAnalysisTask.class, taskId);
    if (task != null) {
      task.setExecutionStatus(ExecutionStatus.SUCCESS);
      task.setTimeTaken(currentTimeMillis - task.getLastUpdatedAt());
      wingsPersistence.save(task);

      logActivityOnAnalysisComplete(cvActivityLogService.getLogger(task.getAccountId(), task.getCvConfigId(),
                                        task.getAnalysis_minute(), task.getState_execution_id()),
          ClusterLevel.valueOf(task.getCluster_level()), task.getMl_analysis_type(), task.getAnalysis_minute(),
          task.is24x7Task());
      log.info("Job has been marked as SUCCESS for taskId : {}", taskId);
    }
  }

  private void logActivityOnAnalysisComplete(CVActivityLogger activityLogger, ClusterLevel level,
      MLAnalysisType mlAnalysisType, long analysisMinute, boolean is247Task) {
    String prefix = mlAnalysisType == MLAnalysisType.TIME_SERIES ? "Time series " : "Log ";
    if (level == ClusterLevel.HF) {
      // TODO: clean this up once analysisMinute becomes absolute for everything.
      if (isAnalysisMinuteAbsoluteTimestamp(analysisMinute)) {
        // for workflow we do analysis for 1 min
        if (is247Task) {
          activityLogger.info(prefix + "analysis completed for time range %t to %t.",
              TimeUnit.MINUTES.toMillis(
                  analysisMinute - CRON_POLL_INTERVAL_IN_MINUTES + 1), // both startTime and endTime are inclusive
              TimeUnit.MINUTES.toMillis(analysisMinute));
        } else {
          activityLogger.info(prefix + "analysis completed for time range %t to %t.",
              TimeUnit.MINUTES.toMillis(analysisMinute), TimeUnit.MINUTES.toMillis(analysisMinute + 1));
        }
      } else {
        activityLogger.info(prefix + "analysis completed for minute " + analysisMinute + ".");
      }
    }
  }
  @Override
  public void markExpTaskCompleted(String taskId) {
    if (taskId == null) {
      log.warn("taskId is null");
      return;
    }
    wingsPersistence.updateField(LearningEngineExperimentalAnalysisTask.class, taskId,
        LearningEngineExperimentalAnalysisTaskKeys.executionStatus, ExecutionStatus.SUCCESS);
  }

  @Override
  public void markStatus(
      String workflowExecutionId, String stateExecutionId, long analysisMinute, ExecutionStatus executionStatus) {
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.workflow_execution_id, workflowExecutionId)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
            .filter(LearningEngineAnalysisTaskKeys.cluster_level, getDefaultClusterLevel())
            .field(LearningEngineAnalysisTaskKeys.analysis_minute)
            .lessThanOrEq(analysisMinute);
    UpdateOperations<LearningEngineAnalysisTask> updateOperations =
        wingsPersistence.createUpdateOperations(LearningEngineAnalysisTask.class)
            .set(LearningEngineAnalysisTaskKeys.executionStatus, executionStatus);

    wingsPersistence.update(query, updateOperations);
  }

  @Override
  public List<MLExperiments> getExperiments(MLAnalysisType ml_analysis_type) {
    return wingsPersistence.createQuery(MLExperiments.class, excludeAuthority)
        .filter("ml_analysis_type", ml_analysis_type)
        .field("is24x7")
        .doesNotExist()
        .asList();
  }

  @Override
  public AnalysisContext getNextVerificationAnalysisTask(ServiceApiVersion serviceApiVersion) {
    // We need to move the WorkflowDataCollectionJob to iterator
    // Since this method is called for scheduling the data collection crons for minute based collection and since
    // AnalysisContext can be marked as running by other iterators like WorkflowLogClusterJob the actual data collection
    // cron schedule can be delayed by VERIFICATION_TASK_TIMEOUT which is 3 minutes. For now reducing the retry to be 30
    // seconds. Since the cron doesn't get added again if it already exists, this change is harmless
    Query<AnalysisContext> query = wingsPersistence.createQuery(AnalysisContext.class)
                                       .filter(AnalysisContextKeys.version, serviceApiVersion)
                                       .field("retry")
                                       .lessThan(LearningEngineAnalysisTask.RETRIES);
    query.or(query.criteria("executionStatus").equal(ExecutionStatus.QUEUED),
        query.and(query.criteria("executionStatus").equal(ExecutionStatus.RUNNING),
            query.criteria(AnalysisContext.LAST_UPDATED_AT_KEY2)
                .lessThan(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30))));
    UpdateOperations<AnalysisContext> updateOperations =
        wingsPersistence.createUpdateOperations(AnalysisContext.class)
            .set("executionStatus", ExecutionStatus.RUNNING)
            .inc("retry")
            .set(AnalysisContext.LAST_UPDATED_AT_KEY2, System.currentTimeMillis());
    return wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
  }

  @Override
  public void markJobStatus(AnalysisContext verificationAnalysisTask, ExecutionStatus executionStatus) {
    log.info(
        "Marking job as {} for stateExecutionId : {}", executionStatus, verificationAnalysisTask.getStateExecutionId());
    wingsPersistence.updateField(
        AnalysisContext.class, verificationAnalysisTask.getUuid(), "executionStatus", executionStatus);
  }

  @Override
  public void checkAndUpdateFailedLETask(String stateExecutionId, int analysisMinute) {
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .filter(LearningEngineAnalysisTaskKeys.analysis_minute, analysisMinute);
    query.or(query.criteria(LearningEngineAnalysisTaskKeys.executionStatus).equal(ExecutionStatus.FAILED),
        query.and(query.criteria(LearningEngineAnalysisTaskKeys.executionStatus).equal(ExecutionStatus.RUNNING),
            query.criteria(LearningEngineAnalysisTaskKeys.retry).greaterThanOrEq(1),
            query.criteria(LearningEngineAnalysisTask.LAST_UPDATED_AT_KEY2)
                .greaterThanOrEq(System.currentTimeMillis() - TIME_SERIES_ANALYSIS_TASK_TIME_OUT)));

    UpdateOperations<LearningEngineAnalysisTask> updateOperations =
        wingsPersistence.createUpdateOperations(LearningEngineAnalysisTask.class)
            .set(LearningEngineAnalysisTaskKeys.state_execution_id,
                stateExecutionId + "-retry-" + TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary()))
            .set(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.FAILED);
    wingsPersistence.update(query, updateOperations);
  }

  @Override
  public boolean notifyFailure(String taskId, LearningEngineError learningEngineError) {
    log.info("error payload {}", learningEngineError);
    final LearningEngineAnalysisTask analysisTask = wingsPersistence.get(LearningEngineAnalysisTask.class, taskId);
    if (analysisTask == null) {
      log.error("No task found with id {}", taskId);
      return false;
    }

    if (analysisTask.is24x7Task()) {
      Preconditions.checkState(isNotEmpty(analysisTask.getCvConfigId()));
      // TODO figure out and implement what to do for service guard tasks
      return false;
    } else {
      Preconditions.checkState(isNotEmpty(analysisTask.getState_execution_id()));
    }
    // TODO: Looks like this is not getting called from LE now. Test this once LE start calling this method.
    cvActivityLogService
        .getLogger(analysisTask.getAccountId(), analysisTask.getCvConfigId(), analysisTask.getAnalysis_minute(),
            analysisTask.getState_execution_id())
        .warn("Error while processing analysis task for "
                + ClusterLevel.valueOf(analysisTask.getCluster_level()).getClusteringPhase()
                + ". Analysis minute %t Error: " + learningEngineError.getErrorMsg(),
            TimeUnit.MINUTES.toMillis(analysisTask.getAnalysis_minute()));
    final AnalysisContext analysisContext =
        wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
            .filter(AnalysisContextKeys.stateExecutionId, analysisTask.getState_execution_id())
            .get();

    if (analysisContext == null) {
      throw new IllegalStateException("No context found for " + analysisTask.getState_execution_id());
    }

    if (!isStateValid(analysisContext.getAppId(), analysisContext.getStateExecutionId())) {
      log.info("For {} state is not in running state", analysisContext.getStateExecutionId());
      return false;
    }

    if (analysisTask.getRetry() >= LearningEngineAnalysisTask.RETRIES) {
      wingsPersistence.updateField(LearningEngineAnalysisTask.class, taskId,
          LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.FAILED);

      final VerificationStateAnalysisExecutionData executionData =
          VerificationStateAnalysisExecutionData.builder()
              .stateExecutionInstanceId(analysisContext.getStateExecutionId())
              .serverConfigId(analysisContext.getAnalysisServerConfigId())
              .canaryNewHostNames(analysisContext.getTestNodes() == null ? Collections.emptySet()
                                                                         : analysisContext.getTestNodes().keySet())
              .lastExecutionNodes(analysisContext.getControlNodes() == null
                      ? Collections.emptySet()
                      : analysisContext.getControlNodes().keySet())
              .correlationId(analysisContext.getCorrelationId())
              .build();
      executionData.setStatus(ExecutionStatus.ERROR);
      executionData.setErrorMsg(learningEngineError.getErrorMsg());
      final VerificationDataAnalysisResponse response =
          VerificationDataAnalysisResponse.builder().stateExecutionData(executionData).build();
      response.setExecutionStatus(ExecutionStatus.ERROR);
      log.info("Notifying state id: {} , corr id: {}", analysisContext.getStateExecutionId(),
          analysisContext.getCorrelationId());

      managerClientHelper.notifyManagerForVerificationAnalysis(analysisContext, response);
    } else {
      wingsPersistence.updateField(LearningEngineAnalysisTask.class, taskId,
          LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED);
    }
    return true;
  }

  @Override
  public boolean isStateValid(String appId, String stateExecutionId) {
    if (stateExecutionId.contains(CV_24x7_STATE_EXECUTION)) {
      return true;
    }
    try {
      return managerClientHelper.callManagerWithRetry(managerClient.isStateValid(appId, stateExecutionId))
          .getResource();
    } catch (Exception e) {
      log.error("for {} failed to reach to manager. Will assume that state is still valid", e);
      return true;
    }
  }

  @Override
  public boolean shouldUseSupervisedModel(String fieldName, String fieldValue) {
    String serviceId;
    if (SupervisedTrainingStatusKeys.serviceId.equals(fieldName)) {
      serviceId = fieldValue;
    } else if (AnalysisContextKeys.stateExecutionId.equals(fieldName)) {
      serviceId = getServiceIdFromStateExecutionId(fieldValue);
      if (isEmpty(serviceId)) {
        return false;
      }
    } else {
      log.info("Unexpected fieldname provided in shouldUseSupervisedModel. Name: {}", fieldName);
      return false;
    }

    PageRequest<SupervisedTrainingStatus> supervisedTrainingStatusPageRequest =
        PageRequestBuilder.aPageRequest()
            .addFilter(SupervisedTrainingStatusKeys.serviceId, Operator.EQ, serviceId)
            .build();
    List<SupervisedTrainingStatus> trainingStatuses =
        dataStoreService.list(SupervisedTrainingStatus.class, supervisedTrainingStatusPageRequest);
    if (isNotEmpty(trainingStatuses)) {
      if (trainingStatuses.size() > 1) {
        log.info("More than one supervised training status found for service {} : {}", serviceId, trainingStatuses);
        return false;
      } else {
        log.info("One supervised training status found for service {}. Returning {}", serviceId,
            trainingStatuses.get(0).isSupervisedReady());
        return trainingStatuses.get(0).isSupervisedReady();
      }
    }
    log.info("No supervised training status found for serviceId {}", serviceId);
    return false;
  }

  @Override
  public String getServiceIdFromStateExecutionId(String stateExecutionId) {
    StateExecutionInstance instance = wingsPersistence.get(StateExecutionInstance.class, stateExecutionId);
    PhaseElement phaseElement = null;
    for (ContextElement element : instance.getContextElements()) {
      if (element instanceof PhaseElement) {
        phaseElement = (PhaseElement) element;
        break;
      }
    }
    if (phaseElement != null) {
      return phaseElement.getServiceElement().getUuid();
    }
    log.error("There is no serviceID associated with the stateExecutionId: " + instance.getUuid());
    return null;
  }
  /**
   *
   * @param stateExecutionId
   * @param analysisMinute
   * @param cvConfig
   * @param analysisType
   * @return a positive backoff number based on previous backoff count
   */
  @Override
  public int getNextServiceGuardBackoffCount(
      String stateExecutionId, String cvConfig, long analysisMinute, MLAnalysisType analysisType) {
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.analysis_minute, analysisMinute)
            .field(LearningEngineAnalysisTaskKeys.state_execution_id)
            .startsWith(stateExecutionId + "-retry-")
            .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, analysisType)
            .order("-lastUpdatedAt");

    LearningEngineAnalysisTask previousTask = query.get();
    if (previousTask == null) {
      return 1;
    }

    int nextBackoffCount = previousTask.getService_guard_backoff_count() == 0
        ? 1
        : getNextFibonacciNumber(previousTask.getService_guard_backoff_count());
    if (nextBackoffCount > BACKOFF_LIMIT) {
      log.info(
          "For cvConfig {} analysisMinute {} the count has reached the total backoff time. Capping the next backoff count.",
          cvConfig, analysisMinute);
      nextBackoffCount = BACKOFF_LIMIT;
    }
    return nextBackoffCount;
  }

  public boolean isTaskRunningOrQueued(String cvConfigId) {
    return isTaskRunningOrQueued(cvConfigId, Optional.empty());
  }

  private boolean isTaskRunningOrQueued(String cvConfigId, Optional<Long> analysisMinute) {
    Query<LearningEngineAnalysisTask> taskQuery =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.cvConfigId, cvConfigId)
            .field(LearningEngineAnalysisTaskKeys.executionStatus)
            .in(Arrays.asList(ExecutionStatus.RUNNING, ExecutionStatus.QUEUED));
    if (analysisMinute.isPresent()) {
      taskQuery = taskQuery.field(LearningEngineAnalysisTaskKeys.analysis_minute).greaterThanOrEq(analysisMinute.get());
    }
    LearningEngineAnalysisTask task = taskQuery.get();
    if (task == null) {
      log.info(
          "There are no tasks running or queued for cvConfig {} after analysisMinute {}", cvConfigId, analysisMinute);
      return false;
    } else {
      log.info("Found a task running or queued for cvConfig {} and minute {}", cvConfigId, task.getAnalysis_minute());
      return true;
    }
  }

  @Override
  public boolean isTaskRunningOrQueued(String cvConfigId, long analysisMinute) {
    return isTaskRunningOrQueued(cvConfigId, Optional.of(analysisMinute));
  }

  @Override
  public boolean isEligibleToCreateTask(
      String stateExecutionId, String cvConfig, long analysisMinute, MLAnalysisType analysisType) {
    Query<LearningEngineAnalysisTask> query =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.analysis_minute, analysisMinute)
            .field(LearningEngineAnalysisTaskKeys.state_execution_id)
            .startsWith(stateExecutionId + "-retry-")
            .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, analysisType)
            .order("-lastUpdatedAt");
    LearningEngineAnalysisTask previousTask = query.get();
    if (previousTask == null) {
      return true;
    }
    long nextSchedulableTime = previousTask.getLastUpdatedAt()
        + previousTask.getService_guard_backoff_count() * TimeUnit.MINUTES.toMillis(BACKOFF_TIME_MINS);
    if (Timestamp.currentMinuteBoundary() >= Timestamp.minuteBoundary(nextSchedulableTime)) {
      return true;
    } else {
      log.info("For cvConfig {} analysisMinute {} the next schedule time is {}", cvConfig, analysisMinute,
          nextSchedulableTime);
      return false;
    }
  }

  private int getNextFibonacciNumber(int n) {
    if (n == 0) {
      return 1;
    } else if (n == 1) {
      return 2;
    }

    for (int i = 2; i <= BACKOFF_LIMIT; i++) {
      if (n == FIBONACCI_SERIES[i - 1]) {
        return FIBONACCI_SERIES[i];
      }
    }
    return FIBONACCI_SERIES[BACKOFF_LIMIT];
  }
}
