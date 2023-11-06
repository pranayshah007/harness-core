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
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.core.beans.CompositeSLODebugResponse;
import io.harness.cvng.core.beans.ProjectDeletionResponse;
import io.harness.cvng.core.beans.SLODebugResponse;
import io.harness.cvng.core.beans.VerifyStepDebugResponse;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectScopedProjectParams;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.jobs.FakeFeatureFlagSRMProducer;
import io.harness.cvng.core.services.api.DebugService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

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
  @ApiOperation(value = "Gets SLO debug data", nickname = "getSLODebugData", hidden = true)
  public RestResponse<SLODebugResponse> getSLODebug(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.getSLODebugResponse(projectParams.getProjectParams(), identifier));
  }

  @GET
  @Timed
  @Path("isProjectDeleted")
  @ApiOperation(
      value = "Checks whether Project resources are deleted", nickname = "isProjectResourcesDeleted", hidden = true)
  public RestResponse<ProjectDeletionResponse>
  isProjectDeleted(@NotNull @BeanParam ProjectScopedProjectParams projectParams) {
    return new RestResponse<>(debugService.isProjectDeleted(projectParams.getProjectParams()));
  }

  @GET
  @Timed
  @Path("isSLODeleted/{identifier}")
  @ApiOperation(value = "Checks whether SLO resources are deleted", nickname = "isSLOResourcesDeleted", hidden = true)
  public RestResponse<Boolean> isSLODeleted(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.isSLODeleted(projectParams.getProjectParams(), identifier));
  }

  @GET
  @Timed
  @Path("schedule-cleanup")
  @ApiOperation(
      value = "Schedule sidekick jobs for cleanup of verificationTasks", nickname = "scheduleCleanup", hidden = true)
  public void
  scheduleCleanup(@NotNull @QueryParam("identifiers") List<String> verificationTaskIds) {
    debugService.scheduleCleanup(verificationTaskIds);
  }

  @GET
  @Timed
  @Path("isSLIDeleted/{identifier}")
  @ApiOperation(value = "Checks whether SLI resources are deleted", nickname = "isSLIResourcesDeleted", hidden = true)
  public RestResponse<Boolean> isSLIDeleted(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.isSLIDeleted(projectParams.getProjectParams(), identifier));
  }

  @DELETE
  @Timed
  @Path("slo")
  @ApiOperation(value = "Force deletes SLOs and associated entities", nickname = "forceDeleteSLO", hidden = true)
  public RestResponse<Boolean> forceDeleteSLO(@BeanParam ProjectParams projectParams,
      @QueryParam("identifiers") List<String> identifiers,
      @QueryParam("uniqueIdentifiers") List<String> uniqueIdentifiers) {
    return new RestResponse<>(debugService.forceDeleteSLO(projectParams, identifiers, uniqueIdentifiers));
  }

  @DELETE
  @Timed
  @Path("sli")
  @ApiOperation(value = "Force deletes SLIs and associated entities", nickname = "forceDeleteSLI", hidden = true)
  public RestResponse<Boolean> forceDeleteSLI(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @Size(min = 1) @Valid @QueryParam("identifiers") List<String> identifiers) {
    return new RestResponse<>(debugService.forceDeleteSLI(projectParams.getProjectParams(), identifiers));
  }

  @DELETE
  @Timed
  @Path("composite-slo")
  @ApiOperation(value = "Force deletes composite SLOs and associated entities", nickname = "forceDeleteCompositeSLO",
      hidden = true)
  public RestResponse<Boolean>
  forceDeleteCompositeSLO(@BeanParam ProjectParams projectParams, @QueryParam("identifiers") List<String> identifiers,
      @QueryParam("uniqueIdentifiers") List<String> uniqueIdentifiers) {
    return new RestResponse<>(debugService.forceDeleteCompositeSLO(projectParams, identifiers, uniqueIdentifiers));
  }

  @GET
  @Timed
  @Path("composite-slo/{identifier}")
  @ApiOperation(value = "Gets Composite SLO debug data", nickname = "getCompositeSLODebugData", hidden = true)
  public RestResponse<CompositeSLODebugResponse> getCompositeSLODebug(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.getCompositeSLODebugResponse(projectParams, identifier));
  }

  @GET
  @Timed
  @Path("verify-step/{identifier}")
  @ApiOperation(value = "Gets Verify Step debug data", nickname = "getVerifyStepDebugData", hidden = true)
  public RestResponse<VerifyStepDebugResponse> getVerifyStepDebug(
      @NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.getVerifyStepDebugResponse(projectParams.getProjectParams(), identifier));
  }

  @PUT
  @Timed
  @Path("datacollectiontask/{identifier}/retry")
  @ApiOperation(
      value = "Updates DataCollectionTask for Debugging", nickname = "updateDataCollectionTaskDebugData", hidden = true)
  public RestResponse<DataCollectionTask>
  updateDataCollectionTaskDebug(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(debugService.retryDataCollectionTask(projectParams.getProjectParams(), identifier));
  }

  @PUT
  @Timed
  @Path("slo/{identifier}/failed")
  @ApiOperation(value = "Updates failed state of SLO for automation", nickname = "markSLOAsFailed", hidden = true)
  public void markSLOAsFailed(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @QueryParam("enable") Boolean enable) {
    debugService.updateFailedStateOfSLO(projectParams.getProjectParams(), identifier, enable);
  }

  @PUT
  @Timed
  @Path("slo/{identifier}/dataCollectionFailures")
  @ApiOperation(value = "enqueue DataCollection Failures for automation", nickname = "enqueueDataCollectionFailure",
      hidden = true)
  public void
  enqueueDataCollectionFailure(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @QueryParam("startTime") Long startTime, @NotNull @Valid @QueryParam("endTime") Long endTime) {
    debugService.enqueueDataCollectionFailure(projectParams.getProjectParams(), identifier, startTime, endTime);
  }

  @PUT
  @Timed
  @Path("slo/{identifier}/restore")
  @ApiOperation(value = "Restore Failed DC tasks", nickname = "restoreFailedDCTasks", hidden = true)
  public void restore(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @QueryParam("startTime") Long startTime, @NotNull @Valid @QueryParam("endTime") Long endTime) {
    debugService.restoreSLOData(projectParams.getProjectParams(), identifier, startTime, endTime);
  }

  @POST
  @Timed
  @Path("change-event/register")
  @ApiOperation(value = "register a Change event for debugging", nickname = "registerChangeEventDebug", hidden = true)
  public RestResponse<Boolean> registerChangeEvent(
      @NotNull @BeanParam ProjectScopedProjectParams projectParams, @NotNull @Body ChangeEventDTO changeEventDTO) {
    return new RestResponse<>(
        debugService.registerInternalChangeEvent(projectParams.getProjectParams(), changeEventDTO));
  }

  @POST
  @Timed
  @Path("health-source-spec")
  @ApiOperation(value = "get health source spec", nickname = "getHealthSourceSpec")
  public RestResponse<HealthSourceSpec> validateHealthSourceSpec(
      @NotNull @BeanParam ProjectScopedProjectParams projectParams) {
    return new RestResponse<>(NextGenHealthSourceSpec.builder().build());
  }

  @POST
  @Path("register-ff-change-event")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "register fake ff event in srm queue", nickname = "register", hidden = true)
  public void register(@Body FakeFeatureFlagSRMProducer.FFEventBody ffEventBody) {
    debugService.registerFFChangeEvent(ffEventBody);
  }

  @POST
  @Path("register-srm-analysis-step")
  @Timed
  @ExceptionMetered
  @ApiOperation(
      value = "register srm analysis event for debugging", nickname = "registerSRMAnalysisStep", hidden = true)
  public RestResponse<Boolean>
  registerSRMAnalysisStep(@NotNull @Body SRMAnalysisStepExecutionDetail srmAnalysisStepBody) {
    return new RestResponse<>(debugService.registerSRMAnalysisStep(srmAnalysisStepBody));
  }
}