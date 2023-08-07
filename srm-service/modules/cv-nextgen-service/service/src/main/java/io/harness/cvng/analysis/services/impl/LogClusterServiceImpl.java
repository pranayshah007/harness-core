/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_CLUSTER_RESOURCE;
import static io.harness.cvng.core.utils.DateTimeUtils.instantToEpochMinute;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.FeatureName;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.entities.ClusteredLog;
import io.harness.cvng.analysis.entities.ClusteredLog.ClusteredLogKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogClusterLearningEngineTask;
import io.harness.cvng.analysis.exceptions.ServiceGuardAnalysisException;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.LogClusterService;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.entities.LogRecord;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.AnalysisProgressLog;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ProgressLog;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@Slf4j
public class LogClusterServiceImpl implements LogClusterService {
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private HPersistence hPersistence;
  @Inject private LogRecordService logRecordService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public List<String> scheduleL1ClusteringTasks(AnalysisInput input, boolean isTaskForDeployment) {
    List<LearningEngineTask> clusterTasks =
        new ArrayList<>(buildClusterTasksForLogL1Clustering(input, isTaskForDeployment));
    if (isNotEmpty(clusterTasks)) {
      learningEngineTaskService.createLearningEngineTasks(clusterTasks);
    }
    log.info("Scheduled {} log cluster tasks for input {} and clusterLevel L1", clusterTasks.size(), input);
    return clusterTasks.stream().map(LearningEngineTask::getUuid).collect(Collectors.toList());
  }

  @Override
  public Optional<String> scheduleDeploymentL2ClusteringTask(AnalysisInput analysisInput) {
    return buildDeploymentClusterTasksForLogL2Clustering(analysisInput)
        .map(task -> learningEngineTaskService.createLearningEngineTask(task));
  }

  @Override
  public Optional<String> scheduleServiceGuardL2ClusteringTask(AnalysisInput analysisInput) {
    return buildServiceGuardClusterTasksForLogL2Clustering(analysisInput)
        .map(task -> learningEngineTaskService.createLearningEngineTask(task));
  }

  @Override
  public Map<String, ExecutionStatus> getTaskStatus(Set<String> taskIds) {
    return learningEngineTaskService.getTaskStatus(taskIds);
  }

  private List<LogClusterLearningEngineTask> buildClusterTasksForLogL1Clustering(
      AnalysisInput input, boolean isTaskForDeployment) {
    List<LogClusterLearningEngineTask> clusterTasks = new ArrayList<>();
    Instant timestamp = input.getStartTime().truncatedTo(ChronoUnit.SECONDS);
    while (timestamp.isBefore(input.getEndTime().truncatedTo(ChronoUnit.SECONDS))) {
      clusterTasks.add(createLogClusterTaskForMinute(
          input.getAccountId(), input.getVerificationTaskId(), timestamp, LogClusterLevel.L1, isTaskForDeployment));
      timestamp = timestamp.plus(1, ChronoUnit.MINUTES);
    }
    return clusterTasks;
  }

  private Optional<LogClusterLearningEngineTask> buildServiceGuardClusterTasksForLogL2Clustering(AnalysisInput input) {
    String testDataUrl = buildTestDataUrlForLogClustering(
        input.getVerificationTaskId(), LogClusterLevel.L2, input.getStartTime(), input.getEndTime());
    return Optional.of(createLogClusterLearningEngineTask(input.getAccountId(), input.getVerificationTaskId(),
        input.getStartTime(), input.getEndTime(), LogClusterLevel.L2, testDataUrl, false));
  }

  private Optional<LogClusterLearningEngineTask> buildDeploymentClusterTasksForLogL2Clustering(AnalysisInput input) {
    VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
        verificationTaskService.getVerificationJobInstanceId(input.getVerificationTaskId()));
    // test data for test verification will be different
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstance.getUuid());
    String testDataUrl = null;
    if (preDeploymentTimeRange.isPresent()) {
      Instant startTime = preDeploymentTimeRange.get().getStartTime();
      Instant endTime = input.getEndTime();
      testDataUrl =
          buildTestDataUrlForLogClustering(input.getVerificationTaskId(), LogClusterLevel.L2, startTime, endTime);
    } else {
      Optional<String> baselineVerificationTaskId = verificationTaskService.findBaselineVerificationTaskId(
          input.getVerificationTaskId(), verificationJobInstance);
      testDataUrl = buildTestDataUrlForLogClustering(baselineVerificationTaskId.orElse(null),
          input.getVerificationTaskId(), verificationJobInstance.getStartTime(), input.getEndTime());
    }
    return Optional.of(createLogClusterLearningEngineTask(input.getAccountId(), input.getVerificationTaskId(),
        input.getStartTime(), input.getEndTime(), LogClusterLevel.L2, testDataUrl, true));
  }

  private List<LogRecord> getLogRecordsForMinute(String cvConfigId, Instant timestamp) {
    return logRecordService.getLogRecords(cvConfigId, timestamp, timestamp.plus(Duration.ofMinutes(1)));
  }

  private LogClusterLearningEngineTask createLogClusterTaskForMinute(String accountId, String verificationTaskId,
      Instant timestamp, LogClusterLevel clusterLevel, boolean isTaskForDeployment) {
    List<LogRecord> logRecords = getLogRecordsForMinute(verificationTaskId, timestamp);
    if (logRecords != null) {
      String testDataUrl = buildTestDataUrlForLogClustering(
          verificationTaskId, clusterLevel, timestamp, timestamp.plus(Duration.ofMinutes(1)));
      return createLogClusterLearningEngineTask(accountId, verificationTaskId, timestamp,
          timestamp.plus(1, ChronoUnit.MINUTES), clusterLevel, testDataUrl, isTaskForDeployment);
    }
    return null;
  }

  @VisibleForTesting
  LogClusterLearningEngineTask createLogClusterLearningEngineTask(String accountId, String verificationTaskId,
      Instant startTime, Instant endTime, LogClusterLevel clusterLevel, @Nullable String testDataUrl,
      boolean isTaskForDeployment) {
    String taskId = generateUuid();
    LogClusterLearningEngineTask task =
        LogClusterLearningEngineTask.builder().clusterLevel(clusterLevel).testDataUrl(testDataUrl).build();
    task.setVerificationTaskId(verificationTaskId);
    task.setAnalysisStartTime(startTime);
    task.setAnalysisEndTime(endTime);
    task.setAnalysisEndEpochMinute(instantToEpochMinute(endTime));
    task.setUuid(taskId);
    task.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    task.setAnalysisSaveUrl(buildClusterSaveUrl(verificationTaskId, startTime, taskId, clusterLevel));
    LearningEngineTaskType learningEngineTaskType = getLearningEngineTaskType(accountId, isTaskForDeployment);
    task.setAnalysisType(learningEngineTaskType);
    task.setType(learningEngineTaskType);
    return task;
  }

  private LearningEngineTaskType getLearningEngineTaskType(String accountId, boolean isTaskForDeployment) {
    if (isTaskForDeployment
        && featureFlagService.isFeatureFlagEnabled(
            accountId, FeatureName.CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING.name())) {
      return LearningEngineTaskType.CV_LOG_CLUSTER;
    } else {
      return LearningEngineTaskType.LOG_CLUSTER;
    }
  }

  private String buildTestDataUrlForLogClustering(
      String verificationTaskId, LogClusterLevel clusterLevel, Instant startTime, Instant endTime) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_CLUSTER_RESOURCE + "/test-data");
    uriBuilder.addParameter(ClusteredLogKeys.verificationTaskId, verificationTaskId);
    uriBuilder.addParameter("startTime", String.valueOf(startTime.toEpochMilli()));
    uriBuilder.addParameter("endTime", String.valueOf(endTime.toEpochMilli()));
    uriBuilder.addParameter(ClusteredLogKeys.clusterLevel, clusterLevel.name());
    return getUriString(uriBuilder);
  }

  private String buildTestDataUrlForLogClustering(
      @Nullable String baselineVerificationTaskId, String verificationTaskId, Instant startTime, Instant endTime) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_CLUSTER_RESOURCE + "/l1-test-verification-test-data");
    if (baselineVerificationTaskId != null) {
      uriBuilder.addParameter("baselineVerificationTaskId", baselineVerificationTaskId);
    }
    uriBuilder.addParameter(ClusteredLogKeys.verificationTaskId, verificationTaskId);
    uriBuilder.addParameter("startTime", String.valueOf(startTime.toEpochMilli()));
    uriBuilder.addParameter("endTime", String.valueOf(endTime.toEpochMilli()));
    return getUriString(uriBuilder);
  }

  private String buildClusterSaveUrl(
      String verificationTaskId, Instant timestamp, String taskId, LogClusterLevel clusterLevel) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_CLUSTER_RESOURCE + "/save-clustered-logs");
    uriBuilder.addParameter("taskId", taskId);
    uriBuilder.addParameter(ClusteredLogKeys.verificationTaskId, verificationTaskId);
    uriBuilder.addParameter(ClusteredLogKeys.timestamp, timestamp.toString());
    uriBuilder.addParameter(ClusteredLogKeys.clusterLevel, clusterLevel.name());
    return getUriString(uriBuilder);
  }

  private String getUriString(URIBuilder uriBuilder) {
    try {
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<LogClusterDTO> getDataForLogCluster(
      String verificationTaskId, Instant startTime, Instant endTime, String host, LogClusterLevel clusterLevel) {
    // TODO(refactoring): better to make L1 and L2 a separate call. Have different Rest API for both levels
    switch (clusterLevel) {
      case L1:
        return getTestDataForL1Clustering(verificationTaskId, startTime, endTime);
      case L2:
        return getClusteredLogData(verificationTaskId, startTime, endTime, LogClusterLevel.L1);
      default:
        throw new ServiceGuardAnalysisException("Unknown clusterlevel in getDataForLogCluster: " + clusterLevel);
    }
  }

  @Override
  public List<LogClusterDTO> getL1TestVerificationTestData(
      @Nullable String baselineVerificationTaskId, String verificationTaskId, Instant startTime, Instant endTime) {
    List<LogClusterDTO> baselineData = new ArrayList<>();
    if (baselineVerificationTaskId != null) {
      baselineData = hPersistence.createQuery(ClusteredLog.class, excludeAuthority)
                         .filter(ClusteredLogKeys.verificationTaskId, baselineVerificationTaskId)
                         .filter(ClusteredLogKeys.clusterLevel, LogClusterLevel.L1)
                         .asList()
                         .stream()
                         .map(ClusteredLog::toDTO)
                         .collect(Collectors.toList());
    }
    List<LogClusterDTO> logClusterData =
        getClusteredLogData(verificationTaskId, startTime, endTime, LogClusterLevel.L1);
    logClusterData.addAll(baselineData);
    return logClusterData;
  }

  private List<LogClusterDTO> getTestDataForL1Clustering(
      String verificationTaskId, Instant startTime, Instant endTime) {
    List<LogClusterDTO> clusterData = new ArrayList<>();
    List<LogRecord> logRecords = logRecordService.getLogRecords(verificationTaskId, startTime, endTime);
    logRecords.forEach(record -> clusterData.add(record.toLogClusterDTO()));
    return clusterData;
  }

  @Override
  public List<LogClusterDTO> getClusteredLogData(
      String verificationTaskId, Instant startTime, Instant endTime, LogClusterLevel clusterLevel) {
    List<LogClusterDTO> clusterData = new ArrayList<>();
    List<ClusteredLog> clusteredLogs = hPersistence.createQuery(ClusteredLog.class, excludeAuthority)
                                           .filter(ClusteredLogKeys.verificationTaskId, verificationTaskId)
                                           .filter(ClusteredLogKeys.clusterLevel, clusterLevel)
                                           .field(ClusteredLogKeys.timestamp)
                                           .greaterThanOrEq(startTime)
                                           .field(ClusteredLogKeys.timestamp)
                                           .lessThan(endTime)
                                           .asList();
    clusteredLogs.forEach(clusteredLog -> clusterData.add(clusteredLog.toDTO()));
    return clusterData;
  }

  @Override
  public List<LogClusterDTO> getClusteredLogDataForDeploymentLog(String verificationTaskId, Instant startTime,
      Instant endTime, LogClusterLevel clusterLevel, Set<String> hostSet) {
    List<LogClusterDTO> clusterData = new ArrayList<>();
    Query<ClusteredLog> clusteredLogsQuery = hPersistence.createQuery(ClusteredLog.class, excludeAuthority)
                                                 .filter(ClusteredLogKeys.verificationTaskId, verificationTaskId)
                                                 .filter(ClusteredLogKeys.clusterLevel, clusterLevel)
                                                 .field(ClusteredLogKeys.timestamp)
                                                 .greaterThanOrEq(startTime)
                                                 .field(ClusteredLogKeys.timestamp)
                                                 .lessThan(endTime);
    if (isNotEmpty(hostSet)) {
      clusteredLogsQuery = clusteredLogsQuery.field(ClusteredLogKeys.host).hasAnyOf(hostSet);
    }
    List<ClusteredLog> clusteredLogs = clusteredLogsQuery.asList();
    clusteredLogs.forEach(clusteredLog -> clusterData.add(clusteredLog.toDTO()));
    return clusterData;
  }

  @Override
  public void saveClusteredData(List<LogClusterDTO> logClusterDTOs, String verificationTaskId, Instant timestamp,
      String taskId, LogClusterLevel clusterLevel) {
    List<ClusteredLog> clusteredLogList = new ArrayList<>();
    logClusterDTOs.forEach(logClusterDTO -> {
      ClusteredLog clusteredLog = logClusterDTO.toClusteredLog();
      clusteredLog.setClusterLevel(clusterLevel);
      clusteredLog.setVerificationTaskId(verificationTaskId);
      clusteredLogList.add(clusteredLog);
    });
    hPersistence.save(clusteredLogList);

    log.info("Saved {} clustered logs for verificationTaskId {} with clusterLevel {} and epochMinute {} ",
        verificationTaskId, clusterLevel, timestamp);
    learningEngineTaskService.markCompleted(taskId);
  }

  @Override
  public void logDeploymentVerificationProgress(
      AnalysisInput analysisInput, AnalysisStatus analysisStatus, LogClusterLevel clusterLevel) {
    ProgressLog progressLog = AnalysisProgressLog.builder()
                                  .startTime(analysisInput.getStartTime())
                                  .endTime(analysisInput.getEndTime())
                                  .analysisStatus(analysisStatus)
                                  .isFinalState(false)
                                  .log("Log clustering for " + clusterLevel)
                                  .verificationTaskId(analysisInput.getVerificationTaskId())
                                  .build();
    verificationJobInstanceService.logProgress(progressLog);
  }
}
