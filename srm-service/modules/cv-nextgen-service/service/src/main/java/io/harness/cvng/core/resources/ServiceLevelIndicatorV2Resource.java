/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.ng.core.CorrelationContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import retrofit2.http.Body;

@Api("sli")
@Path("sli")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelIndicatorV2Resource {
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

  @POST
  @Timed
  @ExceptionMetered
  @Path("/onboarding-graph")
  @ApiOperation(value = "get Sli graph for onboarding UI", nickname = "v2GetSliGraph")
  @Deprecated
  public RestResponse<TimeGraphResponse> getGraph(@Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @Valid @Body SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec) {
    return new RestResponse<>(serviceLevelIndicatorService
                                  .getOnboardingGraphs(projectParams.getProjectParams(),
                                      simpleServiceLevelObjectiveSpec, CorrelationContext.getCorrelationId())
                                  .getSliGraph());
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("/onboarding-graphs")
  @ApiOperation(value = "get Sli and metric graphs for onboarding UI", nickname = "v2GetSliOnboardingGraphs")
  public RestResponse<SLIOnboardingGraphs> getGraphs(@BeanParam @Valid ProjectScopedProjectParams projectParams,
      @NotNull @Valid @Body SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec) {
    return new RestResponse<>(serviceLevelIndicatorService.getOnboardingGraphs(
        projectParams.getProjectParams(), simpleServiceLevelObjectiveSpec, CorrelationContext.getCorrelationId()));
  }
}
