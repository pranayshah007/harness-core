/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.beans.FeatureName;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.analysis.CVCollaborationProviderParameters;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.FeedbackAction;
import software.wings.service.impl.analysis.LogMLAnalysisSummary;
import software.wings.service.impl.analysis.LogMLFeedback;
import software.wings.service.impl.analysis.LogMLFeedbackRecord;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.analysis.LogAnalysisResource;
import software.wings.service.intfc.verification.CV24x7DashboardService;
import software.wings.sm.StateType;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
public class LogMLResource {
  @Inject private AnalysisService analysisService;
  @Inject private CV24x7DashboardService cv24x7DashboardService;
  @Inject private FeatureFlagService featureFlagService;

  @GET
  @Path(LogAnalysisResource.ANALYSIS_STATE_GET_ANALYSIS_SUMMARY_URL)
  @Timed
  @ExceptionMetered
  public RestResponse<LogMLAnalysisSummary> getLogAnalysisSummary(@QueryParam("accountId") String accountId,
      @QueryParam("applicationId") String applicationId, @QueryParam("stateExecutionId") String stateExecutionId,
      @QueryParam("stateType") StateType stateType) throws IOException {
    if (featureFlagService.isEnabledReloadCache(FeatureName.CV_DEMO, accountId)) {
      return new RestResponse<>(analysisService.getAnalysisSummaryForDemo(stateExecutionId, applicationId, stateType));
    } else {
      return new RestResponse<>(analysisService.getAnalysisSummary(stateExecutionId, applicationId, stateType));
    }
  }

  @POST
  @Path(LogAnalysisResource.ANALYSIS_USER_FEEDBACK)
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> createUserFeedback(
      @QueryParam("accountId") String accountId, @QueryParam("stateType") StateType stateType, LogMLFeedback feedback) {
    if (!isEmpty(feedback.getLogMLFeedbackId())) {
      throw new WingsException("feedback id should not be set in POST call. to update feedback use PUT");
    }
    return new RestResponse<>(analysisService.saveFeedback(feedback, stateType));
  }

  @DELETE
  @Path(LogAnalysisResource.ANALYSIS_USER_FEEDBACK + "/{feedbackId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteUserFeedback(
      @QueryParam("accountId") String accountId, @PathParam("feedbackId") String feedbackId) throws IOException {
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
  @Path(LogAnalysisResource.ANALYSIS_USER_FEEDBACK_BY_WORKFLOW)
  @Timed
  @ExceptionMetered
  public RestResponse<List<LogMLFeedbackRecord>> getFeedbackForDashboard(
      @QueryParam("accountId") String accountId, @QueryParam("workflowId") String workflowId) {
    return new RestResponse<>(null);
  }

  @GET
  @Produces({"application/json", "application/v1+json"})
  @Path(LogAnalysisResource.LAST_EXECUTION_NODES)
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, Map<String, InstanceDetails>>> getLastExecutionNodes(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId,
      @QueryParam("workflowId") String workflowId) {
    return new RestResponse<>(analysisService.getLastExecutionNodes(appId, workflowId));
  }

  @POST
  @Path(LogAnalysisResource.ANALYSIS_24x7_USER_FEEDBACK)
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> create24x7UserFeedback(
      @QueryParam("accountId") String accountId, @QueryParam("cvConfigId") String cvConfigId, LogMLFeedback feedback) {
    if (!isEmpty(feedback.getLogMLFeedbackId())) {
      throw new WingsException("Feedback id should not be set in POST call. to update feedback use PUT", USER);
    }
    return new RestResponse<>(analysisService.save24x7Feedback(feedback, cvConfigId));
  }

  @GET
  @Produces({"application/json", "application/v1+json"})
  @Path(LogAnalysisResource.ANALYSIS_24x7_USER_FEEDBACK)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<List<LogMLFeedbackRecord>> get24x7Feedback(
      @QueryParam("accountId") String accountId, @QueryParam("cvConfigId") String cvConfigId) {
    return new RestResponse<>(analysisService.get24x7MLFeedback(cvConfigId));
  }

  @GET
  @Path(LogAnalysisResource.GET_FEEDBACK_LIST)
  @Timed
  @ExceptionMetered
  public RestResponse<List<CVFeedbackRecord>> getFeedbacks(@QueryParam("accountId") String accountId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId) {
    return new RestResponse<>(analysisService.getFeedbacks(cvConfigId, stateExecutionId, false));
  }

  @GET
  @Path(LogAnalysisResource.GET_FEEDBACK_LIST_LE)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  public RestResponse<List<CVFeedbackRecord>> getFeedbacksLE(@QueryParam("accountId") String accountId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId) {
    return new RestResponse<>(analysisService.getFeedbacks(cvConfigId, stateExecutionId, false));
  }

  @GET
  @Path(LogAnalysisResource.GET_FEEDBACK_ACTION_LIST)
  @Timed
  @ExceptionMetered
  public RestResponse<Map<FeedbackAction, List<FeedbackAction>>> getFeedbacksActionList() {
    return new RestResponse<>(analysisService.getNextFeedbackActions());
  }

  @POST
  @Path(LogAnalysisResource.FEEDBACK_ADD_TO_BASELINE)
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> addToBaseline(@QueryParam("accountId") String accountId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId,
      CVFeedbackRecord feedback) {
    return new RestResponse<>(analysisService.addToBaseline(accountId, cvConfigId, stateExecutionId, feedback));
  }

  @POST
  @Path(LogAnalysisResource.FEEDBACK_REMOVE_FROM_BASELINE)
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> removeFromBaseline(@QueryParam("accountId") String accountId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId,
      CVFeedbackRecord feedback) {
    return new RestResponse<>(analysisService.removeFromBaseline(accountId, cvConfigId, stateExecutionId, feedback));
  }

  @POST
  @Path(LogAnalysisResource.FEEDBACK_UPDATE_PRIORITY)
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateFeedbackPriority(@QueryParam("accountId") String accountId,
      @QueryParam("cvConfigId") String cvConfigId, @QueryParam("stateExecutionId") String stateExecutionId,
      CVFeedbackRecord feedback) {
    return new RestResponse<>(
        analysisService.updateFeedbackPriority(accountId, cvConfigId, stateExecutionId, feedback));
  }

  @POST
  @Path(LogAnalysisResource.FEEDBACK_CREATE_JIRA)
  @Timed
  @ExceptionMetered
  public RestResponse<String> createJiraForAnomaly(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("stateExecutionId") String stateExecutionId, CVCollaborationProviderParameters cvJiraParameters) {
    return new RestResponse<>(analysisService.createCollaborationFeedbackTicket(
        accountId, appId, cvConfigId, stateExecutionId, cvJiraParameters));
  }
}
