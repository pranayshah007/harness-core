/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.System.currentTimeMillis;

import io.harness.beans.FeatureName;
import io.harness.entities.TimeSeriesAnomaliesRecord;
import io.harness.entities.TimeSeriesCumulativeSums;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.TimeSeriesAnalysisService;

import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.GoogleDataStoreServiceImpl;
import software.wings.service.impl.realization.VerificationLogContext;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.TSRequest;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLScores;
import software.wings.service.impl.analysis.Version;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.sm.StateType;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 04/11/18.
 */
@Api(MetricDataAnalysisService.RESOURCE_URL)
@Path("/" + MetricDataAnalysisService.RESOURCE_URL)
@Produces("application/json")
@Scope(ResourceType.SETTING)
@Slf4j
public class TimeSeriesResource {
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private LearningEngineService learningEngineService;
  @Inject private VerificationManagerClient managerClient;
  @Inject private VerificationManagerClientHelper managerClientHelper;
  @Inject private DataStoreService dataStoreService;

  @VisibleForTesting
  @Inject
  public TimeSeriesResource(TimeSeriesAnalysisService timeSeriesAnalysisService,
      VerificationManagerClientHelper managerClientHelper, VerificationManagerClient managerClient,
      LearningEngineService learningEngineService) {
    this.timeSeriesAnalysisService = timeSeriesAnalysisService;
    this.managerClientHelper = managerClientHelper;
    this.managerClient = managerClient;
    this.learningEngineService = learningEngineService;
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/get-metrics")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<Set<NewRelicMetricDataRecord>> getMetricData(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("workflowExecutionId") String workflowExecutionId,
      @QueryParam("groupName") final String groupName, @QueryParam("compareCurrent") boolean compareCurrent,
      TSRequest request) {
    if (compareCurrent) {
      return new RestResponse<>(timeSeriesAnalysisService.getRecords(appId, request.getStateExecutionId(), groupName,
          request.getNodes(), request.getAnalysisMinute(), request.getAnalysisStartMinute(), accountId));
    } else {
      if (workflowExecutionId == null || workflowExecutionId.equals("-1")) {
        return new RestResponse<>(new HashSet<>());
      }
      return new RestResponse<>(timeSeriesAnalysisService.getPreviousSuccessfulRecords(appId, workflowExecutionId,
          groupName, request.getAnalysisMinute(), request.getAnalysisStartMinute(), accountId));
    }
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/save-analysis")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> saveMLAnalysisRecords(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String appId, @QueryParam("stateType") StateType stateType,
      @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("workflowExecutionId") final String workflowExecutionId,
      @QueryParam("groupName") final String groupName, @QueryParam("analysisMinute") Integer analysisMinute,
      @QueryParam("taskId") String taskId, @QueryParam("baseLineExecutionId") String baseLineExecutionId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("tag") String tag,
      TimeSeriesMLAnalysisRecord mlAnalysisResponse) {
    try (VerificationLogContext ignored = new VerificationLogContext(
             accountId, cvConfigId, stateExecutionId, stateType.getDelegateStateType(), OVERRIDE_ERROR)) {
      LearningEngineAnalysisTask analysisTask = learningEngineService.getTaskById(taskId);
      Preconditions.checkNotNull(analysisTask);
      long currentEpoch = currentTimeMillis();
      long timeTaken = currentEpoch - analysisTask.getCreatedAt();
      log.info("Finished analysis: Analysis type: {}, time delay: {} seconds", MLAnalysisType.TIME_SERIES.name(),
          TimeUnit.MILLISECONDS.toSeconds(timeTaken));
      return new RestResponse<>(timeSeriesAnalysisService.saveAnalysisRecordsML(accountId, stateType, appId,
          stateExecutionId, workflowExecutionId, groupName, analysisMinute, taskId, baseLineExecutionId, cvConfigId,
          mlAnalysisResponse, tag));
    }
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/get-scores")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<List<TimeSeriesMLScores>> getScores(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String applicationId, @QueryParam("workflowId") String workflowId,
      @QueryParam("analysisMinute") Integer analysisMinute, @QueryParam("limit") Integer limit) {
    return new RestResponse<>(
        timeSeriesAnalysisService.getTimeSeriesMLScores(applicationId, workflowId, analysisMinute, limit));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/get-metric-template")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @Deprecated
  public RestResponse<Map<String, Map<String, TimeSeriesMetricDefinition>>> getMetricTemplatePost(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId,
      @QueryParam("stateType") StateType stateType, @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("serviceId") String serviceId, @QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("groupName") String groupName) {
    return new RestResponse<>(timeSeriesAnalysisService.getMetricTemplate(
        appId, stateType, stateExecutionId, serviceId, cvConfigId, groupName));
  }

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/get-metric-template")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Map<String, Map<String, TimeSeriesMetricDefinition>>> getMetricTemplate(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId,
      @QueryParam("stateType") StateType stateType, @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("serviceId") String serviceId, @QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("groupName") String groupName) {
    boolean isWorkflowTask = cvConfigId == null;
    if (managerClientHelper
            .callManagerWithRetry(managerClient.isFeatureEnabled(FeatureName.SUPERVISED_TS_THRESHOLD, accountId))
            .getResource()
        && dataStoreService instanceof GoogleDataStoreServiceImpl && isWorkflowTask) {
      return new RestResponse<>(timeSeriesAnalysisService.getMetricTemplateWithCategorizedThresholds(
          appId, stateType, stateExecutionId, serviceId, cvConfigId, groupName, Version.PROD));
    } else {
      return new RestResponse<>(timeSeriesAnalysisService.getMetricTemplate(
          appId, stateType, stateExecutionId, serviceId, cvConfigId, groupName));
    }
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/get-metric-data-247")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<Set<NewRelicMetricDataRecord>> getMetricRecords(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("stateType") StateType stateType,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("serviceId") String serviceId,
      @QueryParam("analysisStartMin") int analysisStartMin, @QueryParam("analysisEndMin") int analysisEndMin,
      @QueryParam("tag") String tag, TSRequest request) {
    return new RestResponse<>(
        timeSeriesAnalysisService.getMetricRecords(cvConfigId, analysisStartMin, analysisEndMin, tag, accountId));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/previous-analysis-247")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<TimeSeriesMLAnalysisRecord> getPreviousAnalysis(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("dataCollectionMin") long dataCollectionMin,
      @QueryParam("tag") String tag) {
    return new RestResponse<>(timeSeriesAnalysisService.getPreviousAnalysis(appId, cvConfigId, dataCollectionMin, tag));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/historical-analysis-24x7")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<List<TimeSeriesMLAnalysisRecord>> getHistoricalAnalysis(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String appId, @QueryParam("analysisMinute") Integer analysisMinute,
      @QueryParam("serviceId") String serviceId, @QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("tag") String tag) {
    return new RestResponse<>(
        timeSeriesAnalysisService.getHistoricalAnalysis(accountId, appId, serviceId, cvConfigId, analysisMinute, tag));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/previous-anomalies-247")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<TimeSeriesAnomaliesRecord> getPreviousAnomalies(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String appId, @QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("tag") String tag, Map<String, List<String>> metrics) {
    return new RestResponse<>(timeSeriesAnalysisService.getPreviousAnomalies(appId, cvConfigId, metrics, tag));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/cumulative-sums-247")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<Set<TimeSeriesCumulativeSums>> getCumulativeSums(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String appId, @QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("analysisMinStart") Integer startMinute, @QueryParam("analysisMinEnd") Integer endMinute,
      @QueryParam("tag") String tag) {
    return new RestResponse<>(
        timeSeriesAnalysisService.getCumulativeSumsForRange(appId, cvConfigId, startMinute, endMinute, tag));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/key-transactions-247")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<Set<String>> getKeyTransactions(@QueryParam("cvConfigId") String cvConfigId) {
    return new RestResponse<>(timeSeriesAnalysisService.getKeyTransactions(cvConfigId));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/save-dummy-experimental-247")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<Boolean> saveDummy247ExperimentalAnalysis(@QueryParam("taskId") String taskId) {
    learningEngineService.markExpTaskCompleted(taskId);
    return new RestResponse<>(true);
  }
}
