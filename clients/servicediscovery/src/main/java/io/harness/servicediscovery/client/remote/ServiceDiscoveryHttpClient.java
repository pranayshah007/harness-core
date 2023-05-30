/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicediscovery.client.remote;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryApplyManifestResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ServiceDiscoveryHttpClient {
  @POST("api/v1/delegateCallback")
  Call<ResponseDTO<Boolean>> pushTaskResponse(@Body ServiceDiscoveryApplyManifestResponse response);
}
