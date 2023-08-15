/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.client;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.triggers.PerpetualTaskInfoForTriggers;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PerpetualTaskResourceClient {
  String PERPETUAL_TASK_API = "/api/agent/delegates/perpetual-task";
  @GET(PERPETUAL_TASK_API + "/perpetual-task-info-for-triggers")
  Call<ResponseDTO<PerpetualTaskInfoForTriggers>> getPerpetualTaskInfoForTriggers(
      @Query(value = "perpetualTaskId") String perpetualTaskId, @Query(value = "accountId") String accountId);
}
