/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.DEL)
public interface LogServiceStackdriverClient {
  @POST("stackdriver")
  Call<Void> sendLogs(@Header("X-Harness-Token") String authToken, @Query("accountID") String accountId,
      @Query("key") String logKey, @Body List<StackdriverLogLine> logs);

  @GET("stackdriver") Call<Boolean> pingStackdriver(@Header("X-Harness-Token") String authToken);
}
