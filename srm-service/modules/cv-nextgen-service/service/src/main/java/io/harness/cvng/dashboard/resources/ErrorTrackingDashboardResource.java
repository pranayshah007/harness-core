/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.analysis.beans.LiveMonitoringLogAnalysisClusterDTO;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.filterParams.LiveMonitoringLogAnalysisFilter;
import io.harness.cvng.dashboard.beans.AnalyzedLogDataDTO;
import io.harness.cvng.dashboard.services.api.ErrorTrackingDashboardService;
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

@Api("error-tracking-dashboard")
@Path("/error-tracking-dashboard")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class ErrorTrackingDashboardResource {
  @Inject private ErrorTrackingDashboardService errorTrackingDashboardService;

  @GET
  @Path("/data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all error tracking data for a time range", nickname = "getAllErrorTrackingData")
  public RestResponse<PageResponse<AnalyzedLogDataDTO>> getAllErrorTrackingData(
      @NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @QueryParam("clusterTypes") List<LogAnalysisResult.LogAnalysisTag> clusterTypes,
      @NotNull @QueryParam("startTime") Long startTimeMillis, @NotNull @QueryParam("endTime") Long endTimeMillis,
      @QueryParam("healthSources") List<String> healthSourceIdentifiers,
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("10") int size) {
    MonitoredServiceParams serviceEnvironmentParams = MonitoredServiceParams.builder()
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
    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIdentifiers)
            .clusterTypes(clusterTypes)
            .build();

    return new RestResponse<>(errorTrackingDashboardService.getAllLogsData(
        serviceEnvironmentParams, timeRangeParams, liveMonitoringLogAnalysisFilter, pageParams));
  }

  @GET
  @Path("/cluster")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all log cluster data for a time range", nickname = "getAllErrorTrackingClusterData")
  public RestResponse<List<LiveMonitoringLogAnalysisClusterDTO>> getAllErrorTrackingClusterData(
      @NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @QueryParam("clusterTypes") List<LogAnalysisResult.LogAnalysisTag> clusterTypes,
      @NotNull @QueryParam("startTime") Long startTimeMillis, @NotNull @QueryParam("endTime") Long endTimeMillis,
      @QueryParam("healthSources") List<String> healthSourceIdentifiers) {
    MonitoredServiceParams serviceEnvironmentParams = MonitoredServiceParams.builder()
                                                          .accountIdentifier(projectParams.getAccountIdentifier())
                                                          .orgIdentifier(projectParams.getOrgIdentifier())
                                                          .projectIdentifier(projectParams.getProjectIdentifier())
                                                          .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                          .build();
    TimeRangeParams timeRangeParams = TimeRangeParams.builder()
                                          .startTime(Instant.ofEpochMilli(startTimeMillis))
                                          .endTime(Instant.ofEpochMilli(endTimeMillis))
                                          .build();
    LiveMonitoringLogAnalysisFilter liveMonitoringLogAnalysisFilter =
        LiveMonitoringLogAnalysisFilter.builder()
            .healthSourceIdentifiers(healthSourceIdentifiers)
            .clusterTypes(clusterTypes)
            .build();

    return new RestResponse<>(errorTrackingDashboardService.getLogAnalysisClusters(
        serviceEnvironmentParams, timeRangeParams, liveMonitoringLogAnalysisFilter));
  }
}
