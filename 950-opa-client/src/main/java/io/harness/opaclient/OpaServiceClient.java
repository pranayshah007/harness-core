/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.opaclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaShouldEvaluateResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public interface OpaServiceClient {
  String API_PREFIX = "api/v1/";

  @POST(API_PREFIX + "evaluate-by-type")
  Call<OpaEvaluationResponseHolder> evaluateWithCredentials(@Query("type") String type,
      @Query("accountIdentifier") String accountIdentifier, @Query("orgIdentifier") String orgIdentifier,
      @Query("projectIdentifier") String projectIdentifier, @Query("action") String action,
      @Query("entity") String entity, @Query("entityMetadata") String entityMetadata,
      @Query("userIdentifier") String userIdentifier, @Body Object context);

  @POST(API_PREFIX + "evaluate-by-ids")
  Call<OpaEvaluationResponseHolder> evaluateWithCredentialsByID(@Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgId, @Query("projectIdentifier") String projId, @Query("ids") String policySets,
      @Query("entityMetadata") String entityMetadata, @Body Object context);

  @GET(API_PREFIX + "evaluate-by-type-check")
  Call<OpaShouldEvaluateResponse> shouldEvaluateByTypeAndAction(@Query("accountIdentifier") String accountId,
      @Query("orgIdentifier") String orgId, @Query("projectIdentifier") String projId, @Query("type") String type,
      @Query("action") String action);
}
