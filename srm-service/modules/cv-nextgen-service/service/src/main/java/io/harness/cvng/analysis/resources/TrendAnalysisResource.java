/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.resources;

import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_SAVE_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_ANALYSIS_TEST_DATA;
import static io.harness.cvng.analysis.CVAnalysisConstants.TREND_METRIC_TEMPLATE;

import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@Api(TREND_ANALYSIS_RESOURCE)
@Path(TREND_ANALYSIS_RESOURCE)
@Produces("application/json")
@Slf4j
public class TrendAnalysisResource {
  @Inject private TrendAnalysisService trendAnalysisService;

  @GET
  @Path("/" + TREND_ANALYSIS_TEST_DATA)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "save time series data for trend analysis", nickname = "getTestDataForTrend")
  public RestResponse<List<TimeSeriesRecordDTO>> getTestData(
      @NotNull @QueryParam("verificationTaskId") String verificationTaskId,
      @NotNull @QueryParam("analysisStartTime") String epochStartInstant,
      @NotNull @QueryParam("analysisEndTime") String epochEndInstant) {
    return new RestResponse<>(trendAnalysisService.getTestData(
        verificationTaskId, Instant.parse(epochStartInstant), Instant.parse(epochEndInstant)));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/" + TREND_ANALYSIS_SAVE_PATH)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "save time series analysis", nickname = "saveServiceGuardTrendAnalysis")
  public RestResponse<Void> saveServiceGuardAnalysis(
      @NotNull @QueryParam("taskId") String taskId, ServiceGuardTimeSeriesAnalysisDTO analysisBody) {
    trendAnalysisService.saveAnalysis(taskId, analysisBody);
    return new RestResponse<>(null);
  }

  @GET
  @Path("/" + TREND_METRIC_TEMPLATE)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get metric template for analysis", nickname = "getMetricTimeSeriesTemplateForTrend")
  public RestResponse<List<TimeSeriesMetricDefinition>> getMetricTemplate() {
    return new RestResponse<>(trendAnalysisService.getTimeSeriesMetricDefinitions());
  }
}
