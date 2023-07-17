/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.analysis.entities.SRMAnalysisStepDetailDTO;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

@Api("srm-analysis-step")
@Path("/srm-analysis-step")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class SRMAnalysisStepResource {
  @Inject SRMAnalysisStepService srmAnalysisStepService;
  @GET
  @Path("/{activityId}/analysis-summary")
  @ApiOperation(value = "get summary of srm analysis activity", nickname = "getSRMAnalysisSummary")
  public RestResponse<SRMAnalysisStepDetailDTO> getSRMAnalysisSummary(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotNull @PathParam("activityId") String activityId) {
    return new RestResponse(srmAnalysisStepService.getSRMAnalysisSummary(activityId));
  }

  @PUT
  @Path("/{executionDetailId}/stop-analysis")
  @ApiOperation(value = "stop srm analysis step", nickname = "stopSRMAnalysisStep")
  public RestResponse<SRMAnalysisStepDetailDTO> stopSRMAnalysisStep(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotNull @PathParam("executionDetailId") String executionDetailId) {
    return new RestResponse(srmAnalysisStepService.abortRunningSrmAnalysisStep(executionDetailId));
  }
}
