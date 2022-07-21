/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.docker.helper;

import io.harness.delegate.beans.ci.docker.DestroyDockerRequest;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerRequest;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerResponse;
import io.harness.delegate.beans.ci.docker.PoolOwnerStepResponse;
import io.harness.delegate.beans.ci.docker.SetupDockerRequest;
import io.harness.delegate.beans.ci.docker.SetupDockerResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RunnerRestClient {
  @POST("pool_owner")
  @Headers("Accept: application/json")
  Call<PoolOwnerStepResponse> poolOwner(@Query("pool") String pool, @Query("stageId") String stageId);

  @POST("setup") @Headers("Accept: application/json") Call<SetupDockerResponse> setup(@Body SetupDockerRequest setupDockerRequest);

  @POST("step")
  @Headers("Accept: application/json")
  Call<ExecuteStepDockerResponse> step(@Body ExecuteStepDockerRequest executeStepRequest);

  @POST("destroy") @Headers("Accept: application/json") Call<Void> destroy(@Body DestroyDockerRequest destroyDockerRequest);
}
