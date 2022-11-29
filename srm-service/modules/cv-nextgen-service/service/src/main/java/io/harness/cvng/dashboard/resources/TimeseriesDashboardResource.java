/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.TimeSeriesAnalysisFilter;
import io.harness.cvng.dashboard.beans.TimeSeriesMetricDataDTO;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.ng.beans.PageResponse;
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
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("timeseries-dashboard")
@Path("/timeseries-dashboard")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class TimeseriesDashboardResource {
  @Inject TimeSeriesDashboardService timeSeriesDashboardService;

  @GET
  @Path("metrics")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all time series data in a given time range", nickname = "getTimeSeriesMetricData")
  public RestResponse<PageResponse<TimeSeriesMetricDataDTO>> getTimeSeriesMetricData(
      @BeanParam ProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @NotNull @QueryParam("startTime") Long startTimeMillis, @NotNull @QueryParam("endTime") Long endTimeMillis,
      @QueryParam("anomalous") @DefaultValue("false") boolean anomalous, @QueryParam("filter") String filter,
      @QueryParam("healthSources") List<String> healthSourceIdentifiers,
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("10") int size) {
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(projectParams.getAccountIdentifier())
                                                        .orgIdentifier(projectParams.getOrgIdentifier())
                                                        .projectIdentifier(projectParams.getProjectIdentifier())
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .build();
    TimeRangeParams timeRangeParams = TimeRangeParams.builder()
                                          .startTime(Instant.ofEpochMilli(startTimeMillis))
                                          .endTime(Instant.ofEpochMilli(endTimeMillis))
                                          .build();
    PageParams pageParams = PageParams.builder().page(page).size(size).build();
    TimeSeriesAnalysisFilter timeSeriesAnalysisFilter = TimeSeriesAnalysisFilter.builder()
                                                            .filter(filter)
                                                            .anomalousMetricsOnly(anomalous)
                                                            .healthSourceIdentifiers(healthSourceIdentifiers)
                                                            .build();

    return new RestResponse<>(timeSeriesDashboardService.getTimeSeriesMetricData(
        monitoredServiceParams, timeRangeParams, timeSeriesAnalysisFilter, pageParams));
  }
}
