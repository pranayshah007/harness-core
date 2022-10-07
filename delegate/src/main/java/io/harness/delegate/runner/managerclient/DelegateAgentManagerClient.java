/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.runner.managerclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.FileBucket;
import io.harness.rest.RestResponse;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateAgentManagerClient {
    @POST("logs/activity/{activityId}/unit/{unitName}/batched")
    Call<RestResponse> saveCommandUnitLogs(@Path("activityId") String activityId, @Path("unitName") String unitName,
                                           @Query("accountId") String accountId, @Body RequestBody logObject);

    @POST("agent/delegates/{delegateId}/state-executions")
    Call<RestResponse> saveApiCallLogs(
        @Path("delegateId") String delegateId, @Query("accountId") String accountId, @Body RequestBody logObject);
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
}

