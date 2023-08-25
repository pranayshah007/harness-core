/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pipeline.dashboards;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.ng.core.dto.ResponseDTO;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PMSDashboardResourceClient {
  String DASHBOARD_ENDPOINT = "dashboard";

  @GET(DASHBOARD_ENDPOINT + "/pipelineHealth")
  Call<ResponseDTO<Object>> fetchPipelinedHealth(
      @NotNull @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @Query(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @Query(NGCommonEntityConstants.MODULE_TYPE) String moduleInfo,
      @NotNull @Query(NGResourceFilterConstants.START_TIME) long startTime,
      @NotNull @Query(NGResourceFilterConstants.END_TIME) long endTime);
}
