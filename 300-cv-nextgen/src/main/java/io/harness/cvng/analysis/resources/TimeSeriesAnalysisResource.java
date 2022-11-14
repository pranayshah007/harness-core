/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.resources;

import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SAVE_ANALYSIS_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_VERIFICATION_TASK_SAVE_ANALYSIS_PATH;

import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Api(TIMESERIES_ANALYSIS_RESOURCE)
@Path(TIMESERIES_ANALYSIS_RESOURCE)
@Produces("application/json")
@Slf4j
public class TimeSeriesAnalysisResource {
  @Inject TimeSeriesAnalysisService timeSeriesAnalysisService;

  @GET
  @Path("/time-series-data")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(
      value = "get test timeseries data for a verification job and risk analysis", nickname = "getTimeSeriesRecords")
  public RestResponse<List<TimeSeriesRecordDTO>>
  getTimeSeriesRecords(@QueryParam("verificationTaskId") @NotNull String verificationTaskId,
      @QueryParam("startTime") @NotNull Long startTime, @QueryParam("endTime") @NotNull Long endTime,
      @QueryParam("hosts") String commaSeparatedHosts) {
    return new RestResponse<>(timeSeriesAnalysisService.getDeploymentMetricTimeSeriesRecordDTOs(
        verificationTaskId, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), commaSeparatedHosts));
  }

  @GET
  @Path("/timeseries-serviceguard-cumulative-sums")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get risk analysis cumulative sums", nickname = "getCumulativeSums")
  public RestResponse<Map<String, Map<String, List<MetricSumDTO>>>> getCumulativeSums(
      @QueryParam("verificationTaskId") String verificationTaskId,
      @QueryParam("analysisStartTime") String epochStartInstant,
      @QueryParam("analysisEndTime") String epochEndInstant) {
    return new RestResponse<>(timeSeriesAnalysisService.getCumulativeSums(
        verificationTaskId, Instant.parse(epochStartInstant), Instant.parse(epochEndInstant)));
  }

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/timeseries-serviceguard-previous-anomalies")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "get previous anomalies for a data source config", nickname = "getPreviousAnomalies")
  public RestResponse<Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>>> getPreviousAnomalies(
      @QueryParam("verificationTaskId") String verificationTaskId) {
    return new RestResponse<>(timeSeriesAnalysisService.getLongTermAnomalies(verificationTaskId));
  }

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/timeseries-serviceguard-shortterm-history")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "get short term history for a data source config", nickname = "getShortTermHistory")
  public RestResponse<Map<String, Map<String, List<Double>>>> getShortTermHistory(
      @QueryParam("verificationTaskId") String verificationTaskId) {
    return new RestResponse<>(timeSeriesAnalysisService.getShortTermHistory(verificationTaskId));
  }

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/timeseries-serviceguard-metric-template")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "get metric template for a verification job", nickname = "getMetricTimeSeriesTemplate")
  public RestResponse<List<TimeSeriesMetricDefinition>> getMetricTemplate(
      @QueryParam("verificationTaskId") String verificationTaskId) {
    return new RestResponse<>(timeSeriesAnalysisService.getMetricTemplate(verificationTaskId));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(TIMESERIES_SAVE_ANALYSIS_PATH)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(
      value = "save time series analysis for a data source config", nickname = "saveServiceGuardTimeseriesAnalysis")
  public RestResponse<Boolean>
  saveServiceGuardAnalysis(@QueryParam("taskId") String taskId, ServiceGuardTimeSeriesAnalysisDTO analysisBody) {
    timeSeriesAnalysisService.saveAnalysis(taskId, analysisBody);
    return new RestResponse<>(true);
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path(TIMESERIES_VERIFICATION_TASK_SAVE_ANALYSIS_PATH)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(
      value = "save time series analysis for a deployment verification", nickname = "saveVerificationTaskAnalysis")
  public RestResponse<Void>
  saveVerificationTaskAnalysis(@QueryParam("taskId") String taskId, @QueryParam("endTime") long endTime,
      DeploymentTimeSeriesAnalysisDTO analysisBody) {
    timeSeriesAnalysisService.saveAnalysis(taskId, analysisBody);
    return new RestResponse<>(null);
  }
}
