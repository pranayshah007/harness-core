/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.ANALYSIS_RISK_RESULTS_LIMIT;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SAVE_ANALYSIS_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_DATA_LENGTH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_VERIFICATION_TASK_SAVE_ANALYSIS_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.CV_ANALYSIS_WINDOW_MINUTES;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask.DeploymentVerificationTaskInfo;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask_v2;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums.TimeSeriesCumulativeSumsKeys;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesLoadTestLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary.TimeSeriesRiskSummaryKeys;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary.TransactionMetricRisk;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory.TimeSeriesShortTermHistoryKeys;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnomalousPatternsService;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.AnalysisProgressLog;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ProgressLog;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@Slf4j
public class TimeSeriesAnalysisServiceImpl implements TimeSeriesAnalysisService {
  @Inject private HPersistence hPersistence;
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private HeatMapService heatMapService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private TimeSeriesAnomalousPatternsService timeSeriesAnomalousPatternsService;

  @Override
  public List<String> scheduleServiceGuardAnalysis(AnalysisInput analysisInput) {
    TimeSeriesLearningEngineTask timeSeriesTask = createTimeSeriesLearningTask(analysisInput);
    learningEngineTaskService.createLearningEngineTasks(Arrays.asList(timeSeriesTask));
    // TODO: find a good way to return all taskIDs
    return Arrays.asList(timeSeriesTask.getUuid());
  }
  @Override
  public List<String> scheduleCanaryVerificationTaskAnalysis(AnalysisInput analysisInput) {
    TimeSeriesCanaryLearningEngineTask timeSeriesTask = createTimeSeriesCanaryLearningEngineTask(analysisInput);
    learningEngineTaskService.createLearningEngineTasks(Arrays.asList(timeSeriesTask));
    // TODO: find a good way to return all taskIDs
    return Arrays.asList(timeSeriesTask.getUuid());
  }

  @Override
  public List<String> scheduleDeploymentTimeSeriesAnalysis(AnalysisInput analysisInput) {
    TimeSeriesCanaryLearningEngineTask_v2 timeSeriesTask = createDeploymentTimeSeriesLearningEngineTask(analysisInput);
    learningEngineTaskService.createLearningEngineTasks(Arrays.asList(timeSeriesTask));
    return Arrays.asList(timeSeriesTask.getUuid());
  }

  @Override
  public List<String> scheduleTestVerificationTaskAnalysis(AnalysisInput analysisInput) {
    TimeSeriesLoadTestLearningEngineTask timeSeriesTask = createTimeSeriesLoadTestLearningEngineTask(analysisInput);
    learningEngineTaskService.createLearningEngineTasks(Arrays.asList(timeSeriesTask));
    return Arrays.asList(timeSeriesTask.getUuid());
  }

  @Override
  public void logDeploymentVerificationProgress(AnalysisInput analysisInput, AnalysisStatus analysisStatus) {
    ProgressLog progressLog = AnalysisProgressLog.builder()
                                  .startTime(analysisInput.getStartTime())
                                  .endTime(analysisInput.getEndTime())
                                  .analysisStatus(analysisStatus)
                                  .isFinalState(true)
                                  .log("Time series analysis")
                                  .verificationTaskId(analysisInput.getVerificationTaskId())
                                  .build();
    boolean isFailFast =
        deploymentTimeSeriesAnalysisService.isAnalysisFailFastForLatestTimeRange(analysisInput.getVerificationTaskId());
    progressLog.shouldTerminate(isFailFast);
    verificationJobInstanceService.logProgress(progressLog);
  }

  private TimeSeriesLoadTestLearningEngineTask createTimeSeriesLoadTestLearningEngineTask(AnalysisInput input) {
    String taskId = generateUuid();
    VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
        verificationTaskService.getVerificationJobInstanceId(input.getVerificationTaskId()));
    Preconditions.checkNotNull(verificationJobInstance, "verificationJobInstance can not be null");
    VerificationJobInstance baseline = null;
    VerificationJob verificationJob = verificationJobInstance.getResolvedJob();
    if (verificationJob.getType() == VerificationJobType.TEST) {
      TestVerificationJob testVerificationJob = (TestVerificationJob) verificationJob;
      if (testVerificationJob.getBaselineVerificationJobInstanceId() != null) {
        baseline = verificationJobInstanceService.getVerificationJobInstance(
            testVerificationJob.getBaselineVerificationJobInstanceId());
      }
    }
    TimeSeriesLoadTestLearningEngineTask timeSeriesLearningEngineTask =
        TimeSeriesLoadTestLearningEngineTask.builder()
            .controlDataUrl(
                baseline == null ? null : baselineDataUrl(input, verificationJobInstance, baseline).orElse(null))
            .testDataUrl(postDeploymentDataUrl(input, verificationJobInstance))
            .dataLength(
                (int) Duration.between(verificationJobInstance.getStartTime(), input.getStartTime()).toMinutes() + 1)
            .metricTemplateUrl(createMetricTemplateUrl(input))
            .tolerance(verificationJob.getSensitivity().getTolerance())
            .baselineStartTime(baseline == null ? null : baseline.getStartTime().toEpochMilli())
            .build();

    timeSeriesLearningEngineTask.setVerificationTaskId(input.getVerificationTaskId());
    timeSeriesLearningEngineTask.setAnalysisType(LearningEngineTaskType.TIME_SERIES_LOAD_TEST);
    timeSeriesLearningEngineTask.setAnalysisStartTime(input.getStartTime());
    timeSeriesLearningEngineTask.setAnalysisEndTime(input.getEndTime());
    timeSeriesLearningEngineTask.setAnalysisEndEpochMinute(
        TimeUnit.MILLISECONDS.toMinutes(input.getEndTime().toEpochMilli()));
    timeSeriesLearningEngineTask.setAnalysisSaveUrl(createVerificationTaskAnalysisSaveUrl(taskId));
    timeSeriesLearningEngineTask.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    timeSeriesLearningEngineTask.setUuid(taskId);

    return timeSeriesLearningEngineTask;
  }

  private TimeSeriesCanaryLearningEngineTask createTimeSeriesCanaryLearningEngineTask(AnalysisInput input) {
    String taskId = generateUuid();
    VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
        verificationTaskService.getVerificationJobInstanceId(input.getVerificationTaskId()));
    CanaryBlueGreenVerificationJob verificationJob =
        (CanaryBlueGreenVerificationJob) verificationJobInstance.getResolvedJob();
    Preconditions.checkNotNull(verificationJobInstance, "verificationJobInstance can not be null");
    TimeSeriesCanaryLearningEngineTask timeSeriesLearningEngineTask =
        TimeSeriesCanaryLearningEngineTask.builder()
            .preDeploymentDataUrl(preDeploymentDataUrl(input, verificationJobInstance, verificationJob))
            .postDeploymentDataUrl(postDeploymentDataUrl(input, verificationJobInstance))
            .dataLength(
                (int) Duration.between(verificationJobInstance.getStartTime(), input.getStartTime()).toMinutes() + 1)
            .metricTemplateUrl(createMetricTemplateUrl(input))
            .tolerance(verificationJob.getSensitivity().getTolerance())
            .build();

    timeSeriesLearningEngineTask.setVerificationTaskId(input.getVerificationTaskId());
    timeSeriesLearningEngineTask.setAnalysisType(LearningEngineTaskType.TIME_SERIES_CANARY);
    timeSeriesLearningEngineTask.setAnalysisStartTime(input.getStartTime());
    timeSeriesLearningEngineTask.setAnalysisEndTime(input.getEndTime());
    timeSeriesLearningEngineTask.setAnalysisEndEpochMinute(
        TimeUnit.MILLISECONDS.toMinutes(input.getEndTime().toEpochMilli()));
    timeSeriesLearningEngineTask.setAnalysisSaveUrl(createVerificationTaskAnalysisSaveUrl(taskId));
    timeSeriesLearningEngineTask.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    timeSeriesLearningEngineTask.setUuid(taskId);

    DeploymentVerificationTaskInfo deploymentVerificationTaskInfo =
        DeploymentVerificationTaskInfo.builder()
            .deploymentStartTime(verificationJobInstance.getDeploymentStartTime().toEpochMilli())
            .newHostsTrafficSplitPercentage(verificationJobInstance.getNewHostsTrafficSplitPercentage())
            .newVersionHosts(verificationJobInstance.getNewVersionHosts())
            .oldVersionHosts(verificationJobInstance.getOldVersionHosts())
            .build();
    timeSeriesLearningEngineTask.setDeploymentVerificationTaskInfo(deploymentVerificationTaskInfo);

    return timeSeriesLearningEngineTask;
  }

  private TimeSeriesCanaryLearningEngineTask_v2 createDeploymentTimeSeriesLearningEngineTask(AnalysisInput input) {
    String taskId = generateUuid();
    Set<String> controlHosts = new HashSet<>();
    Set<String> testHosts = new HashSet<>();
    if (isNotEmpty(input.getControlHosts())) {
      controlHosts = input.getControlHosts();
    }
    if (isNotEmpty(input.getTestHosts())) {
      testHosts = input.getTestHosts();
    }
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(input.getVerificationJobInstanceId());
    CanaryBlueGreenVerificationJob verificationJob =
        (CanaryBlueGreenVerificationJob) verificationJobInstance.getResolvedJob();
    Preconditions.checkNotNull(verificationJobInstance, "verificationJobInstance can not be null");
    TimeSeriesCanaryLearningEngineTask_v2 timeSeriesLearningEngineTask =
        TimeSeriesCanaryLearningEngineTask_v2.builder()
            .controlDataUrl(getControlDataUrlForDeploymentTimeSeries(input, verificationJobInstance, verificationJob))
            .testDataUrl(getTestDataUrlForDeploymentTimeSeries(input, verificationJobInstance))
            .dataLength(
                (int) Duration.between(verificationJobInstance.getStartTime(), input.getStartTime()).toMinutes() + 1)
            .metricTemplateUrl(createMetricTemplateUrl(input))
            .tolerance(verificationJob.getSensitivity().getTolerance())
            .build();

    timeSeriesLearningEngineTask.setLearningEngineTaskType(input.getLearningEngineTaskType());
    timeSeriesLearningEngineTask.setVerificationTaskId(input.getVerificationTaskId());
    timeSeriesLearningEngineTask.setAnalysisType(input.getLearningEngineTaskType());
    timeSeriesLearningEngineTask.setAnalysisStartTime(input.getStartTime());
    timeSeriesLearningEngineTask.setAnalysisEndTime(input.getEndTime());
    timeSeriesLearningEngineTask.setAnalysisEndEpochMinute(
        TimeUnit.MILLISECONDS.toMinutes(input.getEndTime().toEpochMilli()));
    timeSeriesLearningEngineTask.setAnalysisSaveUrl(createVerificationTaskAnalysisSaveUrl(taskId));
    timeSeriesLearningEngineTask.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    timeSeriesLearningEngineTask.setUuid(taskId);
    timeSeriesLearningEngineTask.setControlHosts(controlHosts);
    timeSeriesLearningEngineTask.setTestHosts(testHosts);

    DeploymentVerificationTaskInfo deploymentVerificationTaskInfo =
        DeploymentVerificationTaskInfo.builder()
            .deploymentStartTime(verificationJobInstance.getDeploymentStartTime().toEpochMilli())
            .newHostsTrafficSplitPercentage(verificationJobInstance.getNewHostsTrafficSplitPercentage())
            .newVersionHosts(verificationJobInstance.getNewVersionHosts())
            .oldVersionHosts(verificationJobInstance.getOldVersionHosts())
            .build();
    timeSeriesLearningEngineTask.setDeploymentVerificationTaskInfo(deploymentVerificationTaskInfo);

    return timeSeriesLearningEngineTask;
  }

  private String createVerificationTaskAnalysisSaveUrl(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + TIMESERIES_VERIFICATION_TASK_SAVE_ANALYSIS_PATH);
    uriBuilder.addParameter("taskId", taskId);
    return getUriString(uriBuilder);
  }

  private TimeSeriesLearningEngineTask createTimeSeriesLearningTask(AnalysisInput input) {
    String taskId = generateUuid();
    TimeSeriesLearningEngineTask timeSeriesLearningEngineTask =
        TimeSeriesLearningEngineTask.builder()
            .cumulativeSumsUrl(createCumulativeSumsUrl(input))
            .keyTransactions(null)
            .metricTemplateUrl(createMetricTemplateUrl(input))
            .previousAnalysisUrl(createPreviousAnalysisUrl(input))
            .previousAnomaliesUrl(createAnomaliesUrl(input))
            .testDataUrl(createTestDataUrl(input))
            .build();
    timeSeriesLearningEngineTask.setVerificationTaskId(input.getVerificationTaskId());
    timeSeriesLearningEngineTask.setAnalysisType(LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES);
    timeSeriesLearningEngineTask.setAnalysisStartTime(input.getStartTime());
    timeSeriesLearningEngineTask.setAnalysisEndTime(input.getEndTime());
    timeSeriesLearningEngineTask.setAnalysisEndEpochMinute(
        TimeUnit.MILLISECONDS.toMinutes(input.getEndTime().toEpochMilli()));
    timeSeriesLearningEngineTask.setAnalysisSaveUrl(createAnalysisSaveUrl(taskId));
    timeSeriesLearningEngineTask.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    timeSeriesLearningEngineTask.setUuid(taskId);

    return timeSeriesLearningEngineTask;
  }
  private String createAnalysisSaveUrl(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + TIMESERIES_SAVE_ANALYSIS_PATH);
    uriBuilder.addParameter("taskId", taskId);
    return getUriString(uriBuilder);
  }

  private String createPreviousAnalysisUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/timeseries-serviceguard-shortterm-history");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    return getUriString(uriBuilder);
  }

  private String createCumulativeSumsUrl(AnalysisInput input) {
    Instant startTime = input.getStartTime().minus(1, ChronoUnit.DAYS);
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/timeseries-serviceguard-cumulative-sums");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("analysisStartTime", startTime.toString());
    uriBuilder.addParameter("analysisEndTime", input.getEndTime().toString());
    return getUriString(uriBuilder);
  }

  private Optional<String> baselineDataUrl(AnalysisInput input, VerificationJobInstance verificationJobInstance,
      VerificationJobInstance baselineVerificationJobInstance) {
    Optional<String> maybeBaselineVerificationTaskId =
        verificationTaskService.findBaselineVerificationTaskId(input.getVerificationTaskId(), verificationJobInstance);
    return maybeBaselineVerificationTaskId.map(baselineVerificationTaskId -> {
      URIBuilder uriBuilder = new URIBuilder();
      uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/time-series-data");
      uriBuilder.addParameter("verificationTaskId", baselineVerificationTaskId);
      uriBuilder.addParameter(
          "startTime", Long.toString(baselineVerificationJobInstance.getStartTime().toEpochMilli()));
      uriBuilder.addParameter("endTime", Long.toString(baselineVerificationJobInstance.getEndTime().toEpochMilli()));
      return getUriString(uriBuilder);
    });
  }
  private String postDeploymentDataUrl(AnalysisInput input, VerificationJobInstance verificationJobInstance) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/time-series-data");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("startTime", Long.toString(verificationJobInstance.getStartTime().toEpochMilli()));
    uriBuilder.addParameter("endTime", Long.toString(input.getEndTime().toEpochMilli()));
    return getUriString(uriBuilder);
  }

  private String getTestDataUrlForDeploymentTimeSeries(
      AnalysisInput input, VerificationJobInstance verificationJobInstance) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/time-series-data");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("startTime", Long.toString(verificationJobInstance.getStartTime().toEpochMilli()));
    uriBuilder.addParameter("endTime", Long.toString(input.getEndTime().toEpochMilli()));
    if (isNotEmpty(input.getTestHosts())) {
      uriBuilder.addParameter("hosts", String.join(",", input.getTestHosts()));
    } else {
      uriBuilder.addParameter("hosts", "");
    }
    return getUriString(uriBuilder);
  }

  private String preDeploymentDataUrl(
      AnalysisInput input, VerificationJobInstance verificationJobInstance, VerificationJob verificationJob) {
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
    Preconditions.checkState(preDeploymentTimeRange.isPresent(),
        "Pre-deployment time range is empty for canary analysis task. This should not happen");
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/time-series-data");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("startTime", Long.toString(preDeploymentTimeRange.get().getStartTime().toEpochMilli()));
    uriBuilder.addParameter("endTime", Long.toString(preDeploymentTimeRange.get().getEndTime().toEpochMilli()));
    return getUriString(uriBuilder);
  }

  private String getControlDataUrlForDeploymentTimeSeries(
      AnalysisInput input, VerificationJobInstance verificationJobInstance, VerificationJob verificationJob) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/time-series-data");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    if (isNotEmpty(input.getControlHosts())) {
      uriBuilder.addParameter("hosts", String.join(",", input.getControlHosts()));
    } else {
      uriBuilder.addParameter("hosts", "");
    }
    if (input.getLearningEngineTaskType().equals(LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES)) {
      uriBuilder.addParameter("startTime", Long.toString(verificationJobInstance.getStartTime().toEpochMilli()));
      uriBuilder.addParameter("endTime", Long.toString(input.getEndTime().toEpochMilli()));
    } else if (input.getLearningEngineTaskType().equals(LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES)) {
      Optional<TimeRange> preDeploymentTimeRange =
          verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
      Preconditions.checkState(preDeploymentTimeRange.isPresent(),
          "Pre-deployment time range is empty for canary analysis task. This should not happen");
      uriBuilder.addParameter("startTime", Long.toString(preDeploymentTimeRange.get().getStartTime().toEpochMilli()));
      uriBuilder.addParameter("endTime", Long.toString(preDeploymentTimeRange.get().getEndTime().toEpochMilli()));
    }
    return getUriString(uriBuilder);
  }

  private String createAnomaliesUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/timeseries-serviceguard-previous-anomalies");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    return getUriString(uriBuilder);
  }

  private String createTestDataUrl(AnalysisInput input) {
    Instant startForTestData = input.getEndTime()
                                   .truncatedTo(ChronoUnit.SECONDS)
                                   .minus(TIMESERIES_SERVICE_GUARD_DATA_LENGTH, ChronoUnit.MINUTES);
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/time-series-data");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter("startTime", Long.toString(startForTestData.toEpochMilli()));
    uriBuilder.addParameter("endTime", Long.toString(input.getEndTime().toEpochMilli()));
    return getUriString(uriBuilder);
  }

  private String createMetricTemplateUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        SERVICE_BASE_URL + "/" + TIMESERIES_ANALYSIS_RESOURCE + "/timeseries-serviceguard-metric-template");
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
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
  public Map<String, ExecutionStatus> getTaskStatus(String verificationTaskId, Set<String> taskIds) {
    return learningEngineTaskService.getTaskStatus(taskIds);
  }

  @Override
  public Map<String, Map<String, List<MetricSumDTO>>> getCumulativeSums(
      String verificationTaskId, Instant startTime, Instant endTime) {
    log.info(
        "Fetching cumulative sums for config: {}, startTime: {}, endTime: {}", verificationTaskId, startTime, endTime);
    List<TimeSeriesCumulativeSums> cumulativeSums =
        hPersistence.createQuery(TimeSeriesCumulativeSums.class, excludeAuthority)
            .filter(TimeSeriesCumulativeSumsKeys.verificationTaskId, verificationTaskId)
            .field(TimeSeriesCumulativeSumsKeys.analysisStartTime)
            .greaterThanOrEq(startTime)
            .field(TimeSeriesCumulativeSumsKeys.analysisEndTime)
            .lessThanOrEq(endTime)
            .asList();

    return TimeSeriesCumulativeSums.convertToMap(cumulativeSums);
  }

  @Override
  public Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> getLongTermAnomalies(String verificationTaskId) {
    return timeSeriesAnomalousPatternsService.getLongTermAnomalies(verificationTaskId);
  }

  @Override
  public Map<String, Map<String, List<Double>>> getShortTermHistory(String verificationTaskId) {
    log.info("Fetching short term history for config: {}", verificationTaskId);
    TimeSeriesShortTermHistory shortTermHistory =
        hPersistence.createQuery(TimeSeriesShortTermHistory.class, excludeAuthority)
            .filter(TimeSeriesShortTermHistoryKeys.verificationTaskId, verificationTaskId)
            .get();
    if (shortTermHistory != null) {
      shortTermHistory.deCompressMetricHistories();
      return shortTermHistory.convertToMap();
    }
    return Collections.emptyMap();
  }

  @Override
  public void saveAnalysis(String taskId, DeploymentTimeSeriesAnalysisDTO analysis) {
    LearningEngineTask learningEngineTask = learningEngineTaskService.get(taskId);
    Preconditions.checkNotNull(learningEngineTask, "Needs to be a valid LE task.");

    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder()
            .startTime(learningEngineTask.getAnalysisStartTime())
            .endTime(learningEngineTask.getAnalysisEndTime())
            .verificationTaskId(learningEngineTask.getVerificationTaskId())
            .hostSummaries(analysis.getHostSummaries())
            .transactionMetricSummaries(analysis.getTransactionMetricSummaries())
            .risk(analysis.getRisk())
            .score(analysis.getScore())
            .failFast(analysis.isFailFast())
            .build();
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysis);
    learningEngineTaskService.markCompleted(taskId);
  }

  @Override
  public List<TransactionMetricRisk> getTopTimeSeriesTransactionMetricRisk(
      List<String> verificationTaskIds, Instant startTime, Instant endTime) {
    List<TimeSeriesRiskSummary> timeSeriesRiskSummaries =
        hPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority)
            .field(TimeSeriesRiskSummaryKeys.verificationTaskId)
            .in(verificationTaskIds)
            .field(TimeSeriesRiskSummaryKeys.analysisEndTime)
            .greaterThanOrEq(startTime)
            .field(TimeSeriesRiskSummaryKeys.analysisEndTime)
            .lessThan(endTime)
            .order(TimeSeriesRiskSummaryKeys.analysisEndTime)
            .asList();
    List<TransactionMetricRisk> transactionMetricRisks =
        timeSeriesRiskSummaries.stream()
            .map(timeSeriesRiskSummary -> timeSeriesRiskSummary.getTransactionMetricRiskList())
            .flatMap(List::stream)
            .collect(Collectors.toList());
    Collections.sort(transactionMetricRisks, Comparator.comparing(TransactionMetricRisk::getMetricRisk));
    return transactionMetricRisks.subList(
        Math.max(0, transactionMetricRisks.size() - ANALYSIS_RISK_RESULTS_LIMIT), transactionMetricRisks.size());
  }

  @Override
  public void saveAnalysis(String taskId, ServiceGuardTimeSeriesAnalysisDTO analysis) {
    LearningEngineTask learningEngineTask = learningEngineTaskService.get(taskId);
    Preconditions.checkNotNull(learningEngineTask, "Needs to be a valid LE task.");
    analysis.setVerificationTaskId(learningEngineTask.getVerificationTaskId());
    Instant endTime = learningEngineTask.getAnalysisEndTime();
    Instant startTime = endTime.minus(Duration.ofMinutes(CV_ANALYSIS_WINDOW_MINUTES));
    analysis.setAnalysisStartTime(startTime);
    analysis.setAnalysisEndTime(endTime);
    String cvConfigId = verificationTaskService.getCVConfigId(learningEngineTask.getVerificationTaskId());
    TimeSeriesShortTermHistory shortTermHistory = buildShortTermHistory(analysis);
    TimeSeriesCumulativeSums cumulativeSums = buildCumulativeSums(analysis, startTime, endTime);
    TimeSeriesRiskSummary riskSummary = buildRiskSummary(analysis, startTime, endTime);

    saveShortTermHistory(shortTermHistory);
    timeSeriesAnomalousPatternsService.saveAnomalousPatterns(analysis, learningEngineTask.getVerificationTaskId());
    hPersistence.save(riskSummary);
    hPersistence.save(cumulativeSums);
    log.info("Saving analysis for config: {}", cvConfigId);
    learningEngineTaskService.markCompleted(taskId);
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    Preconditions.checkNotNull(cvConfig, "CVConfig can not be null");
    double risk = getOverallRisk(analysis);
    long anomalousMetricCount = analysis.getTxnMetricAnalysisData()
                                    .values()
                                    .stream()
                                    .flatMap(x -> x.values().stream().filter(y -> y.getRisk() == Risk.NEED_ATTENTION))
                                    .count();
    heatMapService.updateRiskScore(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
        cvConfig.getProjectIdentifier(), cvConfig, cvConfig.getCategory(), startTime, risk, anomalousMetricCount, 0);
    timeSeriesRecordService.updateRiskScores(cvConfigId, riskSummary);
  }

  private double getOverallRisk(ServiceGuardTimeSeriesAnalysisDTO analysis) {
    return analysis.getOverallMetricScores().values().stream().mapToDouble(score -> score).max().orElse(-2.0);
  }

  public void saveShortTermHistory(TimeSeriesShortTermHistory shortTermHistory) {
    shortTermHistory.compressMetricHistories();
    Query<TimeSeriesShortTermHistory> query =
        hPersistence.createQuery(TimeSeriesShortTermHistory.class, excludeAuthority)
            .filter(TimeSeriesShortTermHistoryKeys.verificationTaskId, shortTermHistory.getVerificationTaskId());
    UpdateOperations<TimeSeriesShortTermHistory> updateOperations =
        hPersistence.createUpdateOperations(TimeSeriesShortTermHistory.class)
            .setOnInsert(TimeSeriesShortTermHistoryKeys.uuid, generateUuid())
            .setOnInsert(TimeSeriesShortTermHistoryKeys.verificationTaskId, shortTermHistory.getVerificationTaskId())
            .set(TimeSeriesShortTermHistoryKeys.compressedMetricHistories,
                shortTermHistory.getCompressedMetricHistories());
    hPersistence.upsert(query, updateOperations);
  }

  @Override
  public List<TimeSeriesMetricDefinition> getMetricTemplate(String verificationTaskId) {
    VerificationTask verificationTask = verificationTaskService.get(verificationTaskId);
    MetricCVConfig cvConfig = null;
    if (verificationTask.getTaskInfo().getTaskType() == TaskType.DEPLOYMENT) {
      cvConfig = (MetricCVConfig) verificationJobInstanceService.getEmbeddedCVConfig(
          ((VerificationTask.DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId(),
          ((VerificationTask.DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId());
    } else if (verificationTask.getTaskInfo().getTaskType() == TaskType.LIVE_MONITORING) {
      cvConfig = (MetricCVConfig) cvConfigService.get(
          ((VerificationTask.LiveMonitoringInfo) verificationTask.getTaskInfo()).getCvConfigId());
    }
    List<TimeSeriesMetricDefinition> timeSeriesMetricDefinitions = new ArrayList<>();
    if (cvConfig != null) {
      timeSeriesMetricDefinitions = timeSeriesRecordService.getTimeSeriesMetricDefinitions(cvConfig);
    }
    // in LE we pass metric identifier as the metric_name, as metric_name is the identifier for LE
    timeSeriesMetricDefinitions.forEach(timeSeriesMetricDefinition
        -> timeSeriesMetricDefinition.setMetricName(timeSeriesMetricDefinition.getMetricIdentifier()));
    // Log instances when deviation type is null
    timeSeriesMetricDefinitions.stream()
        .filter(timeSeriesMetricDefinition -> Objects.isNull(timeSeriesMetricDefinition.getDeviationType()))
        .forEach(timeSeriesMetricDefinition
            -> log.error("Deviation type should never be null" + timeSeriesMetricDefinition));
    return timeSeriesMetricDefinitions;
  }

  @Override
  public List<TimeSeriesRecordDTO> getTimeSeriesRecordDTOs(
      String verificationTaskId, Instant startTime, Instant endTime) {
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOS =
        timeSeriesRecordService.getTimeSeriesRecordDTOs(verificationTaskId, startTime, endTime);
    // in LE we pass metric identifier as the metric_name, as metric_name is the identifier for LE.
    timeSeriesRecordDTOS.forEach(
        timeSeriesRecordDTO -> timeSeriesRecordDTO.setMetricName(timeSeriesRecordDTO.getMetricIdentifier()));
    return timeSeriesRecordDTOS;
  }

  @Override
  public List<TimeSeriesRecordDTO> getDeploymentMetricTimeSeriesRecordDTOs(
      String verificationTaskId, Instant startTime, Instant endTime, String commaSeparatedHosts) {
    Set<String> hostSet = new HashSet<>();
    if (commaSeparatedHosts != null) {
      hostSet = Arrays.stream(commaSeparatedHosts.split(",")).collect(Collectors.toSet());
    }
    List<TimeSeriesRecordDTO> timeSeriesRecordDTOS = timeSeriesRecordService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, startTime, endTime, hostSet);
    // in LE we pass metric identifier as the metric_name, as metric_name is the identifier for LE.
    timeSeriesRecordDTOS.forEach(
        timeSeriesRecordDTO -> timeSeriesRecordDTO.setMetricName(timeSeriesRecordDTO.getMetricIdentifier()));
    return timeSeriesRecordDTOS;
  }

  private TimeSeriesRiskSummary buildRiskSummary(
      ServiceGuardTimeSeriesAnalysisDTO analysisDTO, Instant startTime, Instant endTime) {
    List<TransactionMetricRisk> metricRiskList = new ArrayList<>();
    analysisDTO.getTxnMetricAnalysisData().forEach((txnName, metricMap) -> {
      metricMap.forEach((metricIdentifier, metricData) -> {
        TransactionMetricRisk metricRisk = TransactionMetricRisk.builder()
                                               .transactionName(txnName)
                                               .metricIdentifier(metricIdentifier)
                                               .metricRisk(metricData.getRisk().getValue())
                                               .metricScore(metricData.getScore())
                                               .lastSeenTime(metricData.getLastSeenTime())
                                               .longTermPattern(metricData.isLongTermPattern())
                                               .build();
        metricRiskList.add(metricRisk);
      });
    });
    return TimeSeriesRiskSummary.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .analysisStartTime(startTime)
        .analysisEndTime(endTime)
        .transactionMetricRiskList(metricRiskList)
        .overallRisk(getOverallRisk(analysisDTO))
        .build();
  }
  private TimeSeriesCumulativeSums buildCumulativeSums(
      ServiceGuardTimeSeriesAnalysisDTO analysisDTO, Instant startTime, Instant endTime) {
    Map<String, Map<String, TimeSeriesCumulativeSums.MetricSum>> cumulativeSumsMap = new HashMap<>();
    analysisDTO.getTxnMetricAnalysisData().forEach((txnName, metricMap) -> {
      cumulativeSumsMap.put(txnName, new HashMap<>());
      metricMap.forEach((metricIdentifier, metricSums) -> {
        if (metricSums.getCumulativeSums() != null) {
          TimeSeriesCumulativeSums.MetricSum sums = metricSums.getCumulativeSums().toMetricSum();
          sums.setMetricIdentifier(metricIdentifier);
          cumulativeSumsMap.get(txnName).put(metricIdentifier, sums);
        }
      });
    });

    List<TimeSeriesCumulativeSums.TransactionMetricSums> transactionMetricSums =
        TimeSeriesCumulativeSums.convertMapToTransactionMetricSums(cumulativeSumsMap);

    return TimeSeriesCumulativeSums.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .transactionMetricSums(transactionMetricSums)
        .analysisStartTime(startTime)
        .analysisEndTime(endTime)
        .build();
  }

  private TimeSeriesShortTermHistory buildShortTermHistory(ServiceGuardTimeSeriesAnalysisDTO analysisDTO) {
    Map<String, Map<String, List<Double>>> shortTermHistoryMap = new HashMap<>();
    analysisDTO.getTxnMetricAnalysisData().forEach((txnName, metricMap) -> {
      shortTermHistoryMap.put(txnName, new HashMap<>());
      metricMap.forEach((metricIdentifier, txnMetricData) -> {
        shortTermHistoryMap.get(txnName).put(metricIdentifier, txnMetricData.getShortTermHistory());
      });
    });

    return TimeSeriesShortTermHistory.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .transactionMetricHistories(TimeSeriesShortTermHistory.convertFromMap(shortTermHistoryMap))
        .build();
  }
}
