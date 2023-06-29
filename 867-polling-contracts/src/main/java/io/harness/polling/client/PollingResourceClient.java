/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.dto.PollingResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;

import javax.ws.rs.QueryParam;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)
public interface PollingResourceClient {
  String POLLING_API = "polling";

  @POST(POLLING_API + "/delegate-response/{perpetualTaskId}")
  Call<ResponseDTO> processPolledResult(@Path("perpetualTaskId") String perpetualTaskId,
      @Query("accountId") String accountId, @Body RequestBody buildSourceExecutionResponse);

  @POST(POLLING_API + "/subscribe") Call<ResponseDTO<PollingResponseDTO>> subscribe(@Body RequestBody pollingItem);
  @GET(POLLING_API + "/polling-info-for-triggers")
  Call<ResponseDTO<PollingInfoForTriggers>> getPollingInfoForTriggers(
      @Query("accountId") String accountId, @QueryParam("pollingDocId") String pollingDocId);

  @POST(POLLING_API + "/unsubscribe") Call<Boolean> unsubscribe(@Body RequestBody pollingItem);
}
