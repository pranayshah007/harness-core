/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.appdynamics;

import io.harness.cvng.beans.appd.AppDynamicsTier;

import software.wings.service.impl.appdynamics.AppdynamicsMetric;
import software.wings.service.impl.appdynamics.AppdynamicsMetricData;
import software.wings.service.impl.appdynamics.AppdynamicsNode;
import software.wings.service.impl.appdynamics.AppdynamicsTier;
import software.wings.service.impl.newrelic.NewRelicApplication;

import java.util.List;
import java.util.Set;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Created by rsingh on 4/17/17.
 */
public interface AppdynamicsRestClient {
  /**
   * Lists all the applications of appdynamics
   *
   * @param authorization the authorization
   * @return the call
   */
  @GET("rest/applications?output=json")
  Call<List<NewRelicApplication>> listAllApplications(@Header("Authorization") String authorization);

  /**
   * Lists all the tiers of an application in appdynamics
   *
   * @param authorization
   * @param appdynamicsAppId
   * @return
   */
  @GET("rest/applications/{appdynamicsAppId}/tiers?output=json")
  Call<Set<AppdynamicsTier>> listTiers(
      @Header("Authorization") String authorization, @Path("appdynamicsAppId") long appdynamicsAppId);

  @GET("rest/applications/{appdynamicsAppId}/tiers?output=json")
  Call<Set<AppDynamicsTier>> listTiersNg(
      @Header("Authorization") String authorization, @Path("appdynamicsAppId") long appdynamicsAppId);
  /**
   * Lists all the nodes of a tier and application in appdynamics
   *
   * @param authorization
   * @param appdynamicsAppId
   * @param tierId
   * @return
   */
  @GET("rest/applications/{appdynamicsAppId}/tiers/{tierId}/nodes?output=json")
  Call<List<AppdynamicsNode>> listNodes(@Header("Authorization") String authorization,
      @Path("appdynamicsAppId") long appdynamicsAppId, @Path("tierId") long tierId, @Query("hosts") List<String> hosts);

  /**
   * Get all the details of a tier
   *
   * @param authorization
   * @param appdynamicsAppId
   * @return
   */
  @GET("rest/applications/{appdynamicsAppId}/tiers/{tierId}?output=json")
  Call<List<AppdynamicsTier>> getTierDetails(@Header("Authorization") String authorization,
      @Path("appdynamicsAppId") long appdynamicsAppId, @Path("tierId") long tierId);

  /**
   * List the metrices of appdynamics application for a give path
   *
   * @param authorization the authorization
   * @param metricPath    the path to look for metrices
   * @return the call
   */
  @GET("rest/applications/{applicationId}/metrics?output=JSON")
  Call<List<AppdynamicsMetric>> listMetrices(@Header("Authorization") String authorization,
      @Path("applicationId") long applicationId, @Query("metric-path") String metricPath);

  /**
   * Get the metric data points of appdynamics application for a given path and time range
   *
   * @param authorization the authorization
   * @param metricPath    the path to look for metrics
   * @return the call
   */
  @GET("rest/applications/{applicationId}/metric-data?output=JSON&time-range-type=BETWEEN_TIMES")
  Call<List<AppdynamicsMetricData>> getMetricDataTimeRange(@Header("Authorization") String authorization,
      @Path("applicationId") long applicationId, @Query("metric-path") String metricPath,
      @Query("start-time") long startTime, @Query("end-time") long endTime, @Query("rollup") boolean rollup);

  @GET("rest/applications/{appName}/metric-data?output=JSON&time-range-type=BETWEEN_TIMES")
  Call<List<AppdynamicsMetricData>> getMetricDataTimeRange(@Header("Authorization") String authorization,
      @Path("appName") String appName, @Query("metric-path") String metricPath, @Query("start-time") long startTime,
      @Query("end-time") long endTime, @Query("rollup") boolean rollup);
}
