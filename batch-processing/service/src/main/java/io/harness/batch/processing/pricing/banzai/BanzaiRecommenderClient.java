/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.banzai;

import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface BanzaiRecommenderClient {
  @GET("status") Call<String> checkServiceStatus(); // success response is "ok"

  @POST("api/v1/recommender/provider/{provider}/service/{service}/region/{region}/cluster")
  Call<RecommendationResponse> getRecommendation(@Path("provider") String provider, @Path("service") String service,
      @Path("region") String region, @Body RecommendClusterRequest request);
}
