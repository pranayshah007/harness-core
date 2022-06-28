package io.harness.perpetualtask;

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
