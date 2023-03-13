/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.params.ProjectScopedProjectParams;
import io.harness.cvng.core.beans.sli.MetricOnboardingGraph;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.CorrelationContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("monitored-service/sli")
@Path("monitored-service/{monitoredServiceIdentifier}/sli")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelIndicatorResource {
  @Inject ServiceLevelIndicatorService sliService;

  @POST
  @Timed
  @ExceptionMetered
  @Path("/onboarding-graph")
  @ApiOperation(value = "get Sli graph for onboarding UI", nickname = "getSliGraph")
  @Deprecated
  public RestResponse<TimeGraphResponse> getGraph(@Valid @BeanParam ProjectScopedProjectParams projectParams,
      @PathParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @NotNull @Valid @Body ServiceLevelIndicatorDTO serviceLevelIndicatorDTO) {
    return new RestResponse<>(sliService
                                  .getOnboardingGraphs(projectParams.getProjectParams(), monitoredServiceIdentifier,
                                      serviceLevelIndicatorDTO, CorrelationContext.getCorrelationId())
                                  .getSliGraph());
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("/onboarding-graphs")
  @ApiOperation(value = "get Sli and metric graphs for onboarding UI", nickname = "getSliOnboardingGraphs")
  public RestResponse<SLIOnboardingGraphs> getGraphs(@BeanParam @Valid ProjectScopedProjectParams projectParams,
      @PathParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @NotNull @Valid @Body ServiceLevelIndicatorDTO serviceLevelIndicatorDTO) {
    return new RestResponse<>(sliService.getOnboardingGraphs(projectParams.getProjectParams(),
        monitoredServiceIdentifier, serviceLevelIndicatorDTO, CorrelationContext.getCorrelationId()));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("/onboarding-metric-graphs")
  @ApiOperation(value = "get metric graphs for onboarding UI", nickname = "getMetricOnboardingGraph")
  public RestResponse<MetricOnboardingGraph> getMetricGraphs(@BeanParam @Valid ProjectScopedProjectParams projectParams,
      @PathParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @NotNull @QueryParam("healthSourceRef") String healthSourceRef,
      @QueryParam("ratioSLIMetricEventType") RatioSLIMetricEventType ratioSLIMetricEventType,
      @NotNull @Size(min = 1, max = 2) @Valid @Body List<String> metricIdentifiers) {
    if (metricIdentifiers.size() == 2 && ratioSLIMetricEventType == null) {
      throw new InvalidArgumentsException("Event type needs to be present for ratio based metric");
    }
    return new RestResponse<>(sliService.getMetricGraphs(projectParams.getProjectParams(), monitoredServiceIdentifier,
        healthSourceRef, ratioSLIMetricEventType, metricIdentifiers, CorrelationContext.getCorrelationId()));
  }
}
