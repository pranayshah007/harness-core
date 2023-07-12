package io.harness.dmsclient;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.serializer.kryo.KryoResponse;

import retrofit2.Call;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface DelegateAgentDMSClient {
  @KryoResponse
  @PUT("agent/delegates/dms/{delegateId}/tasks/{taskId}/acquire/v2")
  Call<DelegateTaskPackage> acquireTask(@Path("delegateId") String delegateId, @Path("taskId") String uuid,
      @Query("accountId") String accountId, @Query("delegateInstanceId") String delegateInstanceId);
}
