/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.common.VerificationConstants.IS_EXPERIMENTAL;

import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.service.intfc.LogAnalysisService;

import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.realization.VerificationLogContext;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord;
import software.wings.service.impl.analysis.FeedbackAction;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisRequest;
import software.wings.service.impl.analysis.LogMLFeedbackRecord;
import software.wings.service.impl.analysis.LogRequest;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.analysis.LogAnalysisResource;
import software.wings.sm.StateType;
import software.wings.verification.log.LogsCVConfiguration;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(LogAnalysisResource.LOG_ANALYSIS)
@Path("/" + LogAnalysisResource.LOG_ANALYSIS)
@Produces("application/json")
@Scope(PermissionAttribute.ResourceType.SETTING)
public class LogVerificationResource {
  @Inject private LogAnalysisService analysisService;
  @Inject private WingsPersistence wingsPersistence;

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_STATE_GET_LOG_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Set<LogDataRecord>> getRawLogData(@QueryParam("accountId") String accountId,
      @QueryParam("workflowExecutionId") String workflowExecutionId,
      @QueryParam("clusterLevel") ClusterLevel clusterLevel, @QueryParam("compareCurrent") boolean compareCurrent,
      @QueryParam("stateType") StateType stateType, LogRequest logRequest) {
    return new RestResponse<>(analysisService.getLogData(
        logRequest, compareCurrent, workflowExecutionId, clusterLevel, stateType, accountId));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_GET_24X7_ALL_LOGS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Set<LogDataRecord>> getAllRawLogData(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("clusterLevel") ClusterLevel clusterLevel,
      @QueryParam("logCollectionMinute") int logCollectionMinute, @QueryParam("startMinute") int startMinute,
      @QueryParam("endMinute") int endMinute, @QueryParam(IS_EXPERIMENTAL) boolean isExperimental) {
    return getRawLogData(appId, cvConfigId, clusterLevel, logCollectionMinute, startMinute, endMinute,
        LogRequest.builder().isExperimental(isExperimental).build());
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_GET_24X7_LOG_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Set<LogDataRecord>> getRawLogData(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("clusterLevel") ClusterLevel clusterLevel,
      @QueryParam("logCollectionMinute") int logCollectionMinute, @QueryParam("startMinute") int startMinute,
      @QueryParam("endMinute") int endMinute, LogRequest logRequest) {
    return new RestResponse<>(analysisService.getLogData(
        appId, cvConfigId, clusterLevel, logCollectionMinute, startMinute, endMinute, logRequest));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_STATE_SAVE_LOG_URL)
  @Timed
  @DelegateAuth
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> saveRawLogData(@QueryParam("accountId") String accountId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("workflowId") String workflowId, @QueryParam("workflowExecutionId") String workflowExecutionId,
      @QueryParam("appId") final String appId, @QueryParam("serviceId") String serviceId,
      @QueryParam("clusterLevel") ClusterLevel clusterLevel, @QueryParam("delegateTaskId") String delegateTaskId,
      @QueryParam("stateType") StateType stateType, List<LogElement> logData) {
    return new RestResponse<>(analysisService.saveLogData(stateType, accountId, appId, cvConfigId, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, clusterLevel, delegateTaskId, logData));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_STATE_SAVE_24X7_CLUSTERED_LOG_URL)
  @Timed
  @DelegateAuth
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> saveClusteredLogData(@QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("appId") String appId, @QueryParam("clusterLevel") ClusterLevel clusterLevel,
      @QueryParam("logCollectionMinute") int logCollectionMinute, @QueryParam("host") String host,
      List<LogElement> logData) {
    return new RestResponse<>(
        analysisService.saveClusteredLogData(appId, cvConfigId, clusterLevel, logCollectionMinute, host, logData));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_STATE_SAVE_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> saveLogAnalysisMLRecords(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String applicationId, @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("logCollectionMinute") Integer logCollectionMinute,
      @QueryParam("isBaselineCreated") boolean isBaselineCreated, @QueryParam("taskId") String taskId,
      @QueryParam("baseLineExecutionId") String baseLineExecutionId, @QueryParam("stateType") StateType stateType,
      @QueryParam("isFeedbackAnalysis") boolean isFeedbackAnalysis,
      @QueryParam("workflowExecutionId") String workflowExecutionId, LogMLAnalysisRecord mlAnalysisResponse) {
    mlAnalysisResponse.setStateExecutionId(stateExecutionId);
    mlAnalysisResponse.setWorkflowExecutionId(workflowExecutionId);
    mlAnalysisResponse.setLogCollectionMinute(logCollectionMinute);
    mlAnalysisResponse.setBaseLineCreated(isBaselineCreated);
    mlAnalysisResponse.setBaseLineExecutionId(baseLineExecutionId);
    mlAnalysisResponse.setAppId(applicationId);
    mlAnalysisResponse.setAccountId(accountId);
    return new RestResponse<>(analysisService.saveLogAnalysisRecords(
        mlAnalysisResponse, stateType, Optional.of(taskId), Optional.of(isFeedbackAnalysis)));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_SAVE_24X7_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> saveLogAnalysisMLRecords(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("analysisMinute") int analysisMinute,
      @QueryParam("taskId") String taskId,
      @QueryParam("comparisonStrategy") AnalysisComparisonStrategy comparisonStrategy,
      @QueryParam("isFeedbackAnalysis") boolean isFeedbackAnalysis, LogMLAnalysisRecord mlAnalysisResponse) {
    final LogsCVConfiguration logsCVConfiguration = wingsPersistence.get(LogsCVConfiguration.class, cvConfigId);
    Preconditions.checkNotNull(logsCVConfiguration);
    try (VerificationLogContext ignored = new VerificationLogContext(logsCVConfiguration.getAccountId(), cvConfigId,
             null, logsCVConfiguration.getStateType().getDelegateStateType(), OVERRIDE_ERROR)) {
      return new RestResponse<>(analysisService.save24X7LogAnalysisRecords(
          appId, cvConfigId, analysisMinute, mlAnalysisResponse, Optional.of(taskId), Optional.of(isFeedbackAnalysis)));
    }
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_STATE_GET_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<LogMLAnalysisRecord> getLogMLAnalysisRecords(@QueryParam("accountId") String accountId,
      @QueryParam("stateType") StateType stateType, LogMLAnalysisRequest mlAnalysisRequest) {
    return new RestResponse<>(analysisService.getLogAnalysisRecords(LogMLAnalysisRecordKeys.stateExecutionId,
        mlAnalysisRequest.getStateExecutionId(), mlAnalysisRequest.getLogCollectionMinute(), false));
  }

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path(LogAnalysisResource.WORKFLOW_GET_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<LogMLAnalysisRecord> getWorkflowAnalysisRecord(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("analysisMinute") int analysisMinute) {
    return new RestResponse<>(analysisService.getLogAnalysisRecords(
        LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId, analysisMinute, false));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<LogMLAnalysisRecord> getLogMLAnalysisRecords(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("analysisMinute") int analysisMinute,
      @QueryParam("compressed") boolean isCompressed) {
    return new RestResponse<>(analysisService.getLogAnalysisRecords(
        LogMLAnalysisRecordKeys.cvConfigId, cvConfigId, analysisMinute, isCompressed));
  }

  @GET
  @Produces({"application/json", "application/v1+json"})
  @Path(LogAnalysisResource.ANALYSIS_GET_24X7_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<LogMLAnalysisRecord> getLogAnalysisRecords(@QueryParam("appId") String appId,

      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("analysisMinute") int analysisMinute,
      @QueryParam("compressed") boolean isCompressed) {
    return new RestResponse<>(analysisService.getLogAnalysisRecords(
        LogMLAnalysisRecordKeys.cvConfigId, cvConfigId, analysisMinute, isCompressed));
  }

  @DELETE
  @Path(LogAnalysisResource.ANALYSIS_USER_FEEDBACK + "/{feedbackId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteUserFeedback(
      @QueryParam("accountId") String accountId, @PathParam("feedbackId") String feedbackId) {
    return new RestResponse<>(analysisService.deleteFeedback(feedbackId));
  }

  @GET
  @Produces({"application/json", "application/v1+json"})
  @Path(LogAnalysisResource.ANALYSIS_USER_FEEDBACK)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<List<LogMLFeedbackRecord>> getFeedback(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("serviceId") String serviceId,
      @QueryParam("workflowId") String workflowId, @QueryParam("workflowExecutionId") String workflowExecutionId) {
    return new RestResponse<>(analysisService.getMLFeedback(appId, serviceId, workflowId, workflowExecutionId));
  }

  @GET
  @Produces({"application/json", "application/v1+json"})
  @Path(VerificationConstants.GET_LOG_FEEDBACKS)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Map<FeedbackAction, List<CVFeedbackRecord>>> getFeedback(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId) {
    return new RestResponse<>(analysisService.getUserFeedback(cvConfigId, stateExecutionId, appId));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(LogAnalysisResource.ANALYSIS_SAVE_EXP_24X7_ANALYSIS_RECORDS_URL)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> saveExperimentalLogAnalysisMLRecords(@QueryParam("appId") String appId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("analysisMinute") int analysisMinute,
      @QueryParam("taskId") String taskId,
      @QueryParam("comparisonStrategy") AnalysisComparisonStrategy comparisonStrategy,
      @QueryParam("isFeedbackAnalysis") boolean isFeedbackAnalysis,
      ExperimentalLogMLAnalysisRecord mlAnalysisResponse) {
    return new RestResponse<>(analysisService.save24X7ExpLogAnalysisRecords(appId, cvConfigId, analysisMinute,
        comparisonStrategy, mlAnalysisResponse, Optional.of(taskId), Optional.of(isFeedbackAnalysis)));
  }
}
