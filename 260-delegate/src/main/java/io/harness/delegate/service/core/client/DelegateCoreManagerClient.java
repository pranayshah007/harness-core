/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.rest.RestResponse;
import io.harness.serializer.kryo.KryoRequest;
import io.harness.serializer.kryo.KryoResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateCoreManagerClient {
  @GET("agent/{delegateId}/tasks/{taskId}/acquire")
  Call<AcquireTasksResponse> acquireProtoTask(@Path("delegateId") String delegateId, @Path("taskId") String uuid,
      @Query("accountId") String accountId, @Query("delegateInstanceId") String delegateInstanceId);

  @POST("agent/tasks/{taskId}/status")
  Call<ResponseBody> sendProtoTaskStatus(
      @Path("taskId") String taskId, @Query("accountId") String accountId, @Body ExecutionStatusResponse taskStatus);

  @POST("agent/delegates/register")
  Call<RestResponse<DelegateRegisterResponse>> registerDelegate(
      @Query("accountId") String accountId, @Body DelegateParams delegateParams);

  @POST("agent/delegates/unregister")
  Call<RestResponse<Void>> unregisterDelegate(
      @Query("accountId") String accountId, @Body DelegateUnregisterRequest request);

  // For polling mode
  @POST("agent/delegates/connectionHeartbeat/{delegateId}")
  Call<RestResponse> doConnectionHeartbeat(@Path("delegateId") String delegateId, @Query("accountId") String accountId,
      @Body DelegateConnectionHeartbeat heartbeat);

  @POST("agent/delegates/heartbeat-with-polling")
  Call<RestResponse<DelegateHeartbeatResponse>> delegateHeartbeat(
      @Query("accountId") String accountId, @Body DelegateParams delegateParams);

  @GET("agent/delegates/{delegateId}/task-events")
  Call<DelegateTaskEventsResponse> pollTaskEvents(
      @Path("delegateId") String delegateId, @Query("accountId") String accountId);

  // Support for Kryo task acquire
  @KryoResponse
  @PUT("agent/delegates/{delegateId}/tasks/{taskId}/acquire/v2")
  Call<DelegateTaskPackage> acquireTask(@Path("delegateId") String delegateId, @Path("taskId") String uuid,
      @Query("accountId") String accountId, @Query("delegateInstanceId") String delegateInstanceId);

  // Support for Kryo task response
  @Headers({"Content-Type: application/x-kryo-v2"})
  @KryoRequest
  @POST("agent/tasks/{taskId}/delegates/{delegateId}/v2")
  Call<ResponseBody> sendTaskStatus(@Path("delegateId") String delegateId, @Path("taskId") String taskId,
      @Query("accountId") String accountId, @Body DelegateTaskResponse delegateTaskResponse);
}
