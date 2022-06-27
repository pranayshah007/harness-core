/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logserviceclient;

import io.harness.common.CICommonEndpointConstants;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface CILogServiceClient {
  @GET(CICommonEndpointConstants.LOG_SERVICE_TOKEN_ENDPOINT)
  Call<String> generateToken(@Query("accountID") String accountId, @Header("X-Harness-Token") String globalToken);

  @DELETE(CICommonEndpointConstants.LOG_SERVICE_STREAM_ENDPOINT)
  Call<Void> closeLogStream(@Query("accountID") String accountId, @Query("key") String logKey,
      @Query("snapshot") boolean snapshot, @Query("prefix") boolean prefix,
      @Header("X-Harness-Token") String authToken);
}
