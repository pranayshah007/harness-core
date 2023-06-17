/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.chaos.client.remote;

import io.harness.chaos.client.beans.ChaosApplyManifestResponse;
import io.harness.chaos.client.beans.ChaosQuery;
import io.harness.chaos.client.beans.ChaosRerunResponse;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ChaosHttpClient {
  @POST("kserver/api/ng/delegateCallback")
  Call<ResponseDTO<Boolean>> pushTaskResponse(@Body ChaosApplyManifestResponse response);

  @POST("manager/api/query") Call<ResponseDTO<ChaosRerunResponse>> reRunWorkflow(@Body ChaosQuery query);
}
