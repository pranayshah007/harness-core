package io.harness.managerclient;

import io.harness.perpetualtask.HeartbeatRequest;
import io.harness.perpetualtask.HeartbeatResponse;
import io.harness.perpetualtask.PerpetualTaskContextRequest;
import io.harness.perpetualtask.PerpetualTaskExecutionContext;
import io.harness.perpetualtask.PerpetualTaskListRequest;
import io.harness.perpetualtask.PerpetualTaskListResponse;
import io.harness.rest.RestResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PerpetualTaskManagerClient {
  @GET("agent/delegates/perpetual-task/list")
  Call<RestResponse<PerpetualTaskListResponse>> perpetualTaskList(
      @Query("perpetualTaskListRequest") PerpetualTaskListRequest perpetualTaskListRequest,
      @Query("accountId") String accountId);

  @GET("agent/delegates/perpetual-task/context")
  Call<RestResponse<PerpetualTaskExecutionContext>> perpetualTaskContext(
      @Query("perpetualTaskListRequest") PerpetualTaskContextRequest perpetualTaskContextRequest,
      @Query("accountId") String accountId);

  @GET("agent/delegates/perpetual-task/heartbeat")
  Call<RestResponse<HeartbeatResponse>> heartbeat(
      @Query("heartbeatRequest") HeartbeatRequest heartbeatRequest, @Query("accountId") String accountId);
}
