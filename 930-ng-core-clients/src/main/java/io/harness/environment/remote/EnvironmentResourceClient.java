/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.environment.remote;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;

import java.util.List;
import java.util.Map;
import javax.ws.rs.DefaultValue;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(PIPELINE)
public interface EnvironmentResourceClient {
  String ENVIRONMENT_API = "environmentsV2";

  @GET(ENVIRONMENT_API)
  Call<ResponseDTO<PageResponse<EnvironmentResponse>>> listEnvironmentsForProject(
      @Query("page") @DefaultValue("0") int page, @Query("size") @DefaultValue("100") int size,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("envIdentifiers") List<String> envIdentifiers, @Query("sort") List<String> sort);

  @GET(ENVIRONMENT_API + "/attributes")
  Call<ResponseDTO<List<Map<String, String>>>> getEnvironmentsAttributes(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("envIdentifiers") List<String> envIdentifiers);
}
