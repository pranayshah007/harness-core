/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pipeline.dashboards;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.dashboards.LandingDashboardRequestPMS;
import io.harness.pms.dashboards.PipelinesCount;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(PIPELINE)
public interface PMSLandingDashboardResourceClient {
  String LANDING_DASHBOARDS_ENDPOINT = "landingDashboards";

  @POST(LANDING_DASHBOARDS_ENDPOINT + "/pipelinesCount")
  Call<ResponseDTO<PipelinesCount>> getPipelinesCount(
      @NotNull @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @Query(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @Query(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull @Body LandingDashboardRequestPMS landingDashboardRequestPMS);
}
