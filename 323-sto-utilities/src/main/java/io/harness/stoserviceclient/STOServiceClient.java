/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stoserviceclient;

import io.harness.common.STOCommonEndpointConstants;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface STOServiceClient {
  @GET(STOCommonEndpointConstants.STO_SERVICE_TOKEN_ENDPOINT)
  Call<String> generateToken(@Query("accountId") String accountId, @Header("X-Harness-Token") String globalToken);
}
