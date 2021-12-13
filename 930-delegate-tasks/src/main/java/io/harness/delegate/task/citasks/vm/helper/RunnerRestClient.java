package io.harness.delegate.task.citasks.vm.helper;

import io.harness.delegate.beans.ci.vm.runner.DestroyVmRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepResponse;
import io.harness.delegate.beans.ci.vm.runner.PoolOwnerStepResponse;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RunnerRestClient {
  @POST("pool_owner")
  @Headers("Accept: application/json")
  Call<PoolOwnerStepResponse> poolOwner(@Query("pool") String pool);

  @POST("setup") @Headers("Accept: application/json") Call<SetupVmResponse> setup(@Body SetupVmRequest setupVmRequest);

  @POST("step")
  @Headers("Accept: application/json")
  Call<ExecuteStepResponse> step(@Body ExecuteStepRequest executeStepRequest);

  @POST("destroy") @Headers("Accept: application/json") Call<Void> destroy(@Body DestroyVmRequest destroyVmRequest);
}
