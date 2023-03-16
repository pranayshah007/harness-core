/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.managerclient;

import static software.wings.delegatetasks.cv.CVConstants.DELEGATE_DATA_COLLECTION;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.rest.RestResponse;

import software.wings.beans.dto.CVActivityLog;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.CVConstants;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.analysis.LogAnalysisResource;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public interface VerificationServiceClient {
  @POST(DELEGATE_DATA_COLLECTION + "/save-metrics")
  Call<RestResponse<Boolean>> saveTimeSeriesMetrics(@Query("accountId") String accountId,
      @Query("applicationId") String applicationId, @Query("stateExecutionId") String stateExecutionId,
      @Query("delegateTaskId") String delegateTaskId, @Body List<NewRelicMetricDataRecord> metricData);

  @POST(DELEGATE_DATA_COLLECTION + LogAnalysisResource.ANALYSIS_STATE_SAVE_LOG_URL)
  Call<RestResponse<Boolean>> saveLogs(@Query("accountId") String accountId, @Query("appId") String appId,
      @Query("cvConfigId") String cvConfigId, @Query("stateExecutionId") String stateExecutionId,
      @Query("workflowId") String workflowId, @Query("workflowExecutionId") String workflowExecutionId,
      @Query("serviceId") String serviceId, @Query("clusterLevel") ClusterLevel clusterLevel,
      @Query("delegateTaskId") String delegateTaskId, @Query("stateType") DelegateStateType stateType,
      @Body List<LogElement> metricData);
  @POST(DELEGATE_DATA_COLLECTION + CVConstants.SAVE_CV_ACTIVITY_LOGS_PATH)
  Call<RestResponse<Void>> saveActivityLogs(
      @Query("accountId") String accountId, @Body List<CVActivityLog> activityLogs);

  @POST(DELEGATE_DATA_COLLECTION + CVConstants.CV_TASK_STATUS_UPDATE_PATH)
  Call<RestResponse<Void>> updateCVTaskStatus(@Query("accountId") String accountId, @Query("cvTaskId") String cvTaskId,
      @Body DataCollectionTaskResult dataCollectionTaskResult);
}
