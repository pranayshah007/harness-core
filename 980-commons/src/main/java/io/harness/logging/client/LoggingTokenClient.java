/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.client;

import io.harness.logging.common.AccessTokenBean;
import io.harness.rest.RestResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LoggingTokenClient {
  @GET("agent/infra-download/delegate-auth/delegate/logging-token")
  Call<RestResponse<AccessTokenBean>> getLoggingToken(@Query("accountId") String accountId);
  @GET("agent/delegates/logging-token")
  Call<RestResponse<AccessTokenBean>> getLoggingServiceToken(@Query("accountId") String accountId);
}
