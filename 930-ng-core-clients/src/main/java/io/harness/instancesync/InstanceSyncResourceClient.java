/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.instancesync;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.perpetualtask.instancesync.InstanceSyncTaskDetails;

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.DX)
public interface InstanceSyncResourceClient {
  String INSTANCE_SYNC = "instancesync";

  @POST(INSTANCE_SYNC + "/response")
  Call<ResponseDTO<Boolean>> sendPerpetualTaskResponse(
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotEmpty @Query(NGCommonEntityConstants.PERPETUAL_TASK_ID) String perpetualTaskId,
      @NotNull @Body DelegateResponseData instanceSyncPerpetualTaskResponse);

  @GET(INSTANCE_SYNC + "/task/{perpetualTaskId}/details")
  Call<ResponseDTO<InstanceSyncTaskDetails>> getInstanceSyncTaskDetails(
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotEmpty @Path(NGCommonEntityConstants.PERPETUAL_TASK_ID) String perpetualTaskId);

  @POST(INSTANCE_SYNC + "/v2/response")
  Call<ResponseDTO<Boolean>> sendPerpetualTaskResponseV2(
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotEmpty @Query(NGCommonEntityConstants.PERPETUAL_TASK_ID) String perpetualTaskId,
      @NotNull @Body DelegateResponseData instanceSyncPerpetualTaskResponse);
}
