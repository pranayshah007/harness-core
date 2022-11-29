/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.analysis.beans.TimeSeriesTestDataDTO;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/timeseries")
@Path("/timeseries")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class TimeSeriesRecordResource {
  @Inject private TimeSeriesRecordService timeSeriesRecordService;

  @GET
  @Path("/metric-template")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metric definition for a given data source config", nickname = "getMetricDefinitions")
  public RestResponse<List<TimeSeriesMetricDefinition>> getMetricDefinitions(
      @QueryParam("accountId") @NotNull String accountId, @QueryParam("cvConfigId") @NotNull String cvConfigId) {
    return new RestResponse<>(timeSeriesRecordService.getTimeSeriesMetricDefinitions(cvConfigId));
  }

  // TODO: rename params to startTime and endTime instead of startTimeEpochMillis
  // The convention is to always use epoch millis for APIs. If something else is used
  // we need to specify that in the API.
  @GET
  @Path("/metric-group-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get time series data for a given data source config", nickname = "getTimeSeriesData")
  public RestResponse<TimeSeriesTestDataDTO> getTimeSeriesData(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("cvConfigId") @NotNull String cvConfigId,
      @QueryParam("startTimeEpochMillis") @NotNull Long startTimeEpochMillis,
      @QueryParam("endTimeEpochMillis") @NotNull Long endTimeEpochMillis,
      @QueryParam("metricName") @NotNull String metricName,
      @QueryParam("groupNameList") @NotNull List<String> groupNameList) {
    return new RestResponse<>(
        timeSeriesRecordService.getMetricGroupDataForRange(cvConfigId, Instant.ofEpochMilli(startTimeEpochMillis),
            Instant.ofEpochMilli(endTimeEpochMillis), metricName, groupNameList));
  }

  @GET
  @Path("/anomalous-metric-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all anomalous metric timeseries data a given data source config",
      nickname = "getAnomalousMetricData")
  public RestResponse<TimeSeriesTestDataDTO>
  getAnomalousMetricData(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("cvConfigId") @NotNull String cvConfigId, @QueryParam("startTime") @NotNull Long startTimeEpochMillis,
      @QueryParam("endTime") @NotNull Long endTimeEpochMillis) {
    return new RestResponse<>(null);
  }
}
