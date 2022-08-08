/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.SLODebugResponse;
import io.harness.cvng.core.beans.VerifyStepDebugResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.services.api.DebugService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@Api("debug")
@Path("debug")
@Produces("application/json")
@OwnedBy(HarnessTeam.CV)
@NextGenManagerAuth
public class DebugResource {
  @Inject DebugService debugService;

  @GET
  @Timed
  @Path("slo/{identifier}")
  @ApiOperation(value = "Gets SLO debug data", nickname = "getSLODebugData")
  public RestResponse<SLODebugResponse> getSLODebug(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.getSLODebugResponse(projectParams, identifier));
  }

  @GET
  @Timed
  @Path("verify-step/{identifier}")
  @ApiOperation(value = "Gets Verify Step debug data", nickname = "getVerifyStepDebugData")
  public RestResponse<VerifyStepDebugResponse> getVerifyStepDebug(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.getVerifyStepDebugResponse(projectParams, identifier));
  }

  @PUT
  @Timed
  @Path("datacollectiontask/{identifier}/retry")
  @ApiOperation(value = "Updates DataCollectionTask for Debugging", nickname = "updateDataCollectionTaskDebugData")
  public RestResponse<DataCollectionTask> updateDataCollectionTaskDebug(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.retryDataCollectionTask(projectParams, identifier));
  }
}
