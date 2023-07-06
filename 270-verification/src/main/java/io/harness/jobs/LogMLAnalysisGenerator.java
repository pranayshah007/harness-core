/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs;

import static io.harness.beans.FeatureName.DISABLE_LOGML_NEURAL_NET;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.common.VerificationConstants.GET_LOG_FEEDBACKS;

import io.harness.beans.ExecutionStatus;
import io.harness.exception.ExceptionUtils;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;

import software.wings.common.VerificationConstants;
import software.wings.service.impl.VerificationLogContext;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskBuilder;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask.LearningEngineExperimentalAnalysisTaskBuilder;
import software.wings.service.impl.newrelic.MLExperiments;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.analysis.LogAnalysisResource;
import software.wings.sm.StateType;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by sriram_parthasarathy on 8/23/17.
 */
@Slf4j
public class LogMLAnalysisGenerator implements Runnable {
  private final AnalysisContext context;
  private final String accountId;
  private final String applicationId;
  private final String workflowId;
  private final String serviceId;
  private final Set<String> testNodes;
  private final Set<String> controlNodes;
  private final String query;
  private final boolean createExperiment;
  private long logAnalysisMinute;
  private LogAnalysisService analysisService;
  private LearningEngineService learningEngineService;
  private VerificationManagerClient managerClient;
  private VerificationManagerClientHelper managerClientHelper;
  private MLAnalysisType analysisType;

  public LogMLAnalysisGenerator(AnalysisContext context, long logAnalysisMinute, boolean createExperiment,
      LogAnalysisService analysisService, LearningEngineService learningEngineService,
      VerificationManagerClient managerClient, VerificationManagerClientHelper managerClientHelper,
      MLAnalysisType mlAnalysisType) {
    this.context = context;
    this.analysisService = analysisService;
    this.applicationId = context.getAppId();
    this.accountId = context.getAccountId();
    this.workflowId = context.getWorkflowId();
    this.serviceId = context.getServiceId();
    this.testNodes = analysisService.getCollectedNodes(context, ClusterLevel.L2);
    this.controlNodes = context.getControlNodes().keySet();
    this.query = context.getQuery();
    this.logAnalysisMinute = logAnalysisMinute;
    this.learningEngineService = learningEngineService;
    this.createExperiment = createExperiment;
    this.managerClient = managerClient;
    this.managerClientHelper = managerClientHelper;
    this.analysisType = mlAnalysisType;
  }

  @Override
  public void run() {
    try (VerificationLogContext ignored = new VerificationLogContext(accountId, null, context.getStateExecutionId(),
             context.getStateType().getDelegateStateType(), OVERRIDE_ERROR)) {
      if (analysisType != null && analysisType == MLAnalysisType.FEEDBACK_ANALYSIS) {
        generateFeedbackAnalysis();
      } else {
        generateAnalysis();
      }
    }
    logAnalysisMinute++;
  }

  private void generateAnalysis() {
    try {
      String uuid = generateUuid();
      // TODO fix this
      if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT
          && !analysisService.isLogDataCollected(
              applicationId, context.getStateExecutionId(), query, logAnalysisMinute, context.getStateType())) {
        log.warn("No data collected for minute " + logAnalysisMinute + " for application: " + applicationId
            + " stateExecution: " + context.getStateExecutionId() + ". No ML analysis will be run this minute");
        return;
      }

      final String lastWorkflowExecutionId = context.getPrevWorkflowExecutionId();
      final boolean isBaselineCreated =
          context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT
          || !isEmpty(lastWorkflowExecutionId);

      String testInputUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
          + LogAnalysisResource.ANALYSIS_STATE_GET_LOG_URL + "?accountId=" + accountId
          + "&clusterLevel=" + ClusterLevel.L2.name() + "&workflowExecutionId=" + context.getWorkflowExecutionId()
          + "&compareCurrent=true&stateType=" + context.getStateType();

      String controlInputUrl;

      if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
        controlInputUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
            + LogAnalysisResource.ANALYSIS_STATE_GET_LOG_URL + "?accountId=" + accountId
            + "&clusterLevel=" + ClusterLevel.L2.name() + "&workflowExecutionId=" + context.getWorkflowExecutionId()
            + "&compareCurrent=true&stateType=" + context.getStateType() + "&timeDelta=0";
      } else {
        controlInputUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
            + LogAnalysisResource.ANALYSIS_STATE_GET_LOG_URL + "?accountId=" + accountId
            + "&clusterLevel=" + ClusterLevel.L2.name() + "&workflowExecutionId=" + lastWorkflowExecutionId
            + "&compareCurrent=false&stateType=" + context.getStateType() + "&timeDelta=0";
      }

      String logAnalysisSaveUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
          + LogAnalysisResource.ANALYSIS_STATE_SAVE_ANALYSIS_RECORDS_URL + "?accountId=" + accountId
          + "&applicationId=" + applicationId + "&stateExecutionId=" + context.getStateExecutionId()
          + "&workflowExecutionId=" + context.getWorkflowExecutionId() + "&logCollectionMinute=" + logAnalysisMinute
          + "&isBaselineCreated=" + isBaselineCreated + "&taskId=" + uuid + "&stateType=" + context.getStateType();

      if (!isEmpty(context.getPrevWorkflowExecutionId())) {
        logAnalysisSaveUrl += "&baseLineExecutionId=" + context.getPrevWorkflowExecutionId();
      }

      final String logAnalysisGetUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
          + LogAnalysisResource.ANALYSIS_STATE_GET_ANALYSIS_RECORDS_URL + "?accountId=" + accountId
          + "&stateType=" + context.getStateType();

      String feedback_url = "";

      boolean isOldFeedbacksEnabled =
          managerClientHelper
              .callManagerWithRetry(managerClient.isFeatureEnabled(DISABLE_LOGML_NEURAL_NET, context.getAccountId()))
              .getResource();
      if (isOldFeedbacksEnabled
          && (logAnalysisMinute == 0 || logAnalysisMinute == context.getStartDataCollectionMinute())) {
        feedback_url = "/verification/" + LogAnalysisResource.LOG_ANALYSIS + LogAnalysisResource.ANALYSIS_USER_FEEDBACK
            + "?accountId=" + accountId + "&appId=" + context.getAppId() + "&serviceId=" + serviceId
            + "&workflowId=" + workflowId + "&workflowExecutionId=" + context.getWorkflowExecutionId();
      }

      if (createExperiment) {
        final String experimentalLogAnalysisSaveUrl = "/verification/learning-exp"
            + LogAnalysisResource.ANALYSIS_STATE_SAVE_ANALYSIS_RECORDS_URL + "?accountId=" + accountId
            + "&applicationId=" + applicationId + "&stateExecutionId=" + context.getStateExecutionId()
            + "&workflowExecutionId=" + context.getWorkflowExecutionId() + "&logCollectionMinute=" + logAnalysisMinute
            + "&isBaselineCreated=" + isBaselineCreated + "&taskId=" + uuid + "&stateType=" + context.getStateType();

        List<MLExperiments> experiments = learningEngineService.getExperiments(MLAnalysisType.LOG_ML);

        LearningEngineExperimentalAnalysisTaskBuilder experimentalAnalysisTaskBuilder =
            LearningEngineExperimentalAnalysisTask.builder()
                .accountId(context.getAccountId())
                .query(Lists.newArrayList(query.split(" ")))
                .workflow_id(context.getWorkflowId())
                .workflow_execution_id(context.getWorkflowExecutionId())
                .state_execution_id(context.getStateExecutionId())
                .service_id(context.getServiceId())
                .sim_threshold(0.9)
                .analysis_minute(logAnalysisMinute)
                .analysis_save_url(experimentalLogAnalysisSaveUrl)
                .log_analysis_get_url(logAnalysisGetUrl)
                .ml_analysis_type(MLAnalysisType.LOG_ML)
                .stateType(context.getStateType());

        if (!isEmpty(feedback_url)) {
          experimentalAnalysisTaskBuilder.feedback_url(feedback_url);
        }
        if (isBaselineCreated) {
          experimentalAnalysisTaskBuilder.control_input_url(controlInputUrl)
              .test_input_url(testInputUrl)
              .control_nodes(controlNodes)
              .test_nodes(testNodes);
        } else {
          experimentalAnalysisTaskBuilder.control_input_url(testInputUrl).control_nodes(testNodes);
        }

        LearningEngineExperimentalAnalysisTask experimentalAnalysisTask;
        for (MLExperiments experiment : experiments) {
          experimentalAnalysisTask =
              experimentalAnalysisTaskBuilder.experiment_name(experiment.getExperimentName()).build();
          experimentalAnalysisTask.setAppId(applicationId);
          experimentalAnalysisTask.setUuid(uuid);
          learningEngineService.addLearningEngineExperimentalAnalysisTask(experimentalAnalysisTask);
        }
      }

      boolean isFlagEnabled =
          managerClientHelper.callManagerWithRetry(managerClient.isFeatureEnabled(DISABLE_LOGML_NEURAL_NET, accountId))
              .getResource();
      String featureName = isFlagEnabled ? "DISABLE_LOGML_NEURAL_NET" : null;

      if (context.getStateType() == StateType.BUG_SNAG) {
        featureName = "DISABLE_LOGML_NEURAL_NET";
      }

      String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
          + VerificationConstants.NOTIFY_LEARNING_FAILURE + "taskId=" + uuid;
      LearningEngineAnalysisTaskBuilder analysisTaskBuilder;
      analysisTaskBuilder = LearningEngineAnalysisTask.builder()
                                .accountId(accountId)
                                .query(Lists.newArrayList(query.split(" ")))
                                .workflow_id(context.getWorkflowId())
                                .workflow_execution_id(context.getWorkflowExecutionId())
                                .state_execution_id(context.getStateExecutionId())
                                .service_id(context.getServiceId())
                                .sim_threshold(0.9)
                                .analysis_minute(logAnalysisMinute)
                                .analysis_start_min((int) context.getStartDataCollectionMinute())
                                .analysis_save_url(logAnalysisSaveUrl)
                                .log_analysis_get_url(logAnalysisGetUrl)
                                .ml_analysis_type(MLAnalysisType.LOG_ML)
                                .feature_name(featureName)
                                .priority(0)
                                .stateType(context.getStateType())
                                .analysis_failure_url(failureUrl);

      if (!isEmpty(feedback_url)) {
        analysisTaskBuilder.feedback_url(feedback_url);
      }

      if (isBaselineCreated) {
        analysisTaskBuilder.control_input_url(controlInputUrl)
            .test_input_url(testInputUrl)
            .control_nodes(controlNodes)
            .test_nodes(testNodes);
      } else {
        analysisTaskBuilder.control_input_url(testInputUrl).control_nodes(testNodes).analysis_minute(logAnalysisMinute);
      }

      LearningEngineAnalysisTask analysisTask = analysisTaskBuilder.build();
      analysisTask.setAppId(applicationId);
      analysisTask.setUuid(uuid);
      learningEngineService.addLearningEngineAnalysisTask(analysisTask);

    } catch (Exception e) {
      throw new RuntimeException("Log analysis failed for " + context.getStateExecutionId() + " for minute "
              + logAnalysisMinute + ", reason: " + ExceptionUtils.getMessage(e),
          e);
    }
  }

  private void generateFeedbackAnalysis() {
    log.info("Creating Feedback analysis task for {}", context.getStateExecutionId());
    String feedbackUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS + GET_LOG_FEEDBACKS
        + "?stateExecutionId=" + context.getStateExecutionId() + "&appId=" + context.getAppId();
    final String taskId = generateUuid();

    final String lastWorkflowExecutionId = context.getPrevWorkflowExecutionId();
    final boolean isBaselineCreated = context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT
        || !isEmpty(lastWorkflowExecutionId);

    String logAnalysisSaveUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.ANALYSIS_STATE_SAVE_ANALYSIS_RECORDS_URL + "?accountId=" + accountId
        + "&applicationId=" + applicationId + "&stateExecutionId=" + context.getStateExecutionId()
        + "&logCollectionMinute=" + logAnalysisMinute + "&isBaselineCreated=" + isBaselineCreated + "&taskId=" + taskId
        + "&stateType=" + context.getStateType() + "&isFeedbackAnalysis=true";

    final String logMLResultUrl = "/verification/" + LogAnalysisResource.LOG_ANALYSIS
        + LogAnalysisResource.WORKFLOW_GET_ANALYSIS_RECORDS_URL + "?appId=" + context.getAppId()
        + "&accountId=" + context.getAccountId() + "&stateExecutionId=" + context.getStateExecutionId()
        + "&analysisMinute=" + logAnalysisMinute;

    final String failureUrl = "/verification/" + LearningEngineService.RESOURCE_URL
        + VerificationConstants.NOTIFY_LEARNING_FAILURE + "?taskId=" + taskId;

    LearningEngineAnalysisTask feedbackTask =
        LearningEngineAnalysisTask.builder()
            .accountId(accountId)
            .feedback_url(feedbackUrl)
            .logMLResultUrl(logMLResultUrl)
            .state_execution_id(context.getStateExecutionId())
            .query(Arrays.asList(context.getQuery()))
            .ml_analysis_type(MLAnalysisType.FEEDBACK_ANALYSIS)
            .service_id(learningEngineService.getServiceIdFromStateExecutionId(context.getStateExecutionId()))
            .shouldUseSupervisedModel(learningEngineService.shouldUseSupervisedModel(
                AnalysisContextKeys.stateExecutionId, context.getStateExecutionId()))
            .analysis_save_url(logAnalysisSaveUrl)
            .analysis_failure_url(failureUrl)
            .analysis_minute(logAnalysisMinute)
            .stateType(context.getStateType())
            .priority(0)
            .is24x7Task(false)
            .build();

    feedbackTask.setAppId(context.getAppId());
    feedbackTask.setUuid(taskId);

    learningEngineService.addLearningEngineAnalysisTask(feedbackTask);
    log.info("Created feedback task for state {} and minute {}", context.getStateExecutionId(), logAnalysisMinute);
  }

  public void sendStateNotification(AnalysisContext context, boolean error, String errorMsg, int logAnalysisMinute) {
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
      managerClientHelper.notifyManagerForVerificationAnalysis(context, response);
    }
  }
}
