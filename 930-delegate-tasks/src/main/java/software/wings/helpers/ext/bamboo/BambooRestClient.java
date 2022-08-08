/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.bamboo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

/**
 * Created by anubhaw on 11/29/16.
 */
@OwnedBy(CDC)
public interface BambooRestClient {
  /**
   * List projects with job details call.
   *
   * @param authorization the authorization
   * @param planKey       the plan key
   * @return the call
   */
  @GET("rest/api/latest/plan/{planKey}.json?authType=basic&expand=stages.stage.plans.plan&max-results=10000")
  Call<JsonNode> listPlanWithJobDetails(@Header("Authorization") String authorization, @Path("planKey") String planKey);

  /**
   * List project plans call.
   *
   * @param authorization the authorization
   * @return the call
   */
  @GET("rest/api/latest/plan.json?authType=basic")
  Call<JsonNode> listProjectPlans(@Header("Authorization") String authorization, @Query("max-results") int maxResults);

  /**
   * List project plans call with pagination.
   *
   * @param authorization the authorization
   * @return the call
   */
  @GET("rest/api/latest/plan.json?authType=basic")
  Call<JsonNode> listProjectPlansWithPagination(@Header("Authorization") String authorization,
      @Query("max-results") int maxResults, @Query("start-index") int startIndex);

  /**
   * Last successful build call.
   *
   * @param authorization the authorization
   * @param planKey       the plan key
   * @return the call
   */
  @GET(
      "rest/api/latest/result/{planKey}.json?authType=basic&buildstate=Successful&max-results=1&expand=results.result.artifacts.artifact")
  Call<JsonNode>
  lastSuccessfulBuildForJob(@Header("Authorization") String authorization, @Path("planKey") String planKey);

  /**
   * List builds for job call.
   *
   * @param authorization the authorization
   * @param planKey       the plan key
   * @param maxResult     the max result
   * @return the call
   */
  @GET("rest/api/latest/result/{planKey}.json?authType=basic&buildstate=Successful&expand=results.result")
  Call<JsonNode> listBuildsForJob(@Header("Authorization") String authorization, @Path("planKey") String planKey,
      @Query("max-result") int maxResult);

  /**
   * Gets build artifacts.
   *
   * @param authorization the authorization
   * @param planKey       the plan key
   * @param buildNo       the build no
   * @return the build artifacts
   */
  @GET(
      "rest/api/latest/result/{planKey}-{buildNo}.json?authType=basic&expand=stages.stage.results.result.artifacts.artifact")
  Call<JsonNode>
  getBuildArtifacts(
      @Header("Authorization") String authorization, @Path("planKey") String planKey, @Path("buildNo") String buildNo);

  /**
   * Download artifact call.
   *
   * @param authorization the authorization
   * @param fileUrl       the file url
   * @return the call
   */
  @GET Call<ResponseBody> downloadArtifact(@Header("Authorization") String authorization, @Url String fileUrl);

  /**
   * Gets the running build status
   * @param authorization
   * @return
   */
  @GET("rest/api/latest/result/status/{buildResultKey}.json?authType=basic")
  Call<Status> getBuildResultStatus(
      @Header("Authorization") String authorization, @Path("buildResultKey") String buildResultKey);

  /**
   * Gets the completed build status
   * @param authorization
   * @return
   */
  @GET("rest/api/latest/result/{buildResultKey}.json?authType=basic")
  Call<Result> getBuildResult(
      @Header("Authorization") String authorization, @Path("buildResultKey") String buildResultKey);

  /***
   * Triggers Plan
   * @param authorization
   * @param planKey
   * @return
   */
  @Headers("Accept: application/json")
  @POST("rest/api/latest/queue/{planKey}?authtype=basic&stage&executeAllStages")
  Call<JsonNode> triggerPlan(@Header("Authorization") String authorization, @Path("planKey") String planKey,
      @QueryMap Map<String, String> parameters);
}
