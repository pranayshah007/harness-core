/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.managerclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.task.validation.DelegateConnectionResultDetail;
import io.harness.logging.AccessTokenBean;
import io.harness.rest.RestResponse;
import io.harness.serializer.kryo.KryoRequest;
import io.harness.serializer.kryo.KryoResponse;

import software.wings.beans.ConfigFileDto;

import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateAgentManagerClient {
  @POST("agent/delegates/register")
  Call<RestResponse<DelegateRegisterResponse>> registerDelegate(
      @Query("accountId") String accountId, @Body DelegateParams delegateParams);

  @POST("agent/delegates/unregister")
  Call<RestResponse<Void>> unregisterDelegate(
      @Query("accountId") String accountId, @Body DelegateUnregisterRequest request);

  @POST("agent/delegates/connectionHeartbeat/{delegateId}")
  Call<RestResponse> doConnectionHeartbeat(@Path("delegateId") String delegateId, @Query("accountId") String accountId,
      @Body DelegateConnectionHeartbeat heartbeat);

  @Headers({"Content-Type: application/x-kryo"})
  @KryoRequest
  @POST("agent/tasks/{taskId}/delegates/{delegateId}")
  Call<ResponseBody> sendTaskStatus(@Path("delegateId") String delegateId, @Path("taskId") String taskId,
      @Query("accountId") String accountId, @Body DelegateTaskResponse delegateTaskResponse);

  @GET("agent/delegates/{delegateId}/profile")
  Call<RestResponse<DelegateProfileParams>> checkForProfile(@Path("delegateId") String delegateId,
      @Query("accountId") String accountId, @Query("profileId") String profileId,
      @Query("lastUpdatedAt") Long lastUpdatedAt);

  @Multipart
  @POST("agent/delegateFiles/{delegateId}/profile-result")
  Call<RestResponse> saveProfileResult(@Path("delegateId") String delegateId, @Query("accountId") String accountId,
      @Query("error") boolean error, @Query("fileBucket") FileBucket bucket, @Part MultipartBody.Part file);

  @Multipart
  @POST("agent/delegateFiles/{delegateId}/tasks/{taskId}")
  Call<RestResponse<String>> uploadFile(@Path("delegateId") String delegateId, @Path("taskId") String taskId,
      @Query("accountId") String accountId, @Query("fileBucket") FileBucket bucket, @Part MultipartBody.Part file);

  @GET("agent/delegateFiles/fileId")
  Call<RestResponse<String>> getFileIdByVersion(@Query("entityId") String entityId,
      @Query("fileBucket") FileBucket fileBucket, @Query("version") int version, @Query("accountId") String accountId);

  @GET("agent/delegateFiles/download")
  Call<ResponseBody> downloadFile(
      @Query("fileId") String fileId, @Query("fileBucket") FileBucket fileBucket, @Query("accountId") String accountId);

  @GET("agent/delegateFiles/downloadConfig")
  Call<ResponseBody> downloadFile(@Query("fileId") String fileId, @Query("accountId") String accountId,
      @Query("appId") String appId, @Query("activityId") String activityId);

  @GET("agent/delegateFiles/metainfo")
  Call<RestResponse<DelegateFile>> getMetaInfo(
      @Query("fileId") String fileId, @Query("fileBucket") FileBucket fileBucket, @Query("accountId") String accountId);

  @GET("agent/delegates/delegateScripts")
  Call<RestResponse<DelegateScripts>> getDelegateScripts(@Query("accountId") String accountId,
      @Query("delegateVersion") String delegateVersion, @Query("delegateName") String delegateName);

  @GET("agent/infra-download/delegate-auth/delegate/logging-token")
  Call<RestResponse<AccessTokenBean>> getLoggingToken(@Query("accountId") String accountId);

  @GET("agent/delegates/{delegateId}/task-events")
  Call<DelegateTaskEventsResponse> pollTaskEvents(
      @Path("delegateId") String delegateId, @Query("accountId") String accountId);

  @POST("agent/delegates/instance-sync/{perpetualTaskId}")
  Call<RestResponse<Boolean>> publishInstanceSyncResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body DelegateResponseData responseData);

  @POST("agent/delegates/instance-sync-ng/{perpetualTaskId}")
  Call<RestResponse<Boolean>> processInstanceSyncNGResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body InstanceSyncPerpetualTaskResponse responseData);

  // Query for a specific set of delegate properties for a given account.
  // Request: GetDelegatePropertiesRequest
  // Response: GetDelegatePropertiesResponse
  @POST("agent/delegates/properties")
  Call<RestResponse<String>> getDelegateProperties(@Query("accountId") String accountId, @Body RequestBody request);

  @POST("logs/activity/{activityId}/unit/{unitName}/batched")
  Call<RestResponse> saveCommandUnitLogs(@Path("activityId") String activityId, @Path("unitName") String unitName,
      @Query("accountId") String accountId, @Body RequestBody logObject);

  @POST("agent/delegates/{delegateId}/state-executions")
  Call<RestResponse> saveApiCallLogs(
      @Path("delegateId") String delegateId, @Query("accountId") String accountId, @Body RequestBody logObject);

  @POST("agent/delegates/manifest-collection/{perpetualTaskId}")
  Call<RestResponse<Boolean>> publishManifestCollectionResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body RequestBody manifestCollectionExecutionResponse);

  @POST("agent/delegates/connectors/{perpetualTaskId}")
  Call<RestResponse<Boolean>> publishConnectorHeartbeatResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body ConnectorHeartbeatDelegateResponse responseData);

  @POST("agent/delegates/artifact-collection/{perpetualTaskId}")
  Call<RestResponse<Boolean>> publishArtifactCollectionResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body RequestBody buildSourceExecutionResponse);

  @POST("agent/delegates/polling/{perpetualTaskId}")
  Call<RestResponse<Boolean>> publishPollingResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body RequestBody buildSourceExecutionResponse);

  @KryoResponse
  @PUT("agent/delegates/{delegateId}/tasks/{taskId}/acquire")
  Call<DelegateTaskPackage> acquireTask(@Path("delegateId") String delegateId, @Path("taskId") String uuid,
      @Query("accountId") String accountId, @Query("delegateInstanceId") String delegateInstanceId);

  @POST("agent/delegates/heartbeat-with-polling")
  Call<RestResponse<DelegateHeartbeatResponse>> delegateHeartbeat(
      @Query("accountId") String accountId, @Body DelegateParams delegateParams);

  @GET("service-templates/{templateId}/compute-files-dto")
  Call<RestResponse<List<ConfigFileDto>>> getConfigFiles(@Path("templateId") String templateId,
      @Query("accountId") String accountId, @Query("appId") String appId, @Query("envId") String envId,
      @Query("hostId") String hostId);

  @KryoResponse
  @POST("agent/delegates/{delegateId}/tasks/{taskId}/report")
  Call<DelegateTaskPackage> reportConnectionResults(@Path("delegateId") String delegateId, @Path("taskId") String uuid,
      @Query("accountId") String accountId, @Query("delegateInstanceId") String delegateInstanceId,
      @Body List<DelegateConnectionResultDetail> results);
}
