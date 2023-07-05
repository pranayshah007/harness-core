/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL;
import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;
import static software.wings.common.VerificationConstants.CV_DATA_COLLECTION_INTERVAL_IN_MINUTE;
import static software.wings.common.VerificationConstants.DUMMY_HOST_NAME;
import static software.wings.common.VerificationConstants.GET_LOG_FEEDBACKS;
import static software.wings.common.VerificationConstants.IS_EXPERIMENTAL;
import static software.wings.common.VerificationConstants.SERVICE_GUARD_ANALYSIS_WINDOW_MINS;
import static software.wings.common.VerificationConstants.TIME_DELAY_QUERY_MINS;
import static software.wings.common.VerificationConstants.VERIFICATION_SERVICE_BASE_URL;
import static software.wings.common.VerificationConstants.getLogAnalysisStates;
import static software.wings.common.VerificationConstants.getMetricAnalysisStates;
import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.PREDECTIVE_HISTORY_MINUTES;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.PREDICTIVE;
import static software.wings.service.impl.analysis.TimeSeriesMlAnalysisType.TIMESERIES_24x7;
import static software.wings.sm.states.AbstractMetricAnalysisState.COMPARISON_WINDOW;
import static software.wings.sm.states.AbstractMetricAnalysisState.MIN_REQUESTS_PER_MINUTE;
import static software.wings.sm.states.AbstractMetricAnalysisState.PARALLEL_PROCESSES;
import static software.wings.sm.states.AbstractMetricAnalysisState.SMOOTH_WINDOW;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SortOrder.OrderType;
import io.harness.entities.CVTask;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.service.intfc.ContinuousVerificationService;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;
import io.harness.service.intfc.TimeSeriesAnalysisService;
import io.harness.time.Timestamp;

import software.wings.alerts.AlertStatus;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.VerificationLogContext;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.FeedbackAction;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisStatus;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.SupervisedTrainingStatus.SupervisedTrainingStatusKeys;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates.TimeSeriesMetricTemplatesKeys;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.MLExperiments;
import software.wings.service.impl.newrelic.MLExperiments.MLExperimentsKeys;
import software.wings.service.impl.splunk.LogAnalysisResult;
import software.wings.service.impl.splunk.SplunkAnalysisCluster;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.analysis.LogAnalysisResource;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.verification.CVTaskService;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.log.LogsCVConfiguration;

import com.codahale.metrics.annotation.Counted;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.bson.types.ObjectId;

/**
 * Created by rsingh on 10/9/18.
 */
@Slf4j
public class ContinuousVerificationServiceImpl implements ContinuousVerificationService {
  private static final int ITERATOR_INTERVAL_WITH_BUFFER_SECONDS = 150;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private VerificationManagerClientHelper verificationManagerClientHelper;
  @Inject private VerificationManagerClient verificationManagerClient;
  @Inject private LearningEngineService learningEngineService;
  @Inject private TimeSeriesAnalysisService analysisService;
  @Inject private LogAnalysisService logAnalysisService;
  @Inject private HarnessMetricRegistry metricRegistry;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UsageMetricsHelper usageMetricsHelper;
  @Inject private CVTaskService cvTaskService;
  @Inject private CVActivityLogService cvActivityLogService;
  @Inject @Named("alertsCreationExecutor") protected ExecutorService executorService;

  @Override
  public boolean shouldPerformServiceGuardTasks(Account account) {
    if (account.getLicenseInfo() == null
        || (AccountStatus.ACTIVE.equals(account.getLicenseInfo().getAccountStatus())
            && Arrays.asList(AccountType.PAID, AccountType.TRIAL)
                   .contains(account.getLicenseInfo().getAccountType()))) {
      return true;
    }
    return false;
  }

  @Override
  @Counted
  @Timed
  public void triggerDataCollection(CVConfiguration cvConfiguration) {
    if (cvConfiguration.isEnabled24x7()) {
      if (getMetricAnalysisStates().contains(cvConfiguration.getStateType())) {
        triggerAPMDataCollection(cvConfiguration);
      } else if (getLogAnalysisStates().contains(cvConfiguration.getStateType())) {
        triggerLogDataCollection(cvConfiguration);
      } else {
        throw new IllegalStateException(
            "Unknown state type when trying to trigger service guard data collection" + cvConfiguration.getStateType());
      }
    }
  }

  @Override
  @Counted
  @Timed
  public void triggerAPMDataCollection(CVConfiguration cvConfiguration) {
    long endMinute = getFlooredTimeForTimeSeries(
        TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis()) - TIME_DELAY_QUERY_MINS, 0);

    if (cvConfiguration.isEnabled24x7() && getMetricAnalysisStates().contains(cvConfiguration.getStateType())) {
      if (!shouldCollectData(
              cvConfiguration, timeSeriesAnalysisService.getCreatedTimeOfLastCollection(cvConfiguration))) {
        return;
      }
      long startTime = getDataCollectionStartMinForAPM(cvConfiguration, endMinute);
      long endTime = TimeUnit.MINUTES.toMillis(endMinute);
      if (endTime - startTime >= TimeUnit.MINUTES.toMillis(CV_DATA_COLLECTION_INTERVAL_IN_MINUTE)) {
        log.info("triggering data collection for state {} config {} startTime {} endTime {} collectionMinute {}",
            cvConfiguration.getStateType(), cvConfiguration.getUuid(), startTime, endTime, endMinute);
        if (isCVTaskBasedCollectionEnabled(cvConfiguration)) {
          createCVTask(cvConfiguration, startTime, endTime);
        } else {
          verificationManagerClientHelper.callManagerWithRetry(verificationManagerClient.triggerCVDataCollection(
              cvConfiguration.getUuid(), cvConfiguration.getStateType(), startTime, endTime));
        }
      }
    }
  }

  @Override
  @Counted
  @Timed
  public boolean triggerAPMDataCollection(String accountId) {
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);
    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getMetricAnalysisStates().contains(cvConfiguration.getStateType()))
        .forEach(cvConfiguration -> { triggerAPMDataCollection(cvConfiguration); });
    return true;
  }

  /**
   * If there are any LE tasks QUEUED or RUNNING - dont collect.
   * If lastDataCollectionTime < 10mins - no backoff
   * if lastDataCollection time between 10-30mins - collect every 5mins
   * if lastDataCollection time between 30-60mins - collect every 10mins
   * if lastDataCollection time  >60mins - collect every 15mins
   * @param lastDataCollectionTime
   * @return
   */
  private boolean shouldCollectData(CVConfiguration cvConfiguration, Optional<Long> lastDataCollectionTime) {
    if ((!lastDataCollectionTime.isPresent() || lastDataCollectionTime.get() <= 0)
        && Instant.ofEpochMilli(cvConfiguration.getLastUpdatedAt())
               .isBefore(Instant.now().minus(30, ChronoUnit.DAYS))) {
      log.info(
          "The config {} for account {} has not collected any data for the past 30 days. We will be disabling that config now. ",
          cvConfiguration.getUuid(), cvConfiguration.getAccountId());
      cvConfigurationService.disableConfig(cvConfiguration.getUuid());
      return false;
    }
    if (learningEngineService.isTaskRunningOrQueued(cvConfiguration.getUuid())) {
      log.info(
          "For {}, there are learning engine tasks that are still QUEUED or RUNNING. We will skip data collection for now.",
          cvConfiguration.getUuid());
      return false;
    }
    if (!lastDataCollectionTime.isPresent() || lastDataCollectionTime.get() <= 0) {
      log.info(
          "For {}, this is the first collection, so we will go ahead and collect data.", cvConfiguration.getUuid());
      return true;
    }
    boolean shouldCollectData = false;
    long currentTime = Timestamp.currentMinuteBoundary();
    long timeSinceLastCollection = currentTime - lastDataCollectionTime.get();
    Optional<Long> allowedDataCollectionTime = Optional.empty();
    long interval = 0;
    if (timeSinceLastCollection <= TimeUnit.MINUTES.toMillis(10)) {
      log.info("For {}, the last collected time is less than 10mins ago. We will go ahead and collect data.",
          cvConfiguration.getUuid());
      shouldCollectData = true;
    } else if (timeSinceLastCollection < TimeUnit.MINUTES.toMillis(30)) {
      interval = 5;
      log.info(
          "For {}, the lastCollectionTime was more than 10mins and less than 30mins ago. Current Time: {}, lastCollectionTime {}, interval: {}",
          cvConfiguration.getUuid(), currentTime, lastDataCollectionTime, interval);
    } else if (timeSinceLastCollection < TimeUnit.MINUTES.toMillis(60)) {
      interval = 10;
      log.info(
          "For {}, the lastCollectionTime was more than 30mins and less than 60mins ago. Current Time: {}, lastCollectionTime {}, interval: {}",
          cvConfiguration.getUuid(), currentTime, lastDataCollectionTime, interval);
    } else {
      interval = 15;
      log.info(
          "For {}, the lastCollectionTime was more than 60 ago. Current Time: {}, lastCollectionTime {}, interval: {}",
          cvConfiguration.getUuid(), currentTime, lastDataCollectionTime, interval);
    }
    if (!shouldCollectData) {
      allowedDataCollectionTime = getNextAllowedTime(currentTime, lastDataCollectionTime.get(), interval);
    }
    if (allowedDataCollectionTime.isPresent() || shouldCollectData) {
      log.info("For {}, we can now collect data based on the backoff value. Current Time: {}, lastCollectionTime {}",
          cvConfiguration.getUuid(), currentTime, lastDataCollectionTime);
      return true;
    }
    log.info("For {}, we cannot collect data due to the backoff value. Current Time: {}, lastCollectionTime {}",
        cvConfiguration.getUuid(), currentTime, lastDataCollectionTime);
    return false;
  }

  private Optional<Long> getNextAllowedTime(long currentTime, long lastDataCollectionTime, long intervalTime) {
    long allowedDataCollectionTime = lastDataCollectionTime;
    while (allowedDataCollectionTime <= currentTime) {
      if (allowedDataCollectionTime + TimeUnit.SECONDS.toMillis(ITERATOR_INTERVAL_WITH_BUFFER_SECONDS) > currentTime) {
        return Optional.of(allowedDataCollectionTime);
      }
      allowedDataCollectionTime += TimeUnit.MINUTES.toMillis(intervalTime);
    }
    return Optional.empty();
  }
  private long getDataCollectionStartMinForAPM(CVConfiguration cvConfiguration, long endMinute) {
    long maxCVCollectionMinute = timeSeriesAnalysisService.getMaxCVCollectionMinute(
        cvConfiguration.getAppId(), cvConfiguration.getUuid(), cvConfiguration.getAccountId());
    long startTime;
    if (maxCVCollectionMinute <= 0) {
      // no collection has been done so far
      startTime = TimeUnit.MINUTES.toMillis(endMinute) - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES)
          - TimeUnit.SECONDS.toMillis(CRON_POLL_INTERVAL);
    } else if (endMinute - maxCVCollectionMinute > PREDECTIVE_HISTORY_MINUTES) {
      // the last collection was more than 2 hours ago.
      // So our analysis is going to start from 2hours (with 2 hours before that as history)
      startTime = TimeUnit.MINUTES.toMillis(endMinute - PREDECTIVE_HISTORY_MINUTES * 2);
      log.info(
          "The last datacollection for {} was more than 2 hours ago, so we will restart from 2hours. Setting start time to {}",
          cvConfiguration.getUuid(), startTime);
    } else {
      // All is well. Happy case.
      startTime = TimeUnit.MINUTES.toMillis(maxCVCollectionMinute);
    }
    log.info("For {} MaxCollectionMinute: {}, Starttime is {}, endTime is {}", cvConfiguration.getUuid(),
        maxCVCollectionMinute > 0 ? Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(maxCVCollectionMinute)) : -1,
        Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(TimeUnit.MINUTES.toMillis(endMinute)));
    return startTime;
  }

  private long getAnalysisStartMinuteForAPM(CVConfiguration cvConfiguration, long lastCVDataCollectionMinute) {
    long lastCVAnalysisMinute =
        timeSeriesAnalysisService.getLastCVAnalysisMinute(cvConfiguration.getAppId(), cvConfiguration.getUuid());
    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    if (lastCVAnalysisMinute <= 0) {
      log.info(
          "For account {} and CV config {} name {} type {} no analysis has been done yet. This is going to be first analysis",
          cvConfiguration.getAccountId(), cvConfiguration.getUuid(), cvConfiguration.getName(),
          cvConfiguration.getStateType());
      return lastCVDataCollectionMinute - TimeUnit.SECONDS.toMinutes(CRON_POLL_INTERVAL);
    } else if (lastCVAnalysisMinute + PREDECTIVE_HISTORY_MINUTES < currentMinute) {
      // it has been more than 2 hours since we did analysis, so we should just do for current time - 2hours and take
      // over from there.
      log.info("The last analysis was more than 2 hours ago. We're restarting the analysis from minute: {} for {}",
          currentMinute - PREDECTIVE_HISTORY_MINUTES, cvConfiguration.getUuid());
      long restartTime = currentMinute - PREDECTIVE_HISTORY_MINUTES;
      if ((restartTime - lastCVAnalysisMinute) % CRON_POLL_INTERVAL_IN_MINUTES != 0) {
        restartTime -= (restartTime - lastCVAnalysisMinute) % CRON_POLL_INTERVAL_IN_MINUTES;
      }
      // check to see if there was any task created for this cvConfig for one hour before expected restart time. If yes,
      // dont do this. Return -1
      boolean isTaskRunning = learningEngineService.isTaskRunningOrQueued(
          cvConfiguration.getUuid(), restartTime - PREDECTIVE_HISTORY_MINUTES - 60);
      if (isTaskRunning) {
        return -1;
      }
      return restartTime;
    } else {
      return lastCVAnalysisMinute;
    }
  }

  @Override
  @Counted
  @Timed
  public void triggerServiceGuardAnalysis(CVConfiguration cvConfiguration) {
    if (cvConfiguration.isEnabled24x7()) {
      if (getMetricAnalysisStates().contains(cvConfiguration.getStateType())) {
        log.info("Triggering Timeseries analysis for {}", cvConfiguration.getUuid());
        triggerTimeseriesAnalysis(cvConfiguration);
      } else if (getLogAnalysisStates().contains(cvConfiguration.getStateType())) {
        log.info("Triggering Log analysis for {}", cvConfiguration.getUuid());
        triggerLogsL1Clustering(cvConfiguration);
        triggerLogsL2Clustering(cvConfiguration);
        triggerLogDataAnalysis(cvConfiguration);
        triggerFeedbackAnalysis(cvConfiguration);
      } else {
        throw new IllegalStateException(
            "Invalid state for cvConfig:" + cvConfiguration.getUuid() + ", " + cvConfiguration.getStateType());
      }
    }
  }

  private void triggerTimeseriesAnalysis(CVConfiguration cvConfiguration) {
    String accountId = cvConfiguration.getAccountId();
    try (VerificationLogContext ignored = new VerificationLogContext(cvConfiguration.getAccountId(),
             cvConfiguration.getUuid(), null, cvConfiguration.getStateType().getDelegateStateType(), OVERRIDE_ERROR)) {
      try {
        log.info(
            "Executing APM data analysis Job for accountId {} and configId {}", accountId, cvConfiguration.getUuid());
        long lastCVDataCollectionMinute = timeSeriesAnalysisService.getMaxCVCollectionMinute(
            cvConfiguration.getAppId(), cvConfiguration.getUuid(), cvConfiguration.getAccountId());
        if (lastCVDataCollectionMinute <= 0) {
          log.info("For account {} and CV config {} name {} type {} no data has been collected yet. Skipping analysis",
              cvConfiguration.getAccountId(), cvConfiguration.getUuid(), cvConfiguration.getName(),
              cvConfiguration.getStateType());
          return;
        }

        long lastCVAnalysisMinute =
            timeSeriesAnalysisService.getLastCVAnalysisMinute(cvConfiguration.getAppId(), cvConfiguration.getUuid());

        if (lastCVAnalysisMinute <= 0) {
          log.info(
              "For account {} and CV config {} name {} type {} no analysis has been done yet. This is going to be first analysis",
              cvConfiguration.getAccountId(), cvConfiguration.getUuid(), cvConfiguration.getName(),
              cvConfiguration.getStateType());
        }
        long analysisStartMinute = getAnalysisStartMinuteForAPM(cvConfiguration, lastCVDataCollectionMinute);
        if (analysisStartMinute == -1) {
          log.info(
              "The last analysis was more than 2 hours ago but there is currently a task running for {}. So exiting.",
              cvConfiguration.getUuid());
          return;
        }
        if (lastCVDataCollectionMinute - lastCVAnalysisMinute >= TimeUnit.SECONDS.toMinutes(CRON_POLL_INTERVAL)) {
          long endMinute = analysisStartMinute + TimeUnit.SECONDS.toMinutes(CRON_POLL_INTERVAL);

          // since analysis startMin is inclusive in LE, we  need to add 1.
          analysisStartMinute += 1;

          Set<String> tags = getTags(cvConfiguration.getUuid());
          List<MLExperiments> experiments = get24x7Experiments(MLAnalysisType.TIME_SERIES.name());
          if (isEmpty(tags)) {
            LearningEngineAnalysisTask learningEngineAnalysisTask =
                createLearningEngineAnalysisTask(accountId, cvConfiguration, analysisStartMinute, endMinute, null);

            if (learningEngineAnalysisTask != null) {
              learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);

              log.info("Triggering Data Analysis for account {} ", accountId);
              log.info("Queuing analysis task for state {} config {} and tag {} with startTime {}",
                  cvConfiguration.getStateType(), cvConfiguration.getUuid(), null, analysisStartMinute);

              for (MLExperiments experiment : experiments) {
                LearningEngineExperimentalAnalysisTask task = createLearningEngineAnalysisExperimentalTask(
                    accountId, cvConfiguration, analysisStartMinute, endMinute, null, experiment.getExperimentName());
                learningEngineService.addLearningEngineExperimentalAnalysisTask(task);
              }
            }
          }
          timeSeriesLETask(tags, accountId, cvConfiguration, analysisStartMinute, endMinute, experiments);
        }
      } catch (Exception ex) {
        log.error("Exception occurred while triggering metric data collection for cvConfig {}",
            cvConfiguration.getUuid(), ex);
      }
    }
  }

  @Override
  @Counted
  @Timed
  public void triggerServiceGuardTimeSeriesAnalysis(String accountId) {
    log.info("Triggering Data Analysis for account {} ", accountId);
    // List all the CV configurations for a given account
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);

    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period
    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getMetricAnalysisStates().contains(cvConfiguration.getStateType()))
        .forEach(cvConfiguration -> triggerTimeseriesAnalysis(cvConfiguration));
  }

  private void timeSeriesLETask(Set<String> tags, String accountId, CVConfiguration cvConfiguration,
      long analysisStartMinute, long endMinute, List<MLExperiments> experiments) {
    for (String tag : tags) {
      LearningEngineAnalysisTask learningEngineAnalysisTask =
          createLearningEngineAnalysisTask(accountId, cvConfiguration, analysisStartMinute, endMinute, tag);
      if (learningEngineAnalysisTask != null) {
        learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);

        log.info("Triggering Data Analysis for account {} ", accountId);
        log.info("Queuing analysis task for state {} config {} and tag {} with startTime {}",
            cvConfiguration.getStateType(), cvConfiguration.getUuid(), tag, analysisStartMinute);

        for (MLExperiments experiment : experiments) {
          LearningEngineExperimentalAnalysisTask task = createLearningEngineAnalysisExperimentalTask(
              accountId, cvConfiguration, analysisStartMinute, endMinute, null, experiment.getExperimentName());
          learningEngineService.addLearningEngineExperimentalAnalysisTask(task);
          log.info(
              "Created experimental analysis task for state {} config {} and tag {} with startTime {}, experimentName {}",
              cvConfiguration.getStateType(), cvConfiguration.getUuid(), tag, analysisStartMinute,
              experiment.getExperimentName());
        }
      }
    }
  }

  private Set<String> getTags(String cvConfigId) {
    Set<String> tags = new HashSet<>();
    TimeSeriesMetricTemplates template = wingsPersistence.createQuery(TimeSeriesMetricTemplates.class, excludeAuthority)
                                             .filter(TimeSeriesMetricTemplatesKeys.cvConfigId, cvConfigId)
                                             .get();

    if (template != null) {
      template.getMetricTemplates().forEach((key, value) -> {
        if (isNotEmpty(value.getTags())) {
          tags.addAll(value.getTags());
        }
      });
    }
    return tags;
  }

  private List<MLExperiments> get24x7Experiments(String analysisType) {
    return wingsPersistence.createQuery(MLExperiments.class, excludeAuthority)
        .filter("is24x7", true)
        .filter(MLExperimentsKeys.ml_analysis_type, analysisType)
        .asList();
  }

  private LearningEngineAnalysisTask createLearningEngineAnalysisTask(
      String accountId, CVConfiguration cvConfiguration, long startMin, long endMin, String tag) {
    String learningTaskId = generateUuid();

    String testInputUrl = getDataFetchUrl(cvConfiguration, startMin - PREDECTIVE_HISTORY_MINUTES, endMin, tag);
    String metricAnalysisSaveUrl = getMetricAnalysisSaveUrl(cvConfiguration, endMin, learningTaskId, tag);
    String historicalAnalysisUrl = getHistoricalAnalysisUrl(cvConfiguration, endMin, tag);
    String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
        + VerificationConstants.NOTIFY_LEARNING_FAILURE + "?taskId=" + learningTaskId;

    String metricTemplateUrl = getMetricTemplateUrl(accountId, cvConfiguration.getAppId(),
        cvConfiguration.getStateType(), cvConfiguration.getServiceId(), cvConfiguration.getUuid());

    final String stateExecutionIdForLETask = CV_24x7_STATE_EXECUTION + "-" + cvConfiguration.getUuid();

    // clear up any old failed task with the same ID and time.
    learningEngineService.checkAndUpdateFailedLETask(stateExecutionIdForLETask, (int) endMin);

    if (!learningEngineService.isEligibleToCreateTask(
            stateExecutionIdForLETask, cvConfiguration.getUuid(), endMin, MLAnalysisType.TIME_SERIES)) {
      return null;
    }
    int nextBackoffCount = learningEngineService.getNextServiceGuardBackoffCount(
        stateExecutionIdForLETask, cvConfiguration.getUuid(), endMin, MLAnalysisType.TIME_SERIES);

    LearningEngineAnalysisTask learningEngineAnalysisTask =
        LearningEngineAnalysisTask.builder()
            .accountId(accountId)
            .service_id(cvConfiguration.getServiceId())
            .state_execution_id(stateExecutionIdForLETask)
            .cvConfigId(cvConfiguration.getUuid())
            .analysis_start_min((int) startMin - PREDECTIVE_HISTORY_MINUTES)
            .analysis_minute(endMin)
            .prediction_start_time((int) startMin - 1)
            .smooth_window(0)
            .tolerance(0)
            .min_rpm(0)
            .comparison_unit_window(0)
            .parallel_processes(0)
            .test_input_url(testInputUrl)
            .previous_analysis_url(getPreviousAnalysisUrl(cvConfiguration, tag))
            .historical_analysis_url(historicalAnalysisUrl)
            .control_input_url("")
            .analysis_save_url(metricAnalysisSaveUrl)
            .metric_template_url(metricTemplateUrl)
            .analysis_failure_url(failureUrl)
            .previous_anomalies_url(getPreviousAnomaliesUrl(cvConfiguration, tag))
            .cumulative_sums_url(getCumulativeSumsUrl(cvConfiguration, (int) endMin, tag))
            .control_nodes(Sets.newHashSet(DUMMY_HOST_NAME))
            .test_nodes(Sets.newHashSet(DUMMY_HOST_NAME))
            .stateType(cvConfiguration.getStateType())
            .ml_analysis_type(MLAnalysisType.TIME_SERIES)
            .time_series_ml_analysis_type(TIMESERIES_24x7)
            .smooth_window(SMOOTH_WINDOW)
            .tolerance(cvConfiguration.getAnalysisTolerance().tolerance())
            .min_rpm(MIN_REQUESTS_PER_MINUTE)
            .comparison_unit_window(COMPARISON_WINDOW)
            .parallel_processes(PARALLEL_PROCESSES)
            .is24x7Task(true)
            .service_guard_backoff_count(nextBackoffCount)
            .tag(tag)
            .alertThreshold(getAlertThreshold(cvConfiguration, endMin))
            .keyTransactionsUrl(getKeyTransactionsUrl(cvConfiguration))
            .priority(1)
            .build();
    learningEngineAnalysisTask.setAppId(cvConfiguration.getAppId());
    learningEngineAnalysisTask.setUuid(learningTaskId);

    return learningEngineAnalysisTask;
  }

  private LearningEngineExperimentalAnalysisTask createLearningEngineAnalysisExperimentalTask(String accountId,
      CVConfiguration cvConfiguration, long startMin, long endMin, String tag, String experimentName) {
    String learningTaskId = generateUuid();

    String testInputUrl = getDataFetchUrl(cvConfiguration, startMin - PREDECTIVE_HISTORY_MINUTES, endMin, tag);
    String historicalAnalysisUrl = getHistoricalAnalysisUrl(cvConfiguration, endMin, tag);

    String metricTemplateUrl = getMetricTemplateUrl(accountId, cvConfiguration.getAppId(),
        cvConfiguration.getStateType(), cvConfiguration.getServiceId(), cvConfiguration.getUuid());

    final String stateExecutionIdForLETask =
        CV_24x7_STATE_EXECUTION + "-" + cvConfiguration.getUuid() + "_" + endMin + "-" + tag;

    LearningEngineExperimentalAnalysisTask learningEngineAnalysisTask =
        LearningEngineExperimentalAnalysisTask.builder()
            .accountId(cvConfiguration.getAccountId())
            .service_id(cvConfiguration.getServiceId())
            .state_execution_id(stateExecutionIdForLETask)
            .cvConfigId(cvConfiguration.getUuid())
            .analysis_start_min((int) startMin - PREDECTIVE_HISTORY_MINUTES)
            .analysis_minute(endMin)
            .prediction_start_time((int) startMin - 1)
            .smooth_window(0)
            .tolerance(0)
            .min_rpm(0)
            .comparison_unit_window(0)
            .parallel_processes(0)
            .test_input_url(testInputUrl)
            .previous_analysis_url(getPreviousAnalysisUrl(cvConfiguration, tag))
            .historical_analysis_url(historicalAnalysisUrl)
            .control_input_url("")
            .analysis_save_url(getSaveUrlForExperimentalTask(learningTaskId))
            .metric_template_url(metricTemplateUrl)
            .previous_anomalies_url(getPreviousAnomaliesUrl(cvConfiguration, tag))
            .cumulative_sums_url(getCumulativeSumsUrl(cvConfiguration, (int) endMin, tag))
            .control_nodes(Sets.newHashSet(DUMMY_HOST_NAME))
            .test_nodes(Sets.newHashSet(DUMMY_HOST_NAME))
            .stateType(cvConfiguration.getStateType())
            .ml_analysis_type(MLAnalysisType.TIME_SERIES)
            .time_series_ml_analysis_type(TIMESERIES_24x7)
            .smooth_window(SMOOTH_WINDOW)
            .tolerance(cvConfiguration.getAnalysisTolerance().tolerance())
            .min_rpm(MIN_REQUESTS_PER_MINUTE)
            .comparison_unit_window(COMPARISON_WINDOW)
            .parallel_processes(PARALLEL_PROCESSES)
            .is24x7Task(true)
            .tag(tag)
            .experiment_name(experimentName)
            .alertThreshold(getAlertThreshold(cvConfiguration, endMin))
            .keyTransactionsUrl(getKeyTransactionsUrl(cvConfiguration))
            .build();
    learningEngineAnalysisTask.setAppId(cvConfiguration.getAppId());
    learningEngineAnalysisTask.setUuid(learningTaskId);

    return learningEngineAnalysisTask;
  }

  private String getSaveUrlForExperimentalTask(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/save-dummy-experimental-247");
    uriBuilder.addParameter("cvConfigId", "Dummy");
    uriBuilder.addParameter("taskId", taskId);
    return getUriString(uriBuilder);
  }

  private String getMetricTemplateUrl(
      String accountId, String appId, StateType stateType, String serviceId, String cvConfigId) {
    final String stateExecutionIdForLETask = CV_24x7_STATE_EXECUTION + "-" + cvConfigId;
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/get-metric-template");
    uriBuilder.addParameter("accountId", accountId);
    uriBuilder.addParameter("appId", appId);
    uriBuilder.addParameter("stateType", stateType.toString());
    uriBuilder.addParameter("cvConfigId", cvConfigId);
    uriBuilder.addParameter("stateExecutionId", stateExecutionIdForLETask);
    return getUriString(uriBuilder);
  }

  private String getMetricAnalysisSaveUrl(CVConfiguration cvConfiguration, long endMinute, String taskId, String tag) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/save-analysis");
    uriBuilder.addParameter("accountId", cvConfiguration.getAccountId());
    uriBuilder.addParameter("applicationId", cvConfiguration.getAppId());
    uriBuilder.addParameter("stateType", cvConfiguration.getStateType().toString());
    uriBuilder.addParameter("serviceId", cvConfiguration.getServiceId());
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    uriBuilder.addParameter("analysisMinute", String.valueOf(endMinute));
    uriBuilder.addParameter("taskId", taskId);
    if (tag != null) {
      uriBuilder.addParameter("tag", tag);
    }
    return getUriString(uriBuilder);
  }
  private String getUriString(URIBuilder uriBuilder) {
    try {
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private String getDataFetchUrl(CVConfiguration cvConfiguration, long startMinute, long endMinute, String tag) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/get-metric-data-247");
    uriBuilder.addParameter("accountId", cvConfiguration.getAccountId());
    uriBuilder.addParameter("appId", cvConfiguration.getAppId());
    uriBuilder.addParameter("stateType", cvConfiguration.getStateType().toString());
    uriBuilder.addParameter("serviceId", cvConfiguration.getServiceId());
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    uriBuilder.addParameter("analysisStartMin", String.valueOf(startMinute));
    uriBuilder.addParameter("analysisEndMin", String.valueOf(endMinute));
    if (tag != null) {
      uriBuilder.addParameter("tag", tag);
    }
    return getUriString(uriBuilder);
  }

  private String getPreviousAnalysisUrl(CVConfiguration cvConfiguration, String tag) {
    long min = timeSeriesAnalysisService.getLastCVAnalysisMinute(cvConfiguration.getAppId(), cvConfiguration.getUuid());
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/previous-analysis-247");
    uriBuilder.addParameter("appId", cvConfiguration.getAppId());
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    uriBuilder.addParameter("dataCollectionMin", String.valueOf(min));
    if (tag != null) {
      uriBuilder.addParameter("tag", tag);
    }
    return getUriString(uriBuilder);
  }

  private String getHistoricalAnalysisUrl(CVConfiguration cvConfiguration, long minute, String tag) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/historical-analysis-24x7");
    uriBuilder.addParameter("accountId", cvConfiguration.getAccountId());
    uriBuilder.addParameter("applicationId", cvConfiguration.getAppId());
    uriBuilder.addParameter("serviceId", cvConfiguration.getServiceId());
    uriBuilder.addParameter("analysisMinute", String.valueOf(minute));
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    if (tag != null) {
      uriBuilder.addParameter("tag", tag);
    }
    return getUriString(uriBuilder);
  }

  private String getPreviousAnomaliesUrl(CVConfiguration cvConfiguration, String tag) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/previous-anomalies-247");
    uriBuilder.addParameter("accountId", cvConfiguration.getAccountId());
    uriBuilder.addParameter("applicationId", cvConfiguration.getAppId());
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    if (tag != null) {
      uriBuilder.addParameter("tag", tag);
    }
    return getUriString(uriBuilder);
  }

  private String getCumulativeSumsUrl(CVConfiguration cvConfiguration, int analysisMinute, String tag) {
    int startMin = analysisMinute - (int) TimeUnit.DAYS.toMinutes(1);
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/cumulative-sums-247");
    uriBuilder.addParameter("accountId", cvConfiguration.getAccountId());
    uriBuilder.addParameter("applicationId", cvConfiguration.getAppId());
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    uriBuilder.addParameter("analysisMinStart", String.valueOf(startMin));
    uriBuilder.addParameter("analysisMinEnd", String.valueOf(analysisMinute));
    if (tag != null) {
      uriBuilder.addParameter("tag", tag);
    }
    return getUriString(uriBuilder);
  }

  private String getKeyTransactionsUrl(CVConfiguration cvConfiguration) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(
        VERIFICATION_SERVICE_BASE_URL + "/" + MetricDataAnalysisService.RESOURCE_URL + "/key-transactions-247");
    uriBuilder.addParameter("cvConfigId", cvConfiguration.getUuid());
    return getUriString(uriBuilder);
  }

  private long getCollectionStartTimeForLogs(LogsCVConfiguration logsCVConfiguration, long maxCvCollectionMinute) {
    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    if (maxCvCollectionMinute <= 0) {
      log.info("For {} there has been no data collected yet. So starting with baselineStart: {}",
          logsCVConfiguration.getUuid(), logsCVConfiguration.getBaselineStartMinute());
      return logsCVConfiguration.getBaselineStartMinute();
    } else if (maxCvCollectionMinute == logsCVConfiguration.getBaselineEndMinute()) {
      // if baselineEnd is within the past 2 hours, then just continue to nextMinute. Else start from 2 hours ago.
      if (!isBeforeTwoHours(logsCVConfiguration.getBaselineEndMinute(), false)) {
        log.info("For {} baselineEnd was within the past 2 hours, continuing to the next minute {}",
            logsCVConfiguration.getUuid(), maxCvCollectionMinute + 1);
        return maxCvCollectionMinute + 1;
      } else {
        long expectedStart = getFlooredStartTime(currentMinute, PREDECTIVE_HISTORY_MINUTES);
        log.info("For {} baselineEnd was more than 2 hours ago, we will start the collection from 2hours ago now: {}",
            logsCVConfiguration.getUuid(), expectedStart);
        return expectedStart;
      }
    } else {
      // 3 cases.
      if (maxCvCollectionMinute > logsCVConfiguration.getBaselineStartMinute()
          && maxCvCollectionMinute < logsCVConfiguration.getBaselineEndMinute()) {
        log.info("We are still collecting in the baseline window. For {}, the collection start time is going to be {}",
            logsCVConfiguration.getUuid(), maxCvCollectionMinute + 1);
        return maxCvCollectionMinute + 1;
      } else if (!isBeforeTwoHours(maxCvCollectionMinute, false)) {
        log.info("All is as expected. For {}, the collection start time is going to be {}",
            logsCVConfiguration.getUuid(), maxCvCollectionMinute + 1);
        return maxCvCollectionMinute + 1;
      } else {
        long expectedStart = getFlooredStartTime(currentMinute, PREDECTIVE_HISTORY_MINUTES);
        log.info(
            "It has been more than 2 hours since last collection. For {}, the collection start time is going to be {}",
            logsCVConfiguration.getUuid(), expectedStart);
        return expectedStart;
      }
    }
  }

  private long getFlooredStartTime(long currentTime, long delta) {
    long expectedStart = currentTime - delta;
    if (Math.floorMod(expectedStart - 1, CRON_POLL_INTERVAL_IN_MINUTES) != 0) {
      expectedStart -= Math.floorMod(expectedStart - 1, CRON_POLL_INTERVAL_IN_MINUTES);
    }
    return expectedStart;
  }

  private long getFlooredTimeForTimeSeries(long currentTime, long delta) {
    long expectedStart = currentTime - delta;
    if (Math.floorMod(expectedStart, CV_DATA_COLLECTION_INTERVAL_IN_MINUTE) != 0) {
      expectedStart -= Math.floorMod(expectedStart, CV_DATA_COLLECTION_INTERVAL_IN_MINUTE);
    }
    return expectedStart;
  }

  private boolean isBeforeTwoHours(long minuteToCheck, boolean includeBuffer) {
    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    long boundaryMinute =
        minuteToCheck + PREDECTIVE_HISTORY_MINUTES + (includeBuffer ? SERVICE_GUARD_ANALYSIS_WINDOW_MINS * 2 : 0);
    return boundaryMinute < currentMinute;
  }

  void triggerLogDataCollection(CVConfiguration cvConfiguration) {
    long endMinute = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis()) - TIME_DELAY_QUERY_MINS;
    try {
      if (!shouldCollectData(cvConfiguration, logAnalysisService.getCreatedTimeOfLastCollection(cvConfiguration))) {
        log.info("Not collecting data for {} due to backoff.", cvConfiguration.getUuid());
        return;
      }
      LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;
      if (logsCVConfiguration.getBaselineStartMinute() < 0 || logsCVConfiguration.getBaselineEndMinute() < 0) {
        log.error("For {} baseline is not set. Skipping collection", logsCVConfiguration.getUuid());
        return;
      }
      final long maxCVCollectionMinute =
          logAnalysisService.getMaxCVCollectionMinute(logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid());

      long startTime =
          TimeUnit.MINUTES.toMillis(getCollectionStartTimeForLogs(logsCVConfiguration, maxCVCollectionMinute));
      long endTime = startTime + TimeUnit.MINUTES.toMillis(CV_DATA_COLLECTION_INTERVAL_IN_MINUTE) - 1;

      if (PREDICTIVE == cvConfiguration.getComparisonStrategy()
          && maxCVCollectionMinute >= logsCVConfiguration.getBaselineEndMinute()) {
        AnalysisContext analysisContext =
            wingsPersistence.get(AnalysisContext.class, logsCVConfiguration.getContextId());
        endTime = startTime + TimeUnit.MINUTES.toMillis(1);

        if (maxCVCollectionMinute >= logsCVConfiguration.getBaselineEndMinute() + analysisContext.getTimeDuration()) {
          log.info("collection for {} is done", analysisContext.getStateExecutionId());
          return;
        }
      }
      if (endTime < TimeUnit.MINUTES.toMillis(endMinute)) {
        if (isCVTaskBasedCollectionEnabled(cvConfiguration)) {
          createCVTask(cvConfiguration, startTime, endTime);
        } else {
          log.info("triggering data collection for state {} config {} startTime {} endTime {} collectionMinute {}",
              cvConfiguration.getStateType(), cvConfiguration.getUuid(), startTime, endTime, endMinute);
          verificationManagerClientHelper.callManagerWithRetry(verificationManagerClient.triggerCVDataCollection(
              cvConfiguration.getUuid(), cvConfiguration.getStateType(), startTime, endTime));
        }
      }
    } catch (Exception ex) {
      log.error("Exception occurred while triggering datacollection for cvConfig {}", cvConfiguration.getUuid(), ex);
    }
  }

  @Override
  @Counted
  @Timed
  public boolean triggerLogDataCollection(String accountId) {
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);

    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getLogAnalysisStates().contains(cvConfiguration.getStateType()))
        .forEach(cvConfiguration -> { triggerLogDataCollection(cvConfiguration); });
    return true;
  }

  private boolean isCVTaskBasedCollectionEnabled(CVConfiguration cvConfiguration) {
    if (cvConfiguration.isCVTaskBasedCollectionEnabled()) {
      return true;
    } else if (cvConfiguration.isCVTaskBasedCollectionFeatureFlagged()) {
      return isFeatureFlagEnabled(
          cvConfiguration.getCVTaskBasedCollectionFeatureFlag(), cvConfiguration.getAccountId());
    } else {
      return false;
    }
  }

  private void createCVTask(CVConfiguration cvConfiguration, long startTime, long endTime) {
    DataCollectionInfoV2 dataCollectionInfo = cvConfiguration.toDataCollectionInfo();
    dataCollectionInfo.setStartTime(Instant.ofEpochMilli(startTime));
    dataCollectionInfo.setEndTime(Instant.ofEpochMilli(endTime));
    CVTask cvTask = CVTask.builder()
                        .status(ExecutionStatus.QUEUED)
                        .cvConfigId(cvConfiguration.getUuid())
                        .accountId(cvConfiguration.getAccountId())
                        .dataCollectionInfo(dataCollectionInfo)
                        .build();
    cvTaskService.saveCVTask(cvTask);
    cvActivityLogService
        .getLoggerByCVConfigId(
            cvConfiguration.getAccountId(), cvConfiguration.getUuid(), TimeUnit.MILLISECONDS.toMinutes(endTime))
        .info("Enqueued service guard task for data collection for time range %t to %t", startTime, endTime);
  }

  @Override
  @Counted
  @Timed
  public boolean triggerWorkflowDataCollection(AnalysisContext context) {
    long lastDataCollectionMinute;
    if (context.getAnalysisType() == MLAnalysisType.TIME_SERIES) {
      lastDataCollectionMinute = timeSeriesAnalysisService.getLastDataCollectedMinute(
          context.getAppId(), context.getStateExecutionId(), context.getStateType());
    } else {
      lastDataCollectionMinute = logAnalysisService.getLastLogDataCollectedMinute(
          context.getQuery(), context.getAppId(), context.getStateExecutionId(), context.getStateType());
    }
    log.info("Inside triggerWorkflowCollection with stateType {}, stateExecutionId {} lastDataCollectionMinute {}",
        context.getStateType(), context.getStateExecutionId(), lastDataCollectionMinute);
    boolean isStateValid = verificationManagerClientHelper
                               .callManagerWithRetry(verificationManagerClient.isStateValid(
                                   context.getAppId(), context.getStateExecutionId()))
                               .getResource();
    if (isStateValid) {
      try {
        if (lastDataCollectionMinute < 0) {
          return verificationManagerClientHelper
              .callManagerWithRetry(verificationManagerClient.triggerWorkflowDataCollection(
                  context.getUuid(), context.getStartDataCollectionMinute()))
              .getResource();
        } else {
          if (lastDataCollectionMinute - context.getStartDataCollectionMinute() < context.getTimeDuration() - 1) {
            log.info("Trigger Data Collection with stateType {}, stateExecutionId {} lastDataCollectionMinute {}",
                context.getStateType(), context.getStateExecutionId(), lastDataCollectionMinute);
            return verificationManagerClientHelper
                .callManagerWithRetry(verificationManagerClient.triggerWorkflowDataCollection(
                    context.getUuid(), lastDataCollectionMinute + 1))
                .getResource();
          } else {
            log.info("Completed Data Collection for stateType {}, stateExecutionId {}", context.getStateType(),
                context.getStateExecutionId());
            return false;
          }
        }
      } catch (Exception e) {
        log.error(
            "Failed to call manager for data collection for workflow with context {} with exception {}", context, e);
      }
      return true;
    } else {
      log.info("State is no longer valid for stateType {}, stateExecutionId {}", context.getStateType(),
          context.getStateExecutionId());
      return false;
    }
  }

  @Override
  @Counted
  @Timed
  public void markWorkflowDataCollectionDone(AnalysisContext context) {
    log.info("for {} markig the data collection to be done", context.getStateExecutionId());
    wingsPersistence.updateField(
        AnalysisContext.class, context.getUuid(), AnalysisContextKeys.perMinCollectionFinished, true);
  }

  @Timed
  public void triggerLogsL1Clustering(String accountId) {
    // List all the CV configurations for a given account
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);

    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period
    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getLogAnalysisStates().contains(cvConfiguration.getStateType())
                && cvConfiguration.getStateType() != StateType.SPLUNKV2)
        .forEach(cvConfiguration -> triggerLogsL1Clustering(cvConfiguration));
  }

  @Counted
  @Timed
  private void triggerLogsL1Clustering(CVConfiguration cvConfiguration) {
    String accountId = cvConfiguration.getAccountId();

    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period

    try (VerificationLogContext ignored = new VerificationLogContext(cvConfiguration.getAccountId(),
             cvConfiguration.getUuid(), null, cvConfiguration.getStateType().getDelegateStateType(), OVERRIDE_ERROR)) {
      long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
      log.info("triggering logs L1 Clustering for account {} and cvConfigId {}", accountId, cvConfiguration.getUuid());
      long lastCVDataCollectionMinute =
          logAnalysisService.getMaxCVCollectionMinute(cvConfiguration.getAppId(), cvConfiguration.getUuid());
      if (lastCVDataCollectionMinute <= 0) {
        log.info("For account {} and CV config {} name {} type {} no data has been collected yet. Skipping clustering",
            cvConfiguration.getAccountId(), cvConfiguration.getUuid(), cvConfiguration.getName(),
            cvConfiguration.getStateType());
        return;
      }
      LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;
      long minLogRecordMinute = logAnalysisService.getLogRecordMinute(
          cvConfiguration.getAppId(), cvConfiguration.getUuid(), ClusterLevel.H0, OrderType.ASC);
      long maxLogRecordMinute = logAnalysisService.getLogRecordMinute(
          cvConfiguration.getAppId(), cvConfiguration.getUuid(), ClusterLevel.H0, OrderType.DESC);
      if (lastCVDataCollectionMinute > logsCVConfiguration.getBaselineStartMinute()
          && lastCVDataCollectionMinute <= logsCVConfiguration.getBaselineEndMinute()) {
        log.info(
            "For account {} and CV config {} name {} type {} We are currently doing L1 clustering in the baseline window.",
            cvConfiguration.getAccountId(), cvConfiguration.getUuid(), cvConfiguration.getName(),
            cvConfiguration.getStateType());
      } else if (isBeforeTwoHours(lastCVDataCollectionMinute, true) || isBeforeTwoHours(maxLogRecordMinute, false)) {
        log.info(
            "For account {} and CV config {} name {} type {} There has been no new data in the past 2 hours. Skipping L1 clustering",
            cvConfiguration.getAccountId(), cvConfiguration.getUuid(), cvConfiguration.getName(),
            cvConfiguration.getStateType());
        return;
      }

      if (isBeforeTwoHours(minLogRecordMinute, false)) {
        if (minLogRecordMinute >= logsCVConfiguration.getBaselineStartMinute()
            && minLogRecordMinute <= logsCVConfiguration.getBaselineEndMinute()) {
          log.info("For {} MinLogRecord minute is {} and it is within the baseline window", cvConfiguration.getUuid(),
              minLogRecordMinute);
        } else {
          log.info(
              "for {} minLogRecordMinute is more than 2 hours ago but maxLogRecordMinute is less than 2 hours ago. We will start L1 clustering from 2 hours ago.",
              cvConfiguration.getUuid());

          minLogRecordMinute = getCeilingFifteenMinBoundaryTime(currentMinute, PREDECTIVE_HISTORY_MINUTES);
        }
      }

      log.info("Clustering pending between {} and {}", minLogRecordMinute, lastCVDataCollectionMinute);

      for (long logRecordMinute = minLogRecordMinute;
           logRecordMinute > 0 && logRecordMinute <= lastCVDataCollectionMinute; logRecordMinute++) {
        Set<String> hosts = logAnalysisService.getHostsForMinute(cvConfiguration.getAppId(),
            LogDataRecordKeys.cvConfigId, cvConfiguration.getUuid(), logRecordMinute, ClusterLevel.L0);

        // there can be a race between finding all the host for a min and le finishing the cluster task and
        // deleting L0 data
        if (isEmpty(hosts)) {
          log.info("For {} minute {} did not find hosts for level {} continuing...", cvConfiguration.getUuid(),
              logRecordMinute, ClusterLevel.L0);
          logAnalysisService.saveClusteredLogData(cvConfiguration.getAppId(), cvConfiguration.getUuid(),
              ClusterLevel.L1, (int) logRecordMinute, null, Lists.newArrayList());
          continue;
        }
        final String taskId = generateUuid();
        String inputLogsUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
            + LogAnalysisResource.ANALYSIS_GET_24X7_LOG_URL + "?cvConfigId=" + cvConfiguration.getUuid()
            + "&appId=" + cvConfiguration.getAppId() + "&clusterLevel=" + ClusterLevel.L0
            + "&logCollectionMinute=" + logRecordMinute;
        String clusteredLogSaveUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
            + LogAnalysisResource.ANALYSIS_STATE_SAVE_24X7_CLUSTERED_LOG_URL
            + "?cvConfigId=" + cvConfiguration.getUuid() + "&appId=" + cvConfiguration.getAppId()
            + "&clusterLevel=" + ClusterLevel.L1 + "&logCollectionMinute=" + logRecordMinute;
        String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
            + VerificationConstants.NOTIFY_LEARNING_FAILURE + "?taskId=" + taskId;

        String stateExecutionIdForLETask = "LOGS_CLUSTER_L1_" + cvConfiguration.getUuid() + "_" + logRecordMinute;
        learningEngineService.checkAndUpdateFailedLETask(stateExecutionIdForLETask, (int) logRecordMinute);

        if (learningEngineService.isEligibleToCreateTask(
                stateExecutionIdForLETask, cvConfiguration.getUuid(), logRecordMinute, MLAnalysisType.LOG_CLUSTER)) {
          int nextBackoffCount = learningEngineService.getNextServiceGuardBackoffCount(
              stateExecutionIdForLETask, cvConfiguration.getUuid(), logRecordMinute, MLAnalysisType.LOG_CLUSTER);
          LearningEngineAnalysisTask analysisTask =
              LearningEngineAnalysisTask.builder()
                  .accountId(accountId)
                  .control_input_url(inputLogsUrl)
                  .analysis_save_url(clusteredLogSaveUrl)
                  .analysis_failure_url(failureUrl)
                  .state_execution_id(stateExecutionIdForLETask)
                  .service_id(cvConfiguration.getServiceId())
                  .control_nodes(hosts)
                  .sim_threshold(0.99)
                  .service_guard_backoff_count(nextBackoffCount)
                  .analysis_minute(logRecordMinute)
                  .cluster_level(ClusterLevel.L1.getLevel())
                  .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                  .stateType(cvConfiguration.getStateType())
                  .query(Lists.newArrayList(((LogsCVConfiguration) cvConfiguration).getQuery()))
                  .is24x7Task(true)
                  .priority(1)
                  .cvConfigId(cvConfiguration.getUuid())
                  .alertThreshold(getAlertThreshold(cvConfiguration, logRecordMinute))
                  .build();
          analysisTask.setAppId(cvConfiguration.getAppId());
          analysisTask.setUuid(taskId);

          learningEngineService.addLearningEngineAnalysisTask(analysisTask);

          List<MLExperiments> experiments = get24x7Experiments(MLAnalysisType.LOG_CLUSTER.name());
          for (MLExperiments experiment : experiments) {
            LearningEngineExperimentalAnalysisTask expTask =
                LearningEngineExperimentalAnalysisTask.builder()
                    .accountId(cvConfiguration.getAccountId())
                    .control_input_url(inputLogsUrl)
                    .analysis_save_url(getSaveUrlForExperimentalTask(taskId))
                    .state_execution_id(
                        "LOGS_CLUSTER_L1_" + cvConfiguration.getUuid() + "_" + logRecordMinute + generateUuid())
                    .service_id(cvConfiguration.getServiceId())
                    .control_nodes(hosts)
                    .sim_threshold(0.99)
                    .analysis_minute(logRecordMinute)
                    .cluster_level(ClusterLevel.L1.getLevel())
                    .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                    .stateType(cvConfiguration.getStateType())
                    .query(Lists.newArrayList(((LogsCVConfiguration) cvConfiguration).getQuery()))
                    .is24x7Task(true)
                    .cvConfigId(cvConfiguration.getUuid())
                    .experiment_name(experiment.getExperimentName())
                    .alertThreshold(getAlertThreshold(cvConfiguration, logRecordMinute))
                    .build();
            expTask.setAppId(cvConfiguration.getAppId());
            learningEngineService.addLearningEngineExperimentalAnalysisTask(expTask);
          }
        }
      }
    }
  }

  /**
   * This helps us ensure that the log analysis min will be in 15min boundary always.
   * @param currentTime - current time in epoch millis
   * @param delta - how far before the current time do we want to go to get the ceiling minute
   * @return
   *
   * Example: current time is 9:10am, delta is 120mins, the return value will be 7:15am
   */
  private long getCeilingFifteenMinBoundaryTime(long currentTime, long delta) {
    long expectedStart = currentTime - delta;
    if (Math.floorMod(expectedStart, CRON_POLL_INTERVAL_IN_MINUTES) != 0) {
      expectedStart += CRON_POLL_INTERVAL_IN_MINUTES - Math.floorMod(expectedStart, CRON_POLL_INTERVAL_IN_MINUTES);
    }
    return expectedStart;
  }

  private Optional<Long> getL2ClusteringTime(LogsCVConfiguration logsCVConfiguration) {
    long minLogRecordL1Minute = logAnalysisService.getLogRecordMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.H1, OrderType.ASC);
    long maxLogRecordL1Minute = logAnalysisService.getLogRecordMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.H1, OrderType.DESC);
    long previousL2RecordMinute = logAnalysisService.getLogRecordMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.H2, OrderType.DESC);
    long previousHFRecordMinute = logAnalysisService.getLogRecordMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.HF, OrderType.DESC);
    Optional<Long> l2ClusteringMinute;
    if (minLogRecordL1Minute <= 0) {
      log.info("For {}, there has been no L1 clustering yet. ", logsCVConfiguration.getUuid());
      l2ClusteringMinute = Optional.empty();
    } else if (maxLogRecordL1Minute - minLogRecordL1Minute < CRON_POLL_INTERVAL_IN_MINUTES - 1) {
      log.info(
          "For {} We do not have 15mins worth of L1s yet. So we have nothing to do now", logsCVConfiguration.getUuid());
      l2ClusteringMinute = Optional.empty();
    } else if (previousL2RecordMinute <= 0 && previousHFRecordMinute <= 0) {
      long l2Minute = minLogRecordL1Minute + CRON_POLL_INTERVAL_IN_MINUTES - 1;
      log.info(
          "For {} this is the first ever L2 clustering task. There has been nothing done before this. Returning {}",
          logsCVConfiguration.getUuid(), l2Minute);

      l2ClusteringMinute = Optional.of(l2Minute);
    } else if (previousHFRecordMinute > previousL2RecordMinute) {
      long l2Minute = previousHFRecordMinute + CRON_POLL_INTERVAL_IN_MINUTES;
      l2ClusteringMinute = Optional.of(l2Minute);
      log.info("For {} we will follow the previous HF minute The previous minute was {}. The next one will be {}",
          logsCVConfiguration.getUuid(), previousHFRecordMinute, l2Minute);
    } else {
      long l2Minute = previousL2RecordMinute + CRON_POLL_INTERVAL_IN_MINUTES;
      l2ClusteringMinute = Optional.of(l2Minute);
      log.info("For {} we will follow the previous L2 minute The previous minute was {}. The next one will be {}",
          logsCVConfiguration.getUuid(), previousL2RecordMinute, l2Minute);
    }

    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Instant.now().toEpochMilli());
    if (l2ClusteringMinute.isPresent() && l2ClusteringMinute.get() > currentMinute) {
      log.info("For {}, next l2 minute is in the future {}. Returning empty", logsCVConfiguration.getUuid(),
          l2ClusteringMinute.get());
      l2ClusteringMinute = Optional.empty();
    } else if (l2ClusteringMinute.isPresent()) {
      // check to see if it's in the 15min boundary. Else, self-correct.
      long l2MinInBoundary = getCeilingFifteenMinBoundaryTime(l2ClusteringMinute.get(), 0);

      // make sure it's within 2hours if it's not in the baseline window.
      if (l2MinInBoundary >= logsCVConfiguration.getBaselineStartMinute()
          && l2MinInBoundary <= logsCVConfiguration.getBaselineEndMinute()) {
        log.info("The returned L2minute is {} and it is within the baseline window", l2MinInBoundary);
      } else if (isBeforeTwoHours(l2MinInBoundary, true)) {
        l2MinInBoundary = getFirstAnalysisMinuteInThePast2Hours(logsCVConfiguration, l2MinInBoundary);
      }

      l2ClusteringMinute = Optional.of(l2MinInBoundary);
    }
    log.info("Returning L2 minute as {}", l2ClusteringMinute);
    return l2ClusteringMinute;
  }

  @Override
  @Counted
  @Timed
  public void triggerLogsL2Clustering(String accountId) {
    // List all the CV configurations for a given account
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);

    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period
    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getLogAnalysisStates().contains(cvConfiguration.getStateType())
                && cvConfiguration.getStateType() != StateType.SPLUNKV2)
        .forEach(cvConfiguration -> triggerLogsL2Clustering(cvConfiguration));
  }

  @Counted
  @Timed
  private void triggerLogsL2Clustering(CVConfiguration cvConfiguration) {
    String accountId = cvConfiguration.getAccountId();
    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period

    try (VerificationLogContext ignored = new VerificationLogContext(cvConfiguration.getAccountId(),
             cvConfiguration.getUuid(), null, cvConfiguration.getStateType().getDelegateStateType(), OVERRIDE_ERROR)) {
      log.info("triggering logs L2 Clustering for account {} and cvConfigId {}", accountId, cvConfiguration.getUuid());
      try {
        Optional<Long> potentialL2Minute = getL2ClusteringTime((LogsCVConfiguration) cvConfiguration);

        if (!potentialL2Minute.isPresent()) {
          log.info("For {} it is not time to do L2 clustering yet", cvConfiguration.getUuid());
          return;
        }

        long l2ClusteringMinute = potentialL2Minute.get();
        long l2StartMinute = potentialL2Minute.get() - CRON_POLL_INTERVAL_IN_MINUTES + 1;

        for (long logRecordMinute = l2StartMinute; logRecordMinute < l2ClusteringMinute; logRecordMinute++) {
          Set<String> hosts =
              logAnalysisService.getHostsForMinute(cvConfiguration.getAppId(), LogDataRecordKeys.cvConfigId,
                  cvConfiguration.getUuid(), logRecordMinute, ClusterLevel.L0, ClusterLevel.H0);
          if (isNotEmpty(hosts)) {
            log.info(
                "For CV config {} there is still node data clustering is pending for {} for minute {}. Skipping L2 clustering",
                cvConfiguration.getUuid(), hosts, logRecordMinute);
            return;
          }

          hosts = logAnalysisService.getHostsForMinute(cvConfiguration.getAppId(), LogDataRecordKeys.cvConfigId,
              cvConfiguration.getUuid(), logRecordMinute, ClusterLevel.L1, ClusterLevel.H1);
          if (isEmpty(hosts)) {
            log.info("For CV config {} there is no clustering data present for minute {}. Skipping L2 clustering",
                cvConfiguration.getUuid(), logRecordMinute);
            return;
          }
        }

        log.info("for {} for minute from {} to {} everything is in place, proceeding for L2 Clustering",
            cvConfiguration.getUuid(), l2StartMinute, l2ClusteringMinute);

        final String taskId = generateUuid();
        String inputLogsUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
            + LogAnalysisResource.ANALYSIS_GET_24X7_ALL_LOGS_URL + "?cvConfigId=" + cvConfiguration.getUuid()
            + "&appId=" + cvConfiguration.getAppId() + "&clusterLevel=" + ClusterLevel.L1
            + "&startMinute=" + l2StartMinute + "&endMinute=" + l2ClusteringMinute;
        String clusteredLogSaveUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
            + LogAnalysisResource.ANALYSIS_STATE_SAVE_24X7_CLUSTERED_LOG_URL
            + "?cvConfigId=" + cvConfiguration.getUuid() + "&appId=" + cvConfiguration.getAppId()
            + "&clusterLevel=" + ClusterLevel.L2 + "&logCollectionMinute=" + l2ClusteringMinute;

        String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
            + VerificationConstants.NOTIFY_LEARNING_FAILURE + "?taskId=" + taskId;
        String stateExecutionIdForLETask = "LOGS_CLUSTER_L2_" + cvConfiguration.getUuid() + "_" + l2ClusteringMinute;
        learningEngineService.checkAndUpdateFailedLETask(stateExecutionIdForLETask, (int) l2ClusteringMinute);

        if (learningEngineService.isEligibleToCreateTask(
                stateExecutionIdForLETask, cvConfiguration.getUuid(), l2ClusteringMinute, MLAnalysisType.LOG_CLUSTER)) {
          int nextBackoffCount = learningEngineService.getNextServiceGuardBackoffCount(
              stateExecutionIdForLETask, cvConfiguration.getUuid(), l2ClusteringMinute, MLAnalysisType.LOG_CLUSTER);
          LearningEngineAnalysisTask analysisTask =
              LearningEngineAnalysisTask.builder()
                  .accountId(accountId)
                  .control_input_url(inputLogsUrl)
                  .analysis_save_url(clusteredLogSaveUrl)
                  .analysis_failure_url(failureUrl)
                  .state_execution_id(stateExecutionIdForLETask)
                  .service_id(cvConfiguration.getServiceId())
                  .control_nodes(Collections.emptySet())
                  .sim_threshold(0.99)
                  .service_guard_backoff_count(nextBackoffCount)
                  .analysis_minute(l2ClusteringMinute)
                  .cluster_level(ClusterLevel.L2.getLevel())
                  .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                  .stateType(cvConfiguration.getStateType())
                  .query(Lists.newArrayList(((LogsCVConfiguration) cvConfiguration).getQuery()))
                  .is24x7Task(true)
                  .priority(1)
                  .cvConfigId(cvConfiguration.getUuid())
                  .alertThreshold(getAlertThreshold(cvConfiguration, l2ClusteringMinute))
                  .build();
          analysisTask.setAppId(cvConfiguration.getAppId());
          analysisTask.setUuid(taskId);

          final boolean taskQueued = learningEngineService.addLearningEngineAnalysisTask(analysisTask);
          if (taskQueued) {
            log.info("L2 Clustering queued for cvConfig {} from minute {} to minute {}", cvConfiguration.getUuid(),
                l2StartMinute, l2ClusteringMinute);
          }
          List<MLExperiments> experiments = get24x7Experiments(MLAnalysisType.LOG_CLUSTER.name());
          for (MLExperiments experiment : experiments) {
            LearningEngineExperimentalAnalysisTask expTask =
                LearningEngineExperimentalAnalysisTask.builder()
                    .accountId(cvConfiguration.getUuid())
                    .control_input_url(inputLogsUrl)
                    .analysis_save_url(getSaveUrlForExperimentalTask(taskId))
                    .state_execution_id("LOGS_CLUSTER_L2_" + cvConfiguration.getUuid() + "_" + l2ClusteringMinute + "-"
                        + generateUUID())
                    .service_id(cvConfiguration.getServiceId())
                    .control_nodes(Collections.emptySet())
                    .sim_threshold(0.99)
                    .analysis_minute(l2ClusteringMinute)
                    .cluster_level(ClusterLevel.L2.getLevel())
                    .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                    .stateType(cvConfiguration.getStateType())
                    .query(Lists.newArrayList(((LogsCVConfiguration) cvConfiguration).getQuery()))
                    .is24x7Task(true)
                    .cvConfigId(cvConfiguration.getUuid())
                    .experiment_name(experiment.getExperimentName())
                    .alertThreshold(getAlertThreshold(cvConfiguration, l2ClusteringMinute))
                    .build();
            expTask.setAppId(cvConfiguration.getAppId());
            learningEngineService.addLearningEngineExperimentalAnalysisTask(expTask);
          }
        }
      } catch (Exception ex) {
        log.error("Creating L2 task failed for cvConfig " + cvConfiguration.getUuid());
      }
    }
  }

  private Optional<Long> getFeedbackAnalysisMinute(LogsCVConfiguration logsCVConfiguration) {
    long lastFeedbackAnalysisMinute = logAnalysisService.getLastCVAnalysisMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);

    long lastLogMLAnalysisMinute = logAnalysisService.getLastCVAnalysisMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);

    if (lastLogMLAnalysisMinute <= 0) {
      log.info("There has been no logML analysis done for this yet. Retuning {}", logsCVConfiguration.getUuid());
      return Optional.empty();
    }
    if (lastFeedbackAnalysisMinute <= 0) {
      log.info(
          "There have been no feedback analysis records for this configuration {}. Creating the first one for minute {}",
          logsCVConfiguration.getUuid(), lastLogMLAnalysisMinute);
      return Optional.of(lastLogMLAnalysisMinute);
    }

    long minuteForFeedbackAnalysis = lastFeedbackAnalysisMinute + CRON_POLL_INTERVAL_IN_MINUTES;

    // check if there is a logAnalysis record for this minute
    boolean isLogMLAnalysisPresent = logAnalysisService.isAnalysisPresentForMinute(
        logsCVConfiguration.getUuid(), (int) minuteForFeedbackAnalysis, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);

    if (isLogMLAnalysisPresent) {
      log.info(
          "Returning the feedbackAnalysisTime for {} as {}", logsCVConfiguration.getUuid(), minuteForFeedbackAnalysis);
      return Optional.of(minuteForFeedbackAnalysis);
    }

    if (lastLogMLAnalysisMinute > lastFeedbackAnalysisMinute) {
      log.info(
          "There has been a mismatch in the log and feedback records for {}. We are self-correcting now. New feedback minute is {}",
          logsCVConfiguration.getUuid(), lastLogMLAnalysisMinute);
      return Optional.of(lastLogMLAnalysisMinute);
    }
    log.info("It is not time for a new feedback task yet for {}. Returning -1", logsCVConfiguration.getUuid());
    return Optional.empty();
  }

  @Override
  @Counted
  @Timed
  public void triggerFeedbackAnalysis(String accountId) {
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);

    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getLogAnalysisStates().contains(cvConfiguration.getStateType()))
        .forEach(cvConfiguration -> triggerFeedbackAnalysis(cvConfiguration));
  }

  @Counted
  @Timed
  private void triggerFeedbackAnalysis(CVConfiguration cvConfiguration) {
    String accountId = cvConfiguration.getAccountId();
    if (isFeatureFlagEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId)) {
      log.info("DISABLE_LOGML_NEURAL_NET feature flag is enabled for account {}, not going to create a feedback task",
          accountId);
      return;
    }

    LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;
    if (logsCVConfiguration.isWorkflowConfig()) {
      log.info("{} is a workflow configuration. Not going to trigger feedback tasks for this.",
          logsCVConfiguration.getUuid());
      return;
    }

    Optional<Long> feedbackAnalysisMinute = getFeedbackAnalysisMinute(logsCVConfiguration);
    if (!feedbackAnalysisMinute.isPresent()) {
      return;
    }
    long minuteForFeedbackAnalysis = feedbackAnalysisMinute.get();

    if (!logsCVConfiguration.is247LogsV2() && minuteForFeedbackAnalysis <= logsCVConfiguration.getBaselineEndMinute()) {
      log.info("We are skipping feedback analysis for {} for minute {} since it is in the baseline window.",
          logsCVConfiguration.getUuid(), minuteForFeedbackAnalysis);
      return;
    }
    Map<FeedbackAction, List<CVFeedbackRecord>> feedbacks =
        logAnalysisService.getUserFeedback(cvConfiguration.getUuid(), null, cvConfiguration.getAppId());
    boolean areAllFeedbacksEmpty = feedbacks.entrySet().stream().allMatch(entry -> isEmpty(entry.getValue()));

    if (areAllFeedbacksEmpty) {
      logAnalysisService.createAndUpdateFeedbackAnalysis(
          LogMLAnalysisRecordKeys.cvConfigId, cvConfiguration.getUuid(), minuteForFeedbackAnalysis);
    } else {
      if (logsCVConfiguration.is247LogsV2() && logsCVConfiguration.getBaselineStartMinute() < minuteForFeedbackAnalysis
          && minuteForFeedbackAnalysis <= logsCVConfiguration.getBaselineEndMinute()) {
        logAnalysisService.createAndUpdateFeedbackAnalysis(
            LogMLAnalysisRecordKeys.cvConfigId, cvConfiguration.getUuid(), minuteForFeedbackAnalysis);
        return;
      }
      boolean feedbackTask = createFeedbackAnalysisTask(logsCVConfiguration, minuteForFeedbackAnalysis);
      log.info("Created Feedback analysis task for {} and minute {}", logsCVConfiguration.getUuid(),
          minuteForFeedbackAnalysis);
      if (feedbackTask) {
        createExperimentalFeedbackTask(logsCVConfiguration, minuteForFeedbackAnalysis);
      }
    }
  }

  private boolean isFeatureFlagEnabled(FeatureName featureName, String accountId) {
    return verificationManagerClientHelper
        .callManagerWithRetry(verificationManagerClient.isFeatureEnabled(featureName, accountId))
        .getResource();
  }
  private void createExperimentalFeedbackTask(LogsCVConfiguration logsCVConfiguration, long logCollectionMinute) {
    try {
      String stateExecutionIdForLETask =
          "LOG_24X7_EXP_FEEDBACK_ANALYSIS_" + logsCVConfiguration.getUuid() + "_" + logCollectionMinute;
      log.info("Creating Experimental Feedback analysis task for {} and minute {}", logsCVConfiguration.getUuid(),
          logCollectionMinute);
      String feedbackUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS + GET_LOG_FEEDBACKS
          + "?cvConfigId=" + logsCVConfiguration.getUuid() + "&appId=" + logsCVConfiguration.getAppId();
      final String logMLResultUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
          + LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL + "?appId=" + logsCVConfiguration.getAppId()
          + "&cvConfigId=" + logsCVConfiguration.getUuid() + "&analysisMinute=" + logCollectionMinute;
      final String taskId = generateUuid();
      final String logAnalysisSaveUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
          + LogAnalysisResource.ANALYSIS_SAVE_EXP_24X7_ANALYSIS_RECORDS_URL
          + "?cvConfigId=" + logsCVConfiguration.getUuid() + "&appId=" + logsCVConfiguration.getAppId()
          + "&analysisMinute=" + logCollectionMinute + "&taskId=" + taskId + "&isFeedbackAnalysis=true";
      List<MLExperiments> experiments = get24x7Experiments(MLAnalysisType.FEEDBACK_ANALYSIS.name());
      for (MLExperiments experiment : experiments) {
        LearningEngineExperimentalAnalysisTask expTask =
            LearningEngineExperimentalAnalysisTask.builder()
                .accountId(logsCVConfiguration.getAccountId())
                .feedback_url(feedbackUrl)
                .logMLResultUrl(logMLResultUrl)
                .state_execution_id(stateExecutionIdForLETask)
                .query(Arrays.asList(logsCVConfiguration.getQuery()))
                .ml_analysis_type(MLAnalysisType.FEEDBACK_ANALYSIS)
                .analysis_save_url(logAnalysisSaveUrl)
                .cvConfigId(logsCVConfiguration.getUuid())
                .service_id(logsCVConfiguration.getServiceId())
                //        .shouldUseSupervisedModel(learningEngineService.shouldUseSupervisedModel(
                //          SupervisedTrainingStatusKeys.serviceId, logsCVConfiguration.getServiceId()))
                .analysis_minute(logCollectionMinute)
                .stateType(logsCVConfiguration.getStateType())
                .analysis_comparison_strategy(logsCVConfiguration.getComparisonStrategy())
                .shouldUseSupervisedModel(true)
                .experiment_name(experiment.getExperimentName())
                .is24x7Task(logsCVConfiguration.isEnabled24x7())
                .build();

        if (logsCVConfiguration.is247LogsV2()) {
          expTask.setFeature_name("247_V2");
        }

        expTask.setAppId(logsCVConfiguration.getAppId());
        expTask.setUuid(taskId);

        learningEngineService.addLearningEngineExperimentalAnalysisTask(expTask);
      }
    } catch (Exception ex) {
      log.info("Exception while creating experimental feedback task", ex);
    }
  }
  private boolean createFeedbackAnalysisTask(LogsCVConfiguration logsCVConfiguration, long logCollectionMinute) {
    String stateExecutionIdForLETask =
        "LOG_24X7_FEEDBACK_ANALYSIS_" + logsCVConfiguration.getUuid() + "_" + logCollectionMinute;
    log.info(
        "Creating Feedback analysis task for {} and minute {}", logsCVConfiguration.getUuid(), logCollectionMinute);
    String feedbackUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS + GET_LOG_FEEDBACKS
        + "?cvConfigId=" + logsCVConfiguration.getUuid() + "&appId=" + logsCVConfiguration.getAppId();
    final String taskId = generateUuid();
    final String logAnalysisSaveUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.ANALYSIS_SAVE_24X7_ANALYSIS_RECORDS_URL + "?cvConfigId=" + logsCVConfiguration.getUuid()
        + "&appId=" + logsCVConfiguration.getAppId() + "&analysisMinute=" + logCollectionMinute + "&taskId=" + taskId
        + "&isFeedbackAnalysis=true";
    final String logMLResultUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL + "?appId=" + logsCVConfiguration.getAppId()
        + "&cvConfigId=" + logsCVConfiguration.getUuid() + "&analysisMinute=" + logCollectionMinute;
    final String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
        + VerificationConstants.NOTIFY_LEARNING_FAILURE + "?taskId=" + taskId;
    LearningEngineAnalysisTask feedbackTask =
        LearningEngineAnalysisTask.builder()
            .accountId(logsCVConfiguration.getAccountId())
            .feedback_url(feedbackUrl)
            .logMLResultUrl(logMLResultUrl)
            .state_execution_id(stateExecutionIdForLETask)
            .query(Arrays.asList(logsCVConfiguration.getQuery()))
            .ml_analysis_type(MLAnalysisType.FEEDBACK_ANALYSIS)
            .analysis_save_url(logAnalysisSaveUrl)
            .analysis_failure_url(failureUrl)
            .cvConfigId(logsCVConfiguration.getUuid())
            .service_id(logsCVConfiguration.getServiceId())
            .shouldUseSupervisedModel(learningEngineService.shouldUseSupervisedModel(
                SupervisedTrainingStatusKeys.serviceId, logsCVConfiguration.getServiceId()))
            .analysis_minute(logCollectionMinute)
            .stateType(logsCVConfiguration.getStateType())
            .is24x7Task(logsCVConfiguration.isEnabled24x7())
            .priority(1)
            .build();

    if (logsCVConfiguration.is247LogsV2()) {
      feedbackTask.setFeature_name("247_V2");
    }

    feedbackTask.setAppId(logsCVConfiguration.getAppId());
    feedbackTask.setUuid(taskId);

    learningEngineService.checkAndUpdateFailedLETask(stateExecutionIdForLETask, (int) logCollectionMinute);
    if (learningEngineService.isEligibleToCreateTask(stateExecutionIdForLETask, logsCVConfiguration.getUuid(),
            logCollectionMinute, MLAnalysisType.FEEDBACK_ANALYSIS)) {
      int nextBackoffCount = learningEngineService.getNextServiceGuardBackoffCount(stateExecutionIdForLETask,
          logsCVConfiguration.getUuid(), logCollectionMinute, MLAnalysisType.FEEDBACK_ANALYSIS);
      feedbackTask.setService_guard_backoff_count(nextBackoffCount);
      return learningEngineService.addLearningEngineAnalysisTask(feedbackTask);
    }
    return false;
  }

  private Double getAlertThreshold(CVConfiguration cvConfiguration, long analysisMin) {
    Double alertThreshold = null;
    if (cvConfiguration.isAlertEnabled()
        && !(analysisMin <= TimeUnit.MILLISECONDS.toMinutes(cvConfiguration.getSnoozeEndTime())
            && analysisMin >= TimeUnit.MILLISECONDS.toMinutes(cvConfiguration.getSnoozeStartTime()))) {
      alertThreshold = cvConfiguration.getAlertThreshold();
    }
    return alertThreshold;
  }

  /**
   * Returns true if baseline window is not completely analyzed yet. Else false.
   * @param logsCVConfiguration
   * @param lastCVAnalysisMinute
   * @return
   */
  private boolean isLogAnalysisInBaselineWindow(LogsCVConfiguration logsCVConfiguration, long lastCVAnalysisMinute) {
    return logsCVConfiguration.getBaselineStartMinute() < lastCVAnalysisMinute
        && lastCVAnalysisMinute < logsCVConfiguration.getBaselineEndMinute();
  }

  private long getFirstAnalysisMinuteAfterBaseline(LogsCVConfiguration logsCVConfiguration) {
    return getFirstAnalysisMinuteInThePast2Hours(logsCVConfiguration, logsCVConfiguration.getBaselineEndMinute());
  }

  /**
   * We jump from the lastCVAnalysisMinute into the window within the past 2 hours + 30min buffer.
   * We jump this way to make sure we maintain the 15min boundaries always.
   * @param logsCVConfiguration
   * @param lastCVAnalysisMinute
   * @return
   */
  private long getFirstAnalysisMinuteInThePast2Hours(
      LogsCVConfiguration logsCVConfiguration, long lastCVAnalysisMinute) {
    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Instant.now().toEpochMilli());
    long logAnalysisMinute = lastCVAnalysisMinute;
    while (logAnalysisMinute < currentMinute - PREDECTIVE_HISTORY_MINUTES - 2 * CRON_POLL_INTERVAL_IN_MINUTES) {
      logAnalysisMinute += CRON_POLL_INTERVAL_IN_MINUTES;
    }
    log.info(
        "For {}, in getFirstAnalysisMinuteInThePast2Hours the lastCVAnalysisMin was {} but within our 2hour interval the new minute is {}",
        logsCVConfiguration.getUuid(), lastCVAnalysisMinute, logAnalysisMinute);
    return logAnalysisMinute;
  }

  /**
   * This method gives the analysisEndMin for log_ml tasks in service guard
   * The possibilities of this method are described in a comment in : https://harness.atlassian.net/browse/CV-3877
   * @param logsCVConfiguration
   * @return
   */
  private Optional<Long> getAnalysisEndMinForLogsMLAnalysis(LogsCVConfiguration logsCVConfiguration) {
    long currentMinute = TimeUnit.MILLISECONDS.toMinutes(Instant.now().toEpochMilli());
    long l2RecordMin = logAnalysisService.getLogRecordMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.H2, OrderType.ASC);
    long l2RecordMax = logAnalysisService.getLogRecordMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.H2, OrderType.DESC);
    long lastCVAnalysisMinute = logAnalysisService.getLastCVAnalysisMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    Optional<Long> analysisEndMinute;

    if (lastCVAnalysisMinute <= 0 || lastCVAnalysisMinute < logsCVConfiguration.getBaselineStartMinute()) {
      log.info(
          "This is the first ever time we're doing analysis for this configuration {}", logsCVConfiguration.getUuid());
      long analysisEnd = l2RecordMin + CRON_POLL_INTERVAL_IN_MINUTES - 1; // since both are inclusive, we subtract one.
      analysisEndMinute = Optional.of(analysisEnd);
    } else {
      log.info("There has been a previous analysis for {} with lastCVAnalysisMinute {}", logsCVConfiguration.getUuid(),
          lastCVAnalysisMinute);
      /*
       * Four possibilities here are:
       *   It is within baseline window
       *   It is doing normal analysis
       *   It is doing the first analysis after more than 2 hours.
       *   The last analysis was for baselineEndMinute
       */

      if (isLogAnalysisInBaselineWindow(logsCVConfiguration, lastCVAnalysisMinute)) {
        log.info("{} is still in baseline window", logsCVConfiguration.getUuid());
        long analysisEnd = lastCVAnalysisMinute + CRON_POLL_INTERVAL_IN_MINUTES;
        analysisEndMinute = Optional.of(analysisEnd);
      } else {
        if (isBeforeTwoHours(lastCVAnalysisMinute, true)) {
          log.info("The last analysis for {} was more than 2 hours ago. We are going to figure out the next minute",
              logsCVConfiguration.getUuid());
          // There are 2 possibilities here. One - The last analysisMin is the baselineEndMinute.
          // Two - It has gotten stuck.
          if (lastCVAnalysisMinute == logsCVConfiguration.getBaselineEndMinute()) {
            long analysisEnd = getFirstAnalysisMinuteAfterBaseline(logsCVConfiguration);
            analysisEndMinute = Optional.of(analysisEnd);
          } else {
            long analysisEnd = getFirstAnalysisMinuteInThePast2Hours(logsCVConfiguration, lastCVAnalysisMinute);
            analysisEndMinute = Optional.of(analysisEnd);
          }
        } else {
          log.info("The last analysis for {} was within a 2 hour window. We're going to continue",
              logsCVConfiguration.getUuid());
          long analysisEnd = lastCVAnalysisMinute + CRON_POLL_INTERVAL_IN_MINUTES;
          analysisEndMinute = Optional.of(analysisEnd);
        }
      }
    }

    if (analysisEndMinute.isPresent()
        && (analysisEndMinute.get() > currentMinute
            || analysisEndMinute.get() < logsCVConfiguration.getBaselineStartMinute())) {
      // This is in the future or way in the past, so we dont have anything to do now
      analysisEndMinute = Optional.empty();
    }

    if (analysisEndMinute.isPresent() && analysisEndMinute.get() > l2RecordMax) {
      log.info("For {} max L2 record is {}, proposed analysisMinute is {}, so we will wait.",
          logsCVConfiguration.getUuid(), l2RecordMax, analysisEndMinute.get());
      analysisEndMinute = Optional.empty();
    }
    if (analysisEndMinute.isPresent()) {
      // check to see if it's in the 15min boundary. Else, self-correct.
      long analysisEnd = getCeilingFifteenMinBoundaryTime(analysisEndMinute.get(), 0);
      analysisEndMinute = Optional.of(analysisEnd);
    }

    log.info("Returning the analysis end minute for {} as {}", logsCVConfiguration.getUuid(),
        analysisEndMinute.isPresent() ? analysisEndMinute.get() : null);
    return analysisEndMinute;
  }

  private LearningEngineAnalysisTask getLogMLAnalysisTask(
      LogsCVConfiguration logsCVConfiguration, long analysisEndMin, int nextBackoffCount) {
    String stateExecutionIdForLETask =
        (logsCVConfiguration.is247LogsV2() ? "LOG_24X7_V2_ANALYSIS_" : "LOG_24X7_ANALYSIS_")
        + logsCVConfiguration.getUuid() + "_" + analysisEndMin;
    String taskId = generateUuid();
    String controlInputUrl = null;
    String testInputUrl = null;
    boolean isBaselineRun = false;
    long startMinute = analysisEndMin - CRON_POLL_INTERVAL_IN_MINUTES + 1;
    long lastCVAnalysisMinute = logAnalysisService.getLastCVAnalysisMinute(
        logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    // this is the baseline prep case
    if (startMinute < logsCVConfiguration.getBaselineStartMinute()
        || (startMinute >= logsCVConfiguration.getBaselineStartMinute()
            && startMinute < logsCVConfiguration.getBaselineEndMinute())) {
      URIBuilder controlInputBuilder = new URIBuilder();
      controlInputBuilder.setPath(
          "/verification/" + LogAnalysisResource.LOG_ANALYSIS + LogAnalysisResource.ANALYSIS_GET_24X7_ALL_LOGS_URL);
      controlInputBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
      controlInputBuilder.addParameter("appId", logsCVConfiguration.getAppId());
      controlInputBuilder.addParameter("startMinute", String.valueOf(startMinute));
      controlInputBuilder.addParameter("endMinute", String.valueOf(analysisEndMin));
      controlInputBuilder.addParameter("clusterLevel", ClusterLevel.L2.name());

      controlInputUrl = getUriString(controlInputBuilder);
      isBaselineRun = true;
    } else {
      URIBuilder testInputBuilder = new URIBuilder();
      testInputBuilder.setPath(
          "/verification/" + LogAnalysisResource.LOG_ANALYSIS + LogAnalysisResource.ANALYSIS_GET_24X7_ALL_LOGS_URL);
      testInputBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
      testInputBuilder.addParameter("appId", logsCVConfiguration.getAppId());
      testInputBuilder.addParameter("startMinute", String.valueOf(startMinute));
      testInputBuilder.addParameter("endMinute", String.valueOf(analysisEndMin));
      testInputBuilder.addParameter("clusterLevel", ClusterLevel.L2.name());

      testInputUrl = getUriString(testInputBuilder);
    }
    URIBuilder saveUrlBuilder = new URIBuilder();
    saveUrlBuilder.setPath("/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.ANALYSIS_SAVE_24X7_ANALYSIS_RECORDS_URL);
    saveUrlBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
    saveUrlBuilder.addParameter("appId", logsCVConfiguration.getAppId());
    saveUrlBuilder.addParameter("analysisMinute", String.valueOf(analysisEndMin));
    saveUrlBuilder.addParameter("taskId", taskId);
    saveUrlBuilder.addParameter("comparisonStrategy", logsCVConfiguration.getComparisonStrategy().name());

    String logAnalysisSaveUrl = getUriString(saveUrlBuilder);

    URIBuilder getUrlBuilder = new URIBuilder();
    getUrlBuilder.setPath("/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL);
    getUrlBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
    getUrlBuilder.addParameter("appId", logsCVConfiguration.getAppId());
    if (logsCVConfiguration.is247LogsV2()) {
      getUrlBuilder.addParameter("analysisMinute", String.valueOf(lastCVAnalysisMinute));
    } else {
      getUrlBuilder.addParameter("analysisMinute", String.valueOf(logsCVConfiguration.getBaselineEndMinute()));
    }
    getUrlBuilder.addParameter("compressed", Boolean.FALSE.toString());

    final String logAnalysisGetUrl = getUriString(getUrlBuilder);

    URIBuilder failUrlBuilder = new URIBuilder();
    failUrlBuilder.setPath(
        "/verification/" + LearningEngineService.RESOURCE_URL + VerificationConstants.NOTIFY_LEARNING_FAILURE);
    failUrlBuilder.addParameter("taskId", taskId);
    String failureUrl = getUriString(failUrlBuilder);

    LearningEngineAnalysisTask analysisTask =
        LearningEngineAnalysisTask.builder()
            .accountId(logsCVConfiguration.getAccountId())
            .state_execution_id(stateExecutionIdForLETask)
            .service_id(logsCVConfiguration.getServiceId())
            .query(Lists.newArrayList(logsCVConfiguration.getQuery()))
            .sim_threshold(0.9)
            .analysis_minute(analysisEndMin)
            .analysis_save_url(logAnalysisSaveUrl)
            .log_analysis_get_url(logAnalysisGetUrl)
            .analysis_failure_url(failureUrl)
            .service_guard_backoff_count(nextBackoffCount)
            .ml_analysis_type(MLAnalysisType.LOG_ML)
            .test_input_url(testInputUrl)
            .control_input_url(controlInputUrl)
            .test_nodes(Sets.newHashSet(DUMMY_HOST_NAME))
            .feature_name("NEURAL_NET")
            .is24x7Task(true)
            .priority(1)
            .stateType(logsCVConfiguration.getStateType())
            .cvConfigId(logsCVConfiguration.getUuid())
            .analysis_comparison_strategy(logsCVConfiguration.getComparisonStrategy())
            .alertThreshold(getAlertThreshold(logsCVConfiguration, analysisEndMin))
            .build();

    if (logsCVConfiguration.is247LogsV2()) {
      analysisTask.setFeature_name("247_V2");
    }
    analysisTask.setAppId(logsCVConfiguration.getAppId());
    analysisTask.setUuid(taskId);
    if (isBaselineRun) {
      analysisTask.setValidUntil(Date.from(OffsetDateTime.now().plusMonths(6).toInstant()));
    }
    if (logsCVConfiguration.getComparisonStrategy() == PREDICTIVE) {
      final String lastLogAnalysisGetUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
          + LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL + "?appId=" + logsCVConfiguration.getAppId()
          + "&cvConfigId=" + logsCVConfiguration.getUuid() + "&analysisMinute=" + analysisEndMin;
      analysisTask.setPrevious_test_analysis_url(lastLogAnalysisGetUrl);
    }

    return analysisTask;
  }

  @Override
  @Counted
  @Timed
  public void triggerLogDataAnalysis(String accountId) {
    // List all the CV configurations for a given account
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);

    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period
    cvConfigurations.stream()
        .filter(cvConfiguration
            -> cvConfiguration.isEnabled24x7() && getLogAnalysisStates().contains(cvConfiguration.getStateType()))

        .forEach(cvConfiguration -> triggerLogDataAnalysis(cvConfiguration));
  }

  @Counted
  @Timed
  private void triggerLogDataAnalysis(CVConfiguration cvConfiguration) {
    // for each 24x7 enabled configurations do following
    // Get last CV Analysis minute on given configuration
    // Add new learning task for next ANALYSIS_PERIOD_MINUTES period
    String accountId = cvConfiguration.getAccountId();
    log.info("triggering logs Data Analysis for account {} and cvConfigId {}", accountId, cvConfiguration.getUuid());
    LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;

    try (VerificationLogContext ignored =
             new VerificationLogContext(logsCVConfiguration.getAccountId(), logsCVConfiguration.getUuid(), null,
                 logsCVConfiguration.getStateType().getDelegateStateType(), OVERRIDE_ERROR)) {
      try {
        if (logsCVConfiguration.isWorkflowConfig()) {
          AnalysisContext context = wingsPersistence.get(AnalysisContext.class, logsCVConfiguration.getContextId());
          long analysisLastMin = logAnalysisService.getLogRecordMinute(
              logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), ClusterLevel.HF, OrderType.DESC);
          if (analysisLastMin >= logsCVConfiguration.getBaselineEndMinute() + context.getTimeDuration()) {
            log.info("Notifying state id: {} , corr id: {}", context.getStateExecutionId(), context.getCorrelationId());
            sendStateNotification(context, false, "", (int) analysisLastMin);
            log.info("Disabled 24x7 for CV Configuration with id {}", logsCVConfiguration.getUuid());
            wingsPersistence.updateField(
                LogsCVConfiguration.class, logsCVConfiguration.getUuid(), "enabled24x7", false);
            return;
          }
        }

        Optional<Long> analysisEnd = getAnalysisEndMinForLogsMLAnalysis(logsCVConfiguration);
        if (!analysisEnd.isPresent()) {
          log.info("For {}, we dont have enough to do log_ml analysis yet", logsCVConfiguration.getUuid());
          return;
        }
        long analysisMin = analysisEnd.get();
        long startMinute = analysisMin - CRON_POLL_INTERVAL_IN_MINUTES + 1;

        if (logsCVConfiguration.isWorkflowConfig()) {
          // TODO: This should be removed. We do not use predictive analysis for workflow anymore.
          long lastCVAnalysisMinute = logAnalysisService.getLastCVAnalysisMinute(
              logsCVConfiguration.getAppId(), logsCVConfiguration.getUuid(), LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);

          AnalysisContext context = wingsPersistence.get(AnalysisContext.class, logsCVConfiguration.getContextId());
          if (lastCVAnalysisMinute >= logsCVConfiguration.getBaselineEndMinute()) {
            analysisMin = lastCVAnalysisMinute + 1;
          }

          if (analysisMin > logsCVConfiguration.getBaselineEndMinute() + context.getTimeDuration()) {
            log.info("Notifying state id: {} , corr id: {}", context.getStateExecutionId(), context.getCorrelationId());
            sendStateNotification(context, false, "", (int) analysisMin);
            log.info("Disabled 24x7 for CV Configuration with id {}", logsCVConfiguration.getUuid());
            wingsPersistence.updateField(
                LogsCVConfiguration.class, logsCVConfiguration.getUuid(), "enabled24x7", false);
          }
        }

        for (long l2Min = startMinute, i = 0; l2Min <= analysisMin; l2Min++, i++) {
          Set<String> hosts =
              logAnalysisService.getHostsForMinute(cvConfiguration.getAppId(), LogDataRecordKeys.cvConfigId,
                  cvConfiguration.getUuid(), l2Min, ClusterLevel.L1, ClusterLevel.H1, ClusterLevel.L0, ClusterLevel.H0);

          if (isNotEmpty(hosts)) {
            log.info(
                "For CV config {} there is still lustering pending for {} for minute {}. Skipping Log Data Analysis",
                cvConfiguration.getUuid(), hosts, l2Min);
            return;
          }
        }

        log.info("for {} for minute from {} to {} everything is in place, proceeding for analysis",
            logsCVConfiguration.getUuid(), startMinute, analysisMin);
        String stateExecutionIdForLETask = "LOG_24X7_ANALYSIS_" + logsCVConfiguration.getUuid() + "_" + analysisMin;
        learningEngineService.checkAndUpdateFailedLETask(stateExecutionIdForLETask, (int) analysisMin);
        int nextBackoffCount = learningEngineService.getNextServiceGuardBackoffCount(
            stateExecutionIdForLETask, cvConfiguration.getUuid(), analysisMin, MLAnalysisType.LOG_ML);

        learningEngineService.addLearningEngineAnalysisTask(
            getLogMLAnalysisTask(logsCVConfiguration, analysisMin, nextBackoffCount));

        log.info("Queuing analysis task for state {} config {} with analysisMin {}", logsCVConfiguration.getStateType(),
            logsCVConfiguration.getUuid(), analysisMin);

        List<MLExperiments> experiments = get24x7Experiments(MLAnalysisType.LOG_ML.name());
        for (MLExperiments experiment : experiments) {
          LearningEngineExperimentalAnalysisTask expTask = getExperimentalLogTask(logsCVConfiguration,
              "LOG_24X7_ANALYSIS_" + logsCVConfiguration.getUuid() + "_" + analysisMin + generateUuid(), analysisMin,
              experiment);
          learningEngineService.addLearningEngineExperimentalAnalysisTask(expTask);
        }

      } catch (Exception ex) {
        try {
          if (cvConfiguration.isWorkflowConfig()) {
            AnalysisContext context = wingsPersistence.get(AnalysisContext.class, logsCVConfiguration.getContextId());
            log.error("Verification L2 -> Log_ML analysis failed", ex);
            final VerificationStateAnalysisExecutionData executionData =
                VerificationStateAnalysisExecutionData.builder().build();
            executionData.setStatus(ExecutionStatus.ERROR);
            executionData.setErrorMsg(ex.getMessage());
            log.info("Notifying state id: {} , corr id: {}", context.getStateExecutionId(), context.getCorrelationId());
            verificationManagerClientHelper.notifyManagerForVerificationAnalysis(
                context, VerificationDataAnalysisResponse.builder().stateExecutionData(executionData).build());
            wingsPersistence.updateField(CVConfiguration.class, cvConfiguration.getUuid(), "enabled24x7", false);
          }
        } catch (Exception e) {
          log.error("Verification cluster manager cleanup failed", e);
        }
      }
    }
  }

  private LearningEngineExperimentalAnalysisTask getExperimentalLogTask(
      LogsCVConfiguration logsCVConfiguration, String stateExecutionId, long analysisEndMin, MLExperiments experiment) {
    String taskId = generateUuid();
    String controlInputUrl = null;
    String testInputUrl = null;
    long startMinute = analysisEndMin - CRON_POLL_INTERVAL_IN_MINUTES + 1;
    // this is the baseline prep case
    if (startMinute < logsCVConfiguration.getBaselineStartMinute()
        || (startMinute >= logsCVConfiguration.getBaselineStartMinute()
            && startMinute < logsCVConfiguration.getBaselineEndMinute())) {
      URIBuilder controlInputBuilder = new URIBuilder();
      controlInputBuilder.setPath(
          "/verification/" + LogAnalysisResource.LOG_ANALYSIS + LogAnalysisResource.ANALYSIS_GET_24X7_ALL_LOGS_URL);
      controlInputBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
      controlInputBuilder.addParameter("appId", logsCVConfiguration.getAppId());
      controlInputBuilder.addParameter("startMinute", String.valueOf(startMinute));
      controlInputBuilder.addParameter("endMinute", String.valueOf(analysisEndMin));
      controlInputBuilder.addParameter("clusterLevel", ClusterLevel.L2.name());

      controlInputUrl = getUriString(controlInputBuilder);

    } else {
      URIBuilder testInputBuilder = new URIBuilder();
      testInputBuilder.setPath(
          "/verification/" + LogAnalysisResource.LOG_ANALYSIS + LogAnalysisResource.ANALYSIS_GET_24X7_ALL_LOGS_URL);
      testInputBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
      testInputBuilder.addParameter("appId", logsCVConfiguration.getAppId());
      testInputBuilder.addParameter("startMinute", String.valueOf(startMinute));
      testInputBuilder.addParameter("endMinute", String.valueOf(analysisEndMin));
      testInputBuilder.addParameter("clusterLevel", ClusterLevel.L2.name());

      testInputUrl = getUriString(testInputBuilder);
    }

    URIBuilder getUrlBuilder = new URIBuilder();
    getUrlBuilder.setPath("/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL);
    getUrlBuilder.addParameter("cvConfigId", logsCVConfiguration.getUuid());
    getUrlBuilder.addParameter("appId", logsCVConfiguration.getAppId());
    getUrlBuilder.addParameter("analysisMinute", String.valueOf(logsCVConfiguration.getBaselineEndMinute()));
    getUrlBuilder.addParameter("compressed", Boolean.FALSE.toString());
    final String logAnalysisGetUrl = getUriString(getUrlBuilder);

    LearningEngineExperimentalAnalysisTask expTask =
        LearningEngineExperimentalAnalysisTask.builder()
            .accountId(logsCVConfiguration.getAccountId())
            .state_execution_id(stateExecutionId)
            .service_id(logsCVConfiguration.getServiceId())
            .query(Lists.newArrayList(logsCVConfiguration.getQuery()))
            .sim_threshold(0.9)
            .analysis_minute(analysisEndMin)
            .analysis_save_url(getSaveUrlForExperimentalTask(taskId))
            .log_analysis_get_url(logAnalysisGetUrl)
            .ml_analysis_type(MLAnalysisType.LOG_ML)
            .test_input_url(isEmpty(testInputUrl) ? null : testInputUrl + "&" + IS_EXPERIMENTAL + "=true")
            .control_input_url(isEmpty(controlInputUrl) ? null : controlInputUrl + "&" + IS_EXPERIMENTAL + "=true")
            .test_nodes(Sets.newHashSet(DUMMY_HOST_NAME))
            .feature_name("NEURAL_NET")
            .is24x7Task(true)
            .stateType(logsCVConfiguration.getStateType())
            .tolerance(logsCVConfiguration.getAnalysisTolerance().tolerance())
            .cvConfigId(logsCVConfiguration.getUuid())
            .analysis_comparison_strategy(logsCVConfiguration.getComparisonStrategy())
            .experiment_name(experiment.getExperimentName())
            .alertThreshold(getAlertThreshold(logsCVConfiguration, analysisEndMin))
            .build();
    expTask.setAppId(logsCVConfiguration.getAppId());
    expTask.setUuid(taskId);
    return expTask;
  }
  @Override
  public void cleanupStuckLocks() {
    DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, "quartz_verification_locks");
    DBCursor lockDataRecords = collection.find();

    log.info("will go through " + lockDataRecords.size() + " records");

    List<ObjectId> toBeDeleted = new ArrayList<>();
    while (lockDataRecords.hasNext()) {
      DBObject next = lockDataRecords.next();

      Date time = (Date) next.get("time");
      long lockTime = time.getTime();
      if (lockTime < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)) {
        toBeDeleted.add((ObjectId) next.get(ID_KEY));
      }
    }
    if (isNotEmpty(toBeDeleted)) {
      log.info("deleting locks {}", toBeDeleted);
      collection.remove(new BasicDBObject(ID_KEY, new BasicDBObject("$in", toBeDeleted.toArray())));
    }
  }

  private void sendStateNotification(AnalysisContext context, boolean error, String errorMsg, int logAnalysisMinute) {
    if (learningEngineService.isStateValid(context.getAppId(), context.getStateExecutionId())) {
      final ExecutionStatus status = error ? ExecutionStatus.ERROR : ExecutionStatus.SUCCESS;

      VerificationStateAnalysisExecutionData logAnalysisExecutionData =
          VerificationStateAnalysisExecutionData.builder()
              .stateExecutionInstanceId(context.getStateExecutionId())
              .serverConfigId(context.getAnalysisServerConfigId())
              .query(context.getQuery())
              .canaryNewHostNames(context.getTestNodes().keySet())
              .lastExecutionNodes(
                  context.getControlNodes() == null ? Collections.emptySet() : context.getControlNodes().keySet())
              .correlationId(context.getCorrelationId())
              .analysisMinute(logAnalysisMinute)
              .build();

      logAnalysisExecutionData.setStatus(status);

      if (error) {
        logAnalysisExecutionData.setErrorMsg(errorMsg);
      }

      final VerificationDataAnalysisResponse response =
          VerificationDataAnalysisResponse.builder().stateExecutionData(logAnalysisExecutionData).build();
      response.setExecutionStatus(status);
      log.info("Notifying state id: {} , corr id: {}", context.getStateExecutionId(), context.getCorrelationId());
      verificationManagerClientHelper.notifyManagerForVerificationAnalysis(context, response);
    }
  }

  @Override
  public void triggerTimeSeriesAlertIfNecessary(String cvConfigId, double riskScore, long analysisMinute) {
    executorService.submit(() -> triggerTimeSeriesAlert(cvConfigId, riskScore, analysisMinute));
  }

  private void triggerTimeSeriesAlert(String cvConfigId, double riskScore, long analysisMinute) {
    if (isEmpty(cvConfigId)) {
      return;
    }
    final CVConfiguration cvConfiguration = wingsPersistence.get(CVConfiguration.class, cvConfigId);
    Preconditions.checkNotNull(cvConfiguration, "no config found with id " + cvConfigId);

    if (!shouldThrowAlert(cvConfiguration)) {
      return;
    }
    if (riskScore <= cvConfiguration.getAlertThreshold()) {
      log.info("for {} the risk {} is lower than the threshold {}. All open alerts will be closed.", cvConfigId,
          riskScore, cvConfiguration.getAlertThreshold());

      verificationManagerClientHelper.callManagerWithRetry(verificationManagerClient.closeCVAlert(cvConfigId,
          ContinuousVerificationAlertData.builder()
              .riskScore(riskScore)
              .mlAnalysisType(MLAnalysisType.TIME_SERIES)
              .alertStatus(AlertStatus.Closed)
              .analysisStartTime(TimeUnit.MINUTES.toMillis(analysisMinute - CRON_POLL_INTERVAL_IN_MINUTES) + 1)
              .analysisEndTime(TimeUnit.MINUTES.toMillis(analysisMinute))
              .build()));
      return;
    }

    // check to see if num of occurrences match.
    if (cvConfiguration.getNumOfOccurrencesForAlert() > 1) {
      int earliestTimeToCheck = (int) analysisMinute
          - (cvConfiguration.getNumOfOccurrencesForAlert() - 1) * SERVICE_GUARD_ANALYSIS_WINDOW_MINS;
      int numOfBreaches = timeSeriesAnalysisService.getNumberOfAnalysisAboveThresholdSince(
          earliestTimeToCheck, cvConfigId, cvConfiguration.getAlertThreshold());
      if (numOfBreaches < cvConfiguration.getNumOfOccurrencesForAlert()) {
        log.info("For {}, number of breaches of alert threshold is {} but min number in config is {} ", cvConfigId,
            numOfBreaches, cvConfiguration.getNumOfOccurrencesForAlert());
        return;
      }
    }

    log.info("triggering alert for {} with risk score {}", cvConfigId, riskScore);
    verificationManagerClientHelper.callManagerWithRetry(verificationManagerClient.triggerCVAlert(cvConfigId,
        ContinuousVerificationAlertData.builder()
            .riskScore(riskScore)
            .mlAnalysisType(MLAnalysisType.TIME_SERIES)
            .alertStatus(AlertStatus.Open)
            .analysisStartTime(TimeUnit.MINUTES.toMillis(analysisMinute - CRON_POLL_INTERVAL_IN_MINUTES) + 1)
            .analysisEndTime(TimeUnit.MINUTES.toMillis(analysisMinute))
            .build()));
  }

  @Override
  public void triggerLogAnalysisAlertIfNecessary(
      String cvConfigId, LogMLAnalysisRecord mlAnalysisResponse, int analysisMinute) {
    if (isEmpty(cvConfigId)) {
      return;
    }
    if (isEmpty(mlAnalysisResponse.getUnknown_clusters())) {
      log.info("No unknown clusters for {} for min {}. No alerts will be triggered");
      return;
    }

    final CVConfiguration cvConfiguration = wingsPersistence.get(CVConfiguration.class, cvConfigId);
    Preconditions.checkNotNull(cvConfiguration, "no config found with id " + cvConfigId);

    if (!shouldThrowAlert(cvConfiguration)) {
      return;
    }

    log.info("triggering alerts for {} with unknown clusters {}", cvConfigId,
        mlAnalysisResponse.getUnknown_clusters().size());

    LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;

    if (logsCVConfiguration.is247LogsV2()) {
      Map<Integer, LogAnalysisResult> logAnalysisResult = mlAnalysisResponse.getLog_analysis_result();

      mlAnalysisResponse.getUnknown_clusters().forEach((clusterLabel, analysisClusterMap) -> {
        if (isNotEmpty(analysisClusterMap)) {
          final SplunkAnalysisCluster splunkAnalysisCluster =
              analysisClusterMap.entrySet().iterator().next().getValue();
          if (splunkAnalysisCluster.getPriority() == null
              || splunkAnalysisCluster.getPriority().getScore() >= logsCVConfiguration.getAlertThreshold()) {
            verificationManagerClientHelper.callManagerWithRetry(verificationManagerClient.triggerCVAlertWithTtl(
                cvConfigId, OffsetDateTime.now().plusHours(2).toInstant().toEpochMilli(),
                ContinuousVerificationAlertData.builder()
                    .mlAnalysisType(MLAnalysisType.LOG_ML)
                    .logAnomaly(splunkAnalysisCluster.getText())
                    .tag(logAnalysisResult.get(splunkAnalysisCluster.getCluster_label()).getTag())
                    .analysisStartTime(TimeUnit.MINUTES.toMillis(analysisMinute - CRON_POLL_INTERVAL_IN_MINUTES) + 1)
                    .analysisEndTime(TimeUnit.MINUTES.toMillis(analysisMinute))
                    .riskScore(1.0)
                    .build()));
          }
        }
      });
    } else {
      mlAnalysisResponse.getUnknown_clusters().forEach((clusterLabel, analysisClusterMap) -> {
        if (isNotEmpty(analysisClusterMap)) {
          final SplunkAnalysisCluster splunkAnalysisCluster =
              analysisClusterMap.entrySet().iterator().next().getValue();
          if (splunkAnalysisCluster.getPriority() == null
              || splunkAnalysisCluster.getPriority().getScore() >= logsCVConfiguration.getAlertThreshold()) {
            verificationManagerClientHelper.callManagerWithRetry(verificationManagerClient.triggerCVAlertWithTtl(
                cvConfigId, OffsetDateTime.now().plusHours(2).toInstant().toEpochMilli(),
                ContinuousVerificationAlertData.builder()
                    .mlAnalysisType(MLAnalysisType.LOG_ML)
                    .logAnomaly(splunkAnalysisCluster.getText())
                    .hosts(analysisClusterMap.keySet())
                    .analysisStartTime(TimeUnit.MINUTES.toMillis(analysisMinute - CRON_POLL_INTERVAL_IN_MINUTES) + 1)
                    .analysisEndTime(TimeUnit.MINUTES.toMillis(analysisMinute))
                    .riskScore(1.0)
                    .build()));
          }
        }
      });
    }
  }

  @Override
  public void processNextCVTasks(String accountId) {
    while (true) {
      Optional<CVTask> cvTask = cvTaskService.getNextTask(accountId);
      if (cvTask.isPresent()) {
        verificationManagerClientHelper.callManagerWithRetry(
            verificationManagerClient.collectCVData(cvTask.get().getUuid(), cvTask.get().getDataCollectionInfo()));
      } else {
        break;
      }
    }
  }

  @Override
  public void expireLongRunningCVTasks(String accountId) {
    cvTaskService.expireLongRunningTasks(accountId);
  }

  @Override
  public void retryCVTasks(String accountId) {
    cvTaskService.retryTasks(accountId);
  }

  private boolean shouldThrowAlert(CVConfiguration cvConfiguration) {
    if (!cvConfiguration.isAlertEnabled()) {
      log.info("for {} the alert is not enabled. Returning", cvConfiguration.getUuid());
      return false;
    }

    final long currentTime = System.currentTimeMillis();
    if (cvConfiguration.getSnoozeStartTime() > 0 && cvConfiguration.getSnoozeEndTime() > 0
        && currentTime >= cvConfiguration.getSnoozeStartTime() && currentTime <= cvConfiguration.getSnoozeEndTime()) {
      log.info("for {} the current time is in the range of snooze time {} to {}. No alerts will be triggered.",
          cvConfiguration.getUuid(), cvConfiguration.getSnoozeStartTime(), cvConfiguration.getSnoozeEndTime());
      return false;
    }

    return true;
  }
}
