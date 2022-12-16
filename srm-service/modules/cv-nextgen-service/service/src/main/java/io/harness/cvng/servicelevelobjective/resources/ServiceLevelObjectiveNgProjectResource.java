/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.RESOURCE_IDENTIFIER_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.SLO_NG_PROJECT_PATH;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.beans.params.ResourcePathParams;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api(SLO_NG_PROJECT_PATH)
@Path(SLO_NG_PROJECT_PATH)
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@Tag(name = "NG SLOs", description = "This contains APIs related to CRUD operations of SLOs (simple & composite)")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelObjectiveNgProjectResource {
  public static final String SLO = "SLO";
  public static final String EDIT_PERMISSION = "chi_slo_edit";
  public static final String VIEW_PERMISSION = "chi_slo_view";
  public static final String DELETE_PERMISSION = "chi_slo_delete";

  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveService;
  @Inject SLODashboardService sloDashboardService;

  @POST
  @Timed
  @ExceptionMetered
  @Path("/composite-slo/onboarding-graph")
  @ApiOperation(value = "Get onboarding graph for composite slo", nickname = "getOnboardingGraphNg")
  @Operation(operationId = "getOnboardingGraphNg", summary = "Get onBoarding graph for composite slo",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Gets the time series data points for composite slo onBoarding graph")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<TimeGraphResponse>
  getOnboardingGraphNg(@Valid @BeanParam ProjectPathParams projectPathParams,
      @Parameter(description = "Composite SLO spec which consists of list of SLO details") @ApiParam(required = true)
      @NotNull @Valid @Body CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec) {
    return new RestResponse<>(serviceLevelObjectiveService.getOnboardingGraph(compositeServiceLevelObjectiveSpec));
  }

  @POST
  @Consumes("application/json")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves slo data", nickname = "saveSLODataNg")
  @Operation(operationId = "saveSLODataNg", summary = "Saves SLO data",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the saved SLO") })
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<ServiceLevelObjectiveV2Response>
  saveSLODataNg(@Valid @BeanParam ProjectPathParams projectPathParams,
      @Parameter(description = "Details of the SLO to be saved") @NotNull @Valid
      @Body ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    validations(
        projectPathParams.getOrgIdentifier(), projectPathParams.getProjectIdentifier(), serviceLevelObjectiveDTO);
    ProjectParams projectParams = buildProjectParamsFromPathParams(projectPathParams);
    return new RestResponse<>(serviceLevelObjectiveService.create(projectParams, serviceLevelObjectiveDTO));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "get service level objective data", nickname = "getServiceLevelObjectiveNg")
  @Operation(operationId = "getServiceLevelObjectiveNg", summary = "Get SLO data",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the SLO's data") })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<ServiceLevelObjectiveV2Response>
  getServiceLevelObjectiveNg(@Valid @BeanParam ResourcePathParams resourcePathParams) {
    ProjectParams projectParams = buildProjectParamsFromPathParams(resourcePathParams);
    return new RestResponse<>(serviceLevelObjectiveService.get(projectParams, resourcePathParams.getIdentifier()));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all service level objectives", nickname = "getServiceLevelObjectivesNg")
  @Operation(operationId = "getServiceLevelObjectivesNg", summary = "Get all SLOs",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the SLOs") })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<ServiceLevelObjectiveV2Response>>
  getServiceLevelObjectivesNg(@Valid @BeanParam ProjectPathParams projectPathParams,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          "offset") @NotNull Integer offset,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam("pageSize")
      @NotNull Integer pageSize, @BeanParam ServiceLevelObjectiveFilter serviceLevelObjectiveFilter) {
    ProjectParams projectParams = buildProjectParamsFromPathParams(projectPathParams);
    return ResponseDTO.newResponse(
        serviceLevelObjectiveService.get(projectParams, offset, pageSize, serviceLevelObjectiveFilter));
  }

  @PUT
  @Consumes("application/json")
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "update slo data", nickname = "updateSLODataNg")
  @Operation(operationId = "updateSLODataNg", summary = "Update SLO data",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the updated SLO") })
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<ServiceLevelObjectiveV2Response>
  updateSLODataNg(@Valid @BeanParam ResourcePathParams resourcePathParams,
      @Parameter(description = "Details of the SLO to be updated") @NotNull @Valid
      @Body ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    validations(
        resourcePathParams.getOrgIdentifier(), resourcePathParams.getProjectIdentifier(), serviceLevelObjectiveDTO);
    ProjectParams projectParams = buildProjectParamsFromPathParams(resourcePathParams);
    return new RestResponse<>(serviceLevelObjectiveService.update(
        projectParams, resourcePathParams.getIdentifier(), serviceLevelObjectiveDTO));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "delete slo data", nickname = "deleteSLODataNg")
  @Operation(operationId = "deleteSLODataNg", summary = "Delete SLO data",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the SLO is deleted") })
  @NGAccessControlCheck(resourceType = SLO, permission = DELETE_PERMISSION)
  public RestResponse<Boolean>
  deleteSLODataNg(@Valid @BeanParam ResourcePathParams resourcePathParams) {
    ProjectParams projectParams = buildProjectParamsFromPathParams(resourcePathParams);
    return new RestResponse<>(serviceLevelObjectiveService.delete(projectParams, resourcePathParams.getIdentifier()));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("/status-list")
  @ApiOperation(value = "get slo list view", nickname = "getSLOHealthListViewNg")
  @Operation(operationId = "getSLOHealthListViewNg", summary = "Get SLO list view",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the SLOs for list view") })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<SLOHealthListView>>
  getSLOHealthListViewNg(@Valid @BeanParam ProjectPathParams projectPathParams, @BeanParam PageParams pageParams,
      @Valid @Body SLODashboardApiFilter filter) {
    ProjectParams projectParams = buildProjectParamsFromPathParams(projectPathParams);
    return ResponseDTO.newResponse(sloDashboardService.getSloHealthListView(projectParams, filter, pageParams));
  }

  private static void validations(
      String orgIdentifier, String projectIdentifier, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    if (!orgIdentifier.equals(serviceLevelObjectiveDTO.getOrgIdentifier())
        || !projectIdentifier.equals(serviceLevelObjectiveDTO.getProjectIdentifier())) {
      throw new IllegalArgumentException("Mismatch between path params and request dto for org / project identifier");
    }
  }

  private static ProjectParams buildProjectParamsFromPathParams(ProjectPathParams projectPathParams) {
    return ProjectParams.builder()
        .accountIdentifier(projectPathParams.getAccountIdentifier())
        .orgIdentifier(projectPathParams.getOrgIdentifier())
        .projectIdentifier(projectPathParams.getProjectIdentifier())
        .build();
  }
}
