/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.managerclient;

import static software.wings.common.VerificationConstants.CHECK_STATE_VALID;
import static software.wings.common.VerificationConstants.LAST_SUCCESSFUL_WORKFLOW_IDS;
import static software.wings.common.VerificationConstants.WORKFLOW_FOR_STATE_EXEC;

import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.beans.Account;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.common.VerificationConstants;
import software.wings.service.impl.analysis.CVFeedbackRecord;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.intfc.analysis.LogAnalysisResource;
import software.wings.sm.StateType;
import software.wings.verification.VerificationDataAnalysisResponse;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Interface containing API's to interact with manager.
 * Created by raghu on 09/17/18.
 */
public interface VerificationManagerClient {
  @GET("workflows" + LAST_SUCCESSFUL_WORKFLOW_IDS)
  Call<RestResponse<List<String>>> getLastSuccessfulWorkflowExecutionIds(
      @Query("appId") String appId, @Query("workflowId") String workflowId, @Query("serviceId") String serviceId);

  @GET("workflows" + CHECK_STATE_VALID)
  Call<RestResponse<Boolean>> isStateValid(
      @Query("appId") String appId, @Query("stateExecutionId") String stateExecutionId);

  @GET("workflows" + WORKFLOW_FOR_STATE_EXEC)
  Call<RestResponse<WorkflowExecution>> getWorkflowExecution(
      @Query("appId") String appId, @Query("stateExecutionId") String stateExecutionId);

  @GET("setup/delegates/available-versions-for-verification")
  Call<RestResponse<List<String>>> getListOfPublishedVersions(@Query("accountId") String accountId);

  @POST("apm" + VerificationConstants.NOTIFY_VERIFICATION_STATE)
  Call<RestResponse<Boolean>> sendNotifyForVerificationState(@HeaderMap Map<String, Object> headers,
      @Query("correlationId") String correlationId, @Body VerificationDataAnalysisResponse metricAnalysisResponse);

  @GET("account") Call<RestResponse<PageResponse<Account>>> getAccounts(@Query("offset") String offset);

  @GET("account/feature-flag-enabled")
  Call<RestResponse<Boolean>> isFeatureEnabled(
      @Query("featureName") FeatureName featureName, @Query("accountId") String accountId);

  @GET("apm" + VerificationConstants.COLLECT_24_7_DATA)
  Call<RestResponse<Boolean>> triggerCVDataCollection(@Query("cvConfigId") String cvConfigId,
      @Query("stateType") StateType stateType, @Query("startTime") long startTime, @Query("endTime") long endTime);

  @GET("apm" + VerificationConstants.COLLECT_DATA)
  Call<RestResponse<Boolean>> triggerWorkflowDataCollection(
      @Query("analysisContextId") String contextId, @Query("startDataCollectionMinute") long collectionMinute);

  @POST("apm" + VerificationConstants.COLLECT_CV_DATA)
  Call<RestResponse<Boolean>> collectCVData(
      @Query("cvTaskId") String cvTaskId, @Body DataCollectionInfoV2 dataCollectionInfoV2);

  @GET("harness-api-keys/validate")
  Call<RestResponse<Boolean>> validateHarnessApiKey(
      @Query("clientType") String clientType, @Query("apiKey") String apiKey);

  @POST("alerts/open-cv-alert")
  Call<RestResponse<Boolean>> triggerCVAlert(
      @Query("cvConfigId") String cvConfigId, @Body ContinuousVerificationAlertData alertData);

  @POST("alerts/open-cv-alert-with-ttl")
  Call<RestResponse<Boolean>> triggerCVAlertWithTtl(@Query("cvConfigId") String cvConfigId,
      @Query("validUntil") long validUntil, @Body ContinuousVerificationAlertData alertData);

  @POST("alerts/close-cv-alert")
  Call<RestResponse<Boolean>> closeCVAlert(
      @Query("cvConfigId") String cvConfigId, @Body ContinuousVerificationAlertData alertData);

  @GET("logml" + LogAnalysisResource.GET_FEEDBACK_LIST_LE)
  Call<RestResponse<List<CVFeedbackRecord>>> getFeedbackList(
      @Query("cvConfigId") String cvConfigId, @Query("stateExecutionId") String stateExecutionId);

  @POST(VerificationConstants.LEARNING_METRIC_EXP_URL + VerificationConstants.UPDATE_MISMATCH)
  Call<RestResponse<Boolean>> updateMismatchStatusInExperiment(
      @Query("stateExecutionId") String stateExecutionId, @Query("analysisMinute") Integer analysisMinute);

  @POST("account/validate-delegate-token")
  Call<RestResponse<Boolean>> authenticateDelegateRequest(@Query("accountId") String accountId,
      @Query("delegateToken") String delegateToken, @Query("delegateId") String delegateId,
      @Query("delegateTokenName") String delegateTokenName, @Query("agentMtlsAuthority") String agentMtlsAuthority);
}
