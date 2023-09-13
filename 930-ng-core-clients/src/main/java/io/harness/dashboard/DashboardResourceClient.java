/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.dashboard;

import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DashboardResourceClient {
  String DASHBOARD_API = "dashboard";

  @GET(DASHBOARD_API + "/getDeploymentsByServiceId")
  Call<ResponseDTO<Optional<DeploymentsInfo>>> getDeploymentsByServiceId(
      @NotEmpty @Query("accountIdentifier") String accountIdentifier, @Query("orgIdentifier") String orgIdentifier,
      @Query("projectIdentifier") String projectIdentifier, @Query("serviceId") String serviceId,
      @Query("startTime") long startInterval, @Query("endTime") long endInterval);
}
