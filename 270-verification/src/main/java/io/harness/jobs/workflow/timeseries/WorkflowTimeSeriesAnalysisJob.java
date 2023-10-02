/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs.workflow.timeseries;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.PREDECTIVE_HISTORY_MINUTES;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SortOrder.OrderType;
import io.harness.exception.ExceptionUtils;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.resources.intfc.ExperimentalMetricAnalysisResource;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.TimeSeriesAnalysisService;

import software.wings.common.VerificationConstants;
import software.wings.delegatetasks.NewRelicDataCollectionTask;
import software.wings.metrics.MetricType;
import software.wings.metrics.RiskLevel;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.dynatrace.DynaTraceTimeSeries;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.MLExperiments;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisValue;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

/**
 * Cron to schedule time series processing for workflow verification.
 */
@DisallowConcurrentExecution
@Slf4j
public class WorkflowTimeSeriesAnalysisJob implements Handler<AnalysisContext> {
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private LearningEngineService learningEngineService;

  @Inject private VerificationManagerClient verificationManagerClient;
  @Inject private VerificationManagerClientHelper managerClientHelper;
  @Inject private CVActivityLogService cvActivityLogService;

  @Override
  public void handle(AnalysisContext analysisContext) {
    if (ExecutionStatus.QUEUED == analysisContext.getExecutionStatus()) {
      learningEngineService.markJobStatus(analysisContext, ExecutionStatus.RUNNING);
    }

    new WorkflowTimeSeriesAnalysisJob
        .MetricAnalysisGenerator(timeSeriesAnalysisService, learningEngineService, managerClientHelper, analysisContext,
            Optional.empty(), cvActivityLogService)
        .run();
  }

  public static class MetricAnalysisGenerator implements Runnable {
    public static final int COMPARATIVE_ANALYSIS_DURATION = 30;
    private final Optional<JobExecutionContext> jobExecutionContext;
    private final Map<String, String> testNodes;
    private final Map<String, String> controlNodes;
    private final TimeSeriesAnalysisService analysisService;
    private final LearningEngineService learningEngineService;
    private VerificationManagerClientHelper managerClientHelper;
    private final int analysisDuration;
    private AnalysisContext context;
    private CVActivityLogService cvActivityLogService;

    public MetricAnalysisGenerator(TimeSeriesAnalysisService service, LearningEngineService learningEngineService,
        VerificationManagerClientHelper managerClientHelper, AnalysisContext context,
        Optional<JobExecutionContext> jobExecutionContext, CVActivityLogService cvActivityLogService) {
      this.analysisService = service;
      this.learningEngineService = learningEngineService;
      this.managerClientHelper = managerClientHelper;
      this.context = context;
      this.jobExecutionContext = jobExecutionContext;
      this.testNodes = context.getTestNodes();
      this.controlNodes = context.getControlNodes();
      this.cvActivityLogService = cvActivityLogService;

      if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
        this.testNodes.keySet().forEach(testNode -> controlNodes.remove(testNode));
      }
      this.analysisDuration = context.getTimeDuration() - 1;
    }

    private Map<String, MetricType> getMetricTypeMap(Map<String, TimeSeriesMetricDefinition> stateValuesToAnalyze) {
      Map<String, MetricType> stateValuesToThresholds = new HashMap<>();
      for (Map.Entry<String, TimeSeriesMetricDefinition> entry : stateValuesToAnalyze.entrySet()) {
        String metricName = entry.getValue().getMetricName();
        stateValuesToThresholds.put(metricName, entry.getValue().getMetricType());
      }

      return stateValuesToThresholds;
    }

    private NewRelicMetricAnalysisRecord analyzeLocal(int analysisMinute, String groupName) {
      log.info("running " + context.getStateType().name() + " for minute {}", analysisMinute);
      int analysisStartMin =
          analysisMinute > COMPARATIVE_ANALYSIS_DURATION ? analysisMinute - COMPARATIVE_ANALYSIS_DURATION : 0;
      final String lastSuccessfulWorkflowExecutionIdWithData =
          analysisService.getLastSuccessfulWorkflowExecutionIdWithData(
              context.getStateType(), context.getAppId(), context.getWorkflowId(), context.getServiceId());
      final Set<NewRelicMetricDataRecord> controlRecords =
          context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS
          ? analysisService.getPreviousSuccessfulRecords(context.getAppId(), lastSuccessfulWorkflowExecutionIdWithData,
              groupName, analysisMinute, analysisStartMin, context.getAccountId())
          : analysisService.getRecords(context.getAppId(), context.getStateExecutionId(), groupName,
              getNodesForGroup(groupName, context.getControlNodes()), analysisMinute, analysisStartMin,
              context.getAccountId());

      final Set<NewRelicMetricDataRecord> testRecords = analysisService.getRecords(context.getAppId(),
          context.getStateExecutionId(), groupName, getNodesForGroup(groupName, context.getTestNodes()), analysisMinute,
          analysisStartMin, context.getAccountId());

      String message = "";
      if (isEmpty(testRecords)) {
        message = "No test data found. Please check load. Skipping analysis for minute " + analysisMinute;
      }

      Map<String, Set<NewRelicMetricDataRecord>> controlRecordsByMetric = splitMetricsByName(controlRecords);
      Map<String, Set<NewRelicMetricDataRecord>> testRecordsByMetric = splitMetricsByName(testRecords);

      NewRelicMetricAnalysisRecord analysisRecord = NewRelicMetricAnalysisRecord.builder()
                                                        .accountId(context.getAccountId())
                                                        .appId(context.getAppId())
                                                        .stateExecutionId(context.getStateExecutionId())
                                                        .workflowExecutionId(context.getWorkflowExecutionId())
                                                        .riskLevel(RiskLevel.LOW)
                                                        .groupName(groupName)
                                                        .message(message)
                                                        .metricAnalyses(new ArrayList<>())
                                                        .build();

      Map<String, MetricType> stateValuesToAnalyze;
      switch (context.getStateType()) {
        case NEW_RELIC:
          stateValuesToAnalyze = getMetricTypeMap(NewRelicMetricValueDefinition.NEW_RELIC_VALUES_TO_ANALYZE);
          break;
        case APP_DYNAMICS:
          stateValuesToAnalyze = getMetricTypeMap(NewRelicMetricValueDefinition.APP_DYNAMICS_VALUES_TO_ANALYZE);
          break;
        case DYNA_TRACE:
          stateValuesToAnalyze = getMetricTypeMap(DynaTraceTimeSeries.getDefinitionsToAnalyze());
          break;
        case PROMETHEUS:
        case CLOUD_WATCH:
        case DATA_DOG:
        case APM_VERIFICATION:
        case STACK_DRIVER:
        case INSTANA:
          stateValuesToAnalyze = getMetricTypeMap(analysisService.getMetricTemplates(
              context.getAppId(), context.getStateType(), context.getStateExecutionId(), null));
          break;
        default:
          throw new IllegalStateException("Invalid stateType " + context.getStateType());
      }

      for (Map.Entry<String, Set<NewRelicMetricDataRecord>> metric : testRecordsByMetric.entrySet()) {
        final String metricName = metric.getKey();
        NewRelicMetricAnalysisRecord.NewRelicMetricAnalysis metricAnalysis =
            NewRelicMetricAnalysisRecord.NewRelicMetricAnalysis.builder()
                .metricName(metricName)
                .riskLevel(RiskLevel.LOW)
                .metricValues(new ArrayList<>())
                .build();

        for (Map.Entry<String, MetricType> valuesToAnalyze : stateValuesToAnalyze.entrySet()) {
          NewRelicMetricValueDefinition metricValueDefinition = NewRelicMetricValueDefinition.builder()
                                                                    .metricName(metricName)
                                                                    .metricValueName(valuesToAnalyze.getKey())
                                                                    .metricType(valuesToAnalyze.getValue())
                                                                    .build();

          NewRelicMetricAnalysisValue metricAnalysisValue =
              metricValueDefinition.analyze(CollectionUtils.emptyIfNull(metric.getValue())
                                                .stream()
                                                .map(NewRelicMetricDataRecord::toDto)
                                                .collect(Collectors.toSet()),
                  CollectionUtils.emptyIfNull(controlRecordsByMetric.get(metricName))
                      .stream()
                      .map(NewRelicMetricDataRecord::toDto)
                      .collect(Collectors.toSet()));
          metricAnalysisValue.setHostAnalysisValues(
              metricValueDefinition.getTestHostValues(CollectionUtils.emptyIfNull(metric.getValue())
                                                          .stream()
                                                          .map(NewRelicMetricDataRecord::toDto)
                                                          .collect(Collectors.toSet())));
          metricAnalysis.addNewRelicMetricAnalysisValue(metricAnalysisValue);

          if (metricAnalysisValue.getRiskLevel().compareTo(metricAnalysis.getRiskLevel()) < 0) {
            metricAnalysis.setRiskLevel(metricAnalysisValue.getRiskLevel());
          }

          if (metricAnalysisValue.getRiskLevel().compareTo(analysisRecord.getRiskLevel()) < 0) {
            analysisRecord.setRiskLevel(metricAnalysisValue.getRiskLevel());
          }
        }
        analysisRecord.addNewRelicMetricAnalysis(metricAnalysis);
      }

      analysisRecord.setAnalysisMinute(analysisMinute);

      return analysisRecord;
    }

    private boolean timeSeriesML(int analysisMinute, String groupName, TimeSeriesMlAnalysisType mlAnalysisType,
        Integer newNodeTrafficSplitPercentage) throws IOException, TimeoutException {
      log.info("Running timeSeriesML with analysisMinute : {} groupName: {} mlAnalysisType : {}", analysisMinute,
          groupName, mlAnalysisType);
      if (learningEngineService.hasAnalysisTimedOut(
              context.getAppId(), context.getWorkflowExecutionId(), context.getStateExecutionId())) {
        learningEngineService.markStatus(
            context.getWorkflowExecutionId(), context.getStateExecutionId(), analysisMinute, ExecutionStatus.FAILED);
        throw new TimeoutException("Error running time series analysis. Finished all retries. stateExecutionId: "
            + context.getStateExecutionId() + ". Please contact the Harness support team. ");
      }
      int analysisStartMin = getAnalysisStartMinute(analysisMinute, mlAnalysisType);

      String testInputUrl = getTestInputUrl(groupName);
      String controlInputUrl = getControlInputUrl(groupName);
      String uuid = generateUuid();
      String metricAnalysisSaveUrl = getMetricAnalysisSaveUrl(
          MetricDataAnalysisService.RESOURCE_URL, "/save-analysis", uuid, groupName, analysisMinute);

      String metricTemplateUrl = "/verification/" + MetricDataAnalysisService.RESOURCE_URL
          + "/get-metric-template?accountId=" + context.getAccountId() + "&appId=" + context.getAppId()
          + "&stateType=" + context.getStateType() + "&stateExecutionId=" + context.getStateExecutionId()
          + "&serviceId=" + context.getServiceId() + "&groupName=" + groupName;
      String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
          + VerificationConstants.NOTIFY_LEARNING_FAILURE + "?taskId=" + uuid;
      metricTemplateUrl = metricTemplateUrl.replaceAll(" ", URLEncoder.encode(" ", "UTF-8"));
      LearningEngineAnalysisTask learningEngineAnalysisTask =
          LearningEngineAnalysisTask.builder()
              .accountId(context.getAccountId())
              .workflow_id(context.getWorkflowId())
              .workflow_execution_id(context.getWorkflowExecutionId())
              .state_execution_id(context.getStateExecutionId())
              .service_id(context.getServiceId())
              .analysis_start_min(analysisStartMin)
              .analysis_minute(analysisMinute)
              .smooth_window(context.getSmooth_window())
              .tolerance(context.getTolerance())
              .min_rpm(context.getMinimumRequestsPerMinute())
              .comparison_unit_window(context.getComparisonWindow())
              .parallel_processes(context.getParallelProcesses())
              .test_input_url(testInputUrl)
              .control_input_url(controlInputUrl)
              .analysis_save_url(metricAnalysisSaveUrl)
              .metric_template_url(metricTemplateUrl)
              .analysis_failure_url(failureUrl)
              .control_nodes(getNodesForGroup(groupName, controlNodes))
              .test_nodes(mlAnalysisType == TimeSeriesMlAnalysisType.PREDICTIVE
                      ? Sets.newHashSet(groupName)
                      : getNodesForGroup(groupName, testNodes))
              .analysis_start_time(
                  mlAnalysisType == TimeSeriesMlAnalysisType.PREDICTIVE ? PREDECTIVE_HISTORY_MINUTES : 0)
              .stateType(context.getStateType())
              .ml_analysis_type(MLAnalysisType.TIME_SERIES)
              .group_name(groupName)
              .time_series_ml_analysis_type(mlAnalysisType)
              .priority(0)
              .newInstanceTrafficSplitPercentage(newNodeTrafficSplitPercentage)
              .build();
      learningEngineAnalysisTask.setAppId(context.getAppId());
      learningEngineAnalysisTask.setUuid(uuid);

      if (mlAnalysisType == TimeSeriesMlAnalysisType.PREDICTIVE) {
        learningEngineAnalysisTask.setPrediction_start_time(PREDECTIVE_HISTORY_MINUTES);
      }

      log.info("Queueing for analysis {}", learningEngineAnalysisTask);
      return learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }

    private int getAnalysisStartMinute(int analysisMinute, TimeSeriesMlAnalysisType mlAnalysisType) {
      switch (mlAnalysisType) {
        case COMPARATIVE:
          return analysisMinute > COMPARATIVE_ANALYSIS_DURATION ? analysisMinute - COMPARATIVE_ANALYSIS_DURATION : 0;
        case PREDICTIVE:
          return analysisMinute > PREDECTIVE_HISTORY_MINUTES * 2 ? analysisMinute - PREDECTIVE_HISTORY_MINUTES * 2 : 0;
        default:
          throw new IllegalArgumentException("Invalid type " + mlAnalysisType);
      }
    }

    @Override
    public void run() {
      log.info("Starting analysis for " + context.getStateExecutionId());

      boolean completeCron = false;
      boolean error = false;
      String errMsg = "";

      int analysisMinute = -1;
      try {
        // Check whether workflow state is valid or not.
        if (!learningEngineService.isStateValid(context.getAppId(), context.getStateExecutionId())) {
          log.info("for {} state is no longer valid", context.getStateExecutionId());
          learningEngineService.markJobStatus(context, ExecutionStatus.SUCCESS);
          completeCron = true;
          return;
        }

        if (managerClientHelper.isFeatureFlagEnabled(FeatureName.OUTAGE_CV_DISABLE, context.getAccountId())) {
          cvActivityLogService.getLoggerByStateExecutionId(context.getAccountId(), context.getStateExecutionId())
              .info("Continuous Verification is disabled for your account. Please contact harness support.");
          completeCron = true;
        }

        // Check if we should fail fast based on the previous analysis.
        TimeSeriesMLAnalysisRecord failFastRecord =
            analysisService.getFailFastAnalysisRecord(context.getAppId(), context.getStateExecutionId());
        if (failFastRecord != null && failFastRecord.isShouldFailFast()) {
          log.info("The analysis for state {} is going to fail fast due to {}", context.getStateExecutionId(),
              "Analysis failed due to fail fast thresholds: " + failFastRecord.getFailFastErrorMsg());
          completeCron = true;
          error = true;
          errMsg = failFastRecord.getFailFastErrorMsg();
          return;
        }

        Map<String, TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo> metricGroups =
            analysisService.getMetricGroups(context.getAppId(), context.getStateExecutionId());

        for (Map.Entry<String, TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo> entry : metricGroups.entrySet()) {
          TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo timeSeriesMlAnalysisGroupInfo = entry.getValue();
          String groupName = timeSeriesMlAnalysisGroupInfo.getGroupName();
          TimeSeriesMlAnalysisType timeSeriesMlAnalysisType = timeSeriesMlAnalysisGroupInfo.getMlAnalysisType();
          final NewRelicMetricDataRecord lastHeartBeatRecord = analysisService.getHeartBeat(context.getStateType(),
              context.getStateExecutionId(), context.getWorkflowExecutionId(), context.getServiceId(), groupName,
              OrderType.DESC, context.getAccountId());

          if (lastHeartBeatRecord != null) {
            final NewRelicMetricDataRecord firstHeartBeatRecord = analysisService.getHeartBeat(context.getStateType(),
                context.getStateExecutionId(), context.getWorkflowExecutionId(), context.getServiceId(), groupName,
                OrderType.ASC, context.getAccountId());
            completeCron = timeSeriesMlAnalysisType == TimeSeriesMlAnalysisType.PREDICTIVE
                ? lastHeartBeatRecord.getDataCollectionMinute()
                    >= PREDECTIVE_HISTORY_MINUTES + context.getStartDataCollectionMinute() + analysisDuration
                : lastHeartBeatRecord.getDataCollectionMinute() - firstHeartBeatRecord.getDataCollectionMinute()
                    >= analysisDuration;

            if (completeCron) {
              log.info("time series analysis finished for {} after running for {} minutes",
                  context.getStateExecutionId(), lastHeartBeatRecord.getDataCollectionMinute());
              return;
            }
          }

          final NewRelicMetricDataRecord analysisDataRecord = analysisService.getAnalysisMinute(context.getStateType(),
              context.getAppId(), context.getStateExecutionId(), context.getWorkflowExecutionId(),
              context.getServiceId(), groupName, context.getAccountId());
          if (analysisDataRecord == null) {
            log.info("for {} Skipping time series analysis. No new data.", context.getStateExecutionId());
            continue;
          }
          analysisMinute = analysisDataRecord.getDataCollectionMinute();

          log.info("running analysis for {} for minute {}", context.getStateExecutionId(), analysisMinute);

          boolean runTimeSeriesML = true;

          boolean taskQueued = false;
          if (runTimeSeriesML) {
            switch (context.getComparisonStrategy()) {
              case COMPARE_WITH_PREVIOUS:
                if (isEmpty(context.getPrevWorkflowExecutionId())) {
                  runTimeSeriesML = false;
                  break;
                }

                int minControlMinute = analysisService.getMinControlMinuteWithData(context.getStateType(),
                    context.getAppId(), context.getServiceId(), context.getWorkflowId(),
                    context.getPrevWorkflowExecutionId(), groupName, context.getAccountId());

                if (analysisMinute < minControlMinute) {
                  log.info(
                      "For {} Baseline control minute starts at {} . But current analysis minute is {} Will run local analysis instead of ML for minute {}",
                      context.getStateExecutionId(), minControlMinute, analysisMinute, analysisMinute);
                  runTimeSeriesML = false;
                  break;
                }

                int maxControlMinute = analysisService.getMaxControlMinuteWithData(context.getStateType(),
                    context.getAppId(), context.getServiceId(), context.getWorkflowId(),
                    context.getPrevWorkflowExecutionId(), groupName, context.getAccountId());
                // this is a migration code
                // we are moving from relative minute to absolute minute for all the usecases.
                // we can get rid of this logic only after we have no relative minute baseline set. The current TTL for
                // newRelicMetricDataRecord is 6 months.
                if (getRelativeAnalysisMinute(analysisMinute, context)
                    > getMaxControlRelativeMinute(maxControlMinute, minControlMinute)) {
                  log.warn(
                      "For {} Not enough control data. analysis minute = {} , max control minute = {} analysisContext = {}",
                      context.getStateExecutionId(), analysisMinute, maxControlMinute, context);
                  // Do nothing. Don't run any analysis.
                  taskQueued = true;
                  analysisService.bumpCollectionMinuteToProcess(context.getAppId(), context.getStateExecutionId(),
                      context.getWorkflowExecutionId(), groupName, analysisMinute, context.getAccountId());
                  break;
                }
                taskQueued = timeSeriesML(
                    analysisMinute, groupName, timeSeriesMlAnalysisType, context.getNewNodesTrafficShiftPercent());
                break;
              // Note that control flows through to COMPARE_WITH_CURRENT where the ml analysis is run.
              case PREDICTIVE:
              case COMPARE_WITH_CURRENT:
                log.info("For {} running time series ml analysis for minute {} for type {}",
                    context.getStateExecutionId(), analysisMinute, context.getComparisonStrategy());
                taskQueued = timeSeriesML(
                    analysisMinute, groupName, timeSeriesMlAnalysisType, context.getNewNodesTrafficShiftPercent());
                break;
              default:
                throw new IllegalStateException("Invalid type " + context.getComparisonStrategy());
            }
          }

          if (!runTimeSeriesML) {
            log.info("running local time series analysis for {}", context.getStateExecutionId());
            NewRelicMetricAnalysisRecord analysisRecord = analyzeLocal(analysisMinute, groupName);
            analysisService.saveAnalysisRecordsIgnoringDuplicate(analysisRecord);
            analysisService.bumpCollectionMinuteToProcess(context.getAppId(), context.getStateExecutionId(),
                context.getWorkflowExecutionId(), groupName, analysisMinute, context.getAccountId());
          } else if (!taskQueued) {
            continue;
          }
          try {
            createExperimentalTask(analysisMinute, groupName, timeSeriesMlAnalysisType);
          } catch (Exception ex) {
            log.info("[Learning Engine] : Failed to create Experimental Task for {} with error {}",
                context.getStateExecutionId(), ex);
          }
        }
      } catch (RuntimeException | TimeoutException | IOException ex) {
        completeCron = true;
        error = true;
        errMsg = ExceptionUtils.getMessage(ex);
        log.warn("analysis failed for {}", context.getStateExecutionId(), ex);
      } finally {
        try {
          if (completeCron || !learningEngineService.isStateValid(context.getAppId(), context.getStateExecutionId())) {
            try {
              log.info("send notification to state manager for {} and delete cron with error : {} errorMsg : {}",
                  context.getStateExecutionId(), error, errMsg);
              sendStateNotification(context, error, errMsg, analysisMinute);
              try {
                if (jobExecutionContext.isPresent()) {
                  jobExecutionContext.get().getScheduler().deleteJob(jobExecutionContext.get().getJobDetail().getKey());
                }
              } catch (Exception e) {
                log.error("for {} Delete cron failed", context.getStateExecutionId(), e);
              }
            } catch (Exception e) {
              log.error("for {} Send notification failed for new relic analysis manager, this will be retried",
                  context.getStateExecutionId(), e);
            }
          }
        } catch (Exception ex) {
          log.error("analysis failed", ex);
        }
      }
    }

    private int getRelativeAnalysisMinute(int analysisMinute, AnalysisContext context) {
      return analysisMinute > 10000 ? (int) (context.getStartDataCollectionMinute() - analysisMinute) : analysisMinute;
    }
    private int getMaxControlRelativeMinute(int maxControlMinute, int minControlMinute) {
      return maxControlMinute > 10000 ? maxControlMinute - minControlMinute : maxControlMinute;
    }

    /**
     * Method to create Experimental Task and save it to DB
     *
     * @param analysisMinute
     * @param groupName
     * @param mlAnalysisType
     */
    private void createExperimentalTask(int analysisMinute, String groupName, TimeSeriesMlAnalysisType mlAnalysisType)
        throws UnsupportedEncodingException {
      log.info("Creating Experimental Task with analysisMinute : {} groupName: {} mlAnalysisType : {}", analysisMinute,
          groupName, mlAnalysisType);
      String uuid = generateUuid();
      String testInputUrl = getTestInputUrl(groupName);
      String controlInputUrl = getControlInputUrl(groupName);

      int analysisStartMin =
          analysisMinute > COMPARATIVE_ANALYSIS_DURATION ? analysisMinute - COMPARATIVE_ANALYSIS_DURATION : 0;

      String experimentalMetricAnalysisSaveUrl = getMetricAnalysisSaveUrl(
          ExperimentalMetricAnalysisResource.LEARNING_EXP_URL,
          ExperimentalMetricAnalysisResource.ANALYSIS_STATE_SAVE_ANALYSIS_RECORDS_URL, uuid, groupName, analysisMinute);

      if (!isEmpty(context.getPrevWorkflowExecutionId())) {
        experimentalMetricAnalysisSaveUrl += "&baseLineExecutionId=" + context.getPrevWorkflowExecutionId();
      }
      List<MLExperiments> experiments = learningEngineService.getExperiments(MLAnalysisType.TIME_SERIES);

      String metricTemplateUrl = "/verification/" + ExperimentalMetricAnalysisResource.LEARNING_EXP_URL
          + ExperimentalMetricAnalysisResource.GET_METRIC_TEMPLATE + "?accountId=" + context.getAccountId()
          + "&appId=" + context.getAppId() + "&stateType=" + context.getStateType()
          + "&stateExecutionId=" + context.getStateExecutionId() + "&groupName=" + groupName;

      metricTemplateUrl = metricTemplateUrl.replaceAll(" ", URLEncoder.encode(" ", "UTF-8"));
      LearningEngineExperimentalAnalysisTask analysisTask =
          LearningEngineExperimentalAnalysisTask.builder()
              .accountId(context.getAccountId())
              .workflow_id(context.getWorkflowId())
              .workflow_execution_id(context.getWorkflowExecutionId())
              .state_execution_id(context.getStateExecutionId())
              .service_id(context.getServiceId())
              .analysis_start_min(analysisStartMin)
              .analysis_minute(analysisMinute)
              .smooth_window(context.getSmooth_window())
              .tolerance(context.getTolerance())
              .min_rpm(context.getMinimumRequestsPerMinute())
              .comparison_unit_window(context.getComparisonWindow())
              .parallel_processes(context.getParallelProcesses())
              .test_input_url(testInputUrl)
              .control_input_url(controlInputUrl)
              .analysis_save_url(experimentalMetricAnalysisSaveUrl)
              .metric_template_url(metricTemplateUrl)
              .control_nodes(getNodesForGroup(groupName, controlNodes))
              .test_nodes(mlAnalysisType == TimeSeriesMlAnalysisType.PREDICTIVE
                      ? Sets.newHashSet(groupName)
                      : getNodesForGroup(groupName, testNodes))
              .analysis_start_time(
                  mlAnalysisType == TimeSeriesMlAnalysisType.PREDICTIVE ? PREDECTIVE_HISTORY_MINUTES : 0)
              .stateType(context.getStateType())
              .ml_analysis_type(MLAnalysisType.TIME_SERIES)
              .group_name(groupName)
              .time_series_ml_analysis_type(mlAnalysisType)
              .build();

      analysisTask.setAppId(context.getAppId());
      analysisTask.setUuid(uuid);

      for (MLExperiments experiment : experiments) {
        String saveUrl = analysisTask.getAnalysis_save_url();
        saveUrl += "&experimentName=" + experiment.getExperimentName();
        analysisTask.setAnalysis_save_url(saveUrl);
        analysisTask.setExperiment_name(experiment.getExperimentName());

        log.info("Queueing for analysis {}", analysisTask);
        learningEngineService.addLearningEngineExperimentalAnalysisTask(analysisTask);
      }
    }

    private Map<String, Set<NewRelicMetricDataRecord>> splitMetricsByName(Set<NewRelicMetricDataRecord> records) {
      final Map<String, Set<NewRelicMetricDataRecord>> rv = new HashMap<>();
      for (NewRelicMetricDataRecord record : records) {
        if (record.getName().equals(NewRelicDataCollectionTask.HARNESS_HEARTBEAT_METRIC_NAME)) {
          continue;
        }
        if (!rv.containsKey(record.getName())) {
          rv.put(record.getName(), new HashSet<>());
        }

        rv.get(record.getName()).add(record);
      }

      return rv;
    }

    private void sendStateNotification(AnalysisContext context, boolean error, String errMsg, int analysisMinute) {
      if (learningEngineService.isStateValid(context.getAppId(), context.getStateExecutionId())) {
        final ExecutionStatus status = error ? ExecutionStatus.ERROR : ExecutionStatus.SUCCESS;
        final VerificationStateAnalysisExecutionData executionData =
            VerificationStateAnalysisExecutionData.builder()
                .stateExecutionInstanceId(context.getStateExecutionId())
                .serverConfigId(context.getAnalysisServerConfigId())
                .canaryNewHostNames(context.getTestNodes().keySet())
                .lastExecutionNodes(
                    context.getControlNodes() == null ? new HashSet<>() : context.getControlNodes().keySet())
                .correlationId(context.getCorrelationId())
                .analysisMinute(analysisMinute)
                .build();
        executionData.setStatus(status);
        if (error) {
          executionData.setErrorMsg(errMsg);
        }
        final VerificationDataAnalysisResponse response =
            VerificationDataAnalysisResponse.builder().stateExecutionData(executionData).build();
        response.setExecutionStatus(status);
        log.info("Notifying state id: {} , corr id: {}", context.getStateExecutionId(), context.getCorrelationId());

        managerClientHelper.notifyManagerForVerificationAnalysis(context, response);
      }
    }

    private Set<String> getNodesForGroup(String groupName, Map<String, String> nodes) {
      Set<String> rv = new HashSet<>();
      nodes.forEach((host, group) -> {
        if (group.equals(groupName) || group.equals(DEFAULT_GROUP_NAME)) {
          rv.add(host);
        }
      });
      return rv;
    }

    private String getMetricAnalysisSaveUrl(
        String resourceUrl, String saveApiName, String uuid, String groupName, int analysisMinute) {
      String metricAnalysisSaveUrl = "/verification/" + resourceUrl + saveApiName
          + "?accountId=" + context.getAccountId() + "&applicationId=" + context.getAppId() + "&workflowExecutionId="
          + context.getWorkflowExecutionId() + "&stateExecutionId=" + context.getStateExecutionId() + "&analysisMinute="
          + analysisMinute + "&taskId=" + uuid + "&groupName=" + groupName + "&stateType=" + context.getStateType();

      if (!isEmpty(context.getPrevWorkflowExecutionId())) {
        metricAnalysisSaveUrl += "&baseLineExecutionId=" + context.getPrevWorkflowExecutionId();
      }
      return metricAnalysisSaveUrl;
    }

    private String getControlInputUrl(String groupName) throws UnsupportedEncodingException {
      String controlInputUrl = "/verification/" + MetricDataAnalysisService.RESOURCE_URL + "/get-metrics?accountId="
          + context.getAccountId() + "&appId=" + context.getAppId() + "&groupName=" + groupName + "&compareCurrent=";
      if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
        controlInputUrl = controlInputUrl + true + "&stateExecutionId=" + context.getStateExecutionId();
      } else {
        controlInputUrl = controlInputUrl + false + "&workflowExecutionId=" + context.getPrevWorkflowExecutionId();
      }

      return controlInputUrl.replaceAll(" ", URLEncoder.encode(" ", "UTF-8"));
    }

    private String getTestInputUrl(String groupName) throws UnsupportedEncodingException {
      String testInputUrl = "/verification/" + MetricDataAnalysisService.RESOURCE_URL
          + "/get-metrics?accountId=" + context.getAccountId() + "&appId=" + context.getAppId()
          + "&stateExecutionId=" + context.getStateExecutionId() + "&groupName=" + groupName + "&compareCurrent=true";
      return testInputUrl.replaceAll(" ", URLEncoder.encode(" ", "UTF-8"));
    }
  }
}
