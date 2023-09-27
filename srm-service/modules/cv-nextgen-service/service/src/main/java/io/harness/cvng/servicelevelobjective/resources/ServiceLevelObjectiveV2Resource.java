/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.CVConstants;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.utils.NGAccessControlClientCheck;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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

@Api("slo/v2")
@Path("slo/v2")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
//@Tag(name = "V2 SLOs", description = "This contains APIs related to CRUD operations of V2 SLOs")
//@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
//    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
//    content =
//    {
//      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
//          schema = @Schema(implementation = FailureDTO.class))
//      ,
//          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
//              schema = @Schema(implementation = FailureDTO.class))
//    })
//@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode =
// NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
//    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
//    content =
//    {
//      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
//          schema = @Schema(implementation = ErrorDTO.class))
//      ,
//          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
//              schema = @Schema(implementation = ErrorDTO.class))
//    })
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelObjectiveV2Resource {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject AccessControlClient accessControlClient;

  public static final String SLO = "SLO";
  public static final String EDIT_PERMISSION = "chi_slo_edit";
  public static final String VIEW_PERMISSION = "chi_slo_view";
  public static final String DELETE_PERMISSION = "chi_slo_delete";

  @POST
  @Timed
  @ExceptionMetered
  @Path("composite-slo/onboarding-graph")
  @ResponseMetered
  @ApiOperation(value = "Get onboarding graph for composite slo", nickname = "getOnboardingGraph")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<TimeGraphResponse> getOnboardingGraph(@BeanParam ProjectParams projectParams,
      @Parameter(description = "Composite SLO spec which consists of list of SLO details") @ApiParam(
          required = true) @Valid @Body CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec) {
    return new RestResponse<>(serviceLevelObjectiveV2Service.getOnboardingGraph(compositeServiceLevelObjectiveSpec));
  }

  @POST
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "saves slo data", nickname = "saveSLOV2Data")
  @Operation(operationId = "saveSLOV2Data", summary = "Saves SLO data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the saved SLO")
      })
  @NGAccessControlClientCheck
  public RestResponse<ServiceLevelObjectiveV2Response>
  saveSLOV2Data(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @ApiParam(
                    required = true) @NotNull @QueryParam("accountId") String accountId,
      @Parameter(description = "Details of the SLO to be saved") @Valid
      @Body ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, serviceLevelObjectiveDTO.getOrgIdentifier(),
                                                  serviceLevelObjectiveDTO.getProjectIdentifier()),
        Resource.of(SLO, null), EDIT_PERMISSION);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(serviceLevelObjectiveDTO.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjectiveDTO.getProjectIdentifier())
                                      .build();
    // TODO: change this api signature
    return new RestResponse<>(serviceLevelObjectiveV2Service.create(projectParams, serviceLevelObjectiveDTO));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @Path("{identifier}")
  @ApiOperation(value = "update slo data", nickname = "updateSLOV2Data")
  @Operation(operationId = "updateSLOV2Data", summary = "Update SLO data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the updated SLO")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<ServiceLevelObjectiveV2Response>
  updateSLOV2Data(@Valid @BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String identifier,
      @Parameter(description = "Details of the SLO to be updated") @Valid
      @Body ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    return new RestResponse<>(
        serviceLevelObjectiveV2Service.update(projectParams, identifier, serviceLevelObjectiveDTO));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @Path("{identifier}")
  @ApiOperation(value = "delete slo data", nickname = "deleteSLOV2Data")
  @Operation(operationId = "deleteSLOV2Data", summary = "Delete SLO data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if the SLO is deleted")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = DELETE_PERMISSION)
  public RestResponse<Boolean>
  deleteSLOV2Data(@Valid @BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(serviceLevelObjectiveV2Service.delete(projectParams, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @ApiOperation(value = "get all service level objectives", nickname = "getServiceLevelObjectivesV2")
  @Operation(operationId = "getServiceLevelObjectivesV2", summary = "Get all SLOs",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Gets the SLOs") })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<ServiceLevelObjectiveV2Response>>
  getServiceLevelObjectivesV2(@Valid @BeanParam ProjectParams projectParams,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          "offset") @NotNull Integer offset,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam("pageSize")
      @NotNull Integer pageSize, @BeanParam ServiceLevelObjectiveFilter serviceLevelObjectiveFilter) {
    return ResponseDTO.newResponse(
        serviceLevelObjectiveV2Service.get(projectParams, offset, pageSize, serviceLevelObjectiveFilter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "get service level objective data", nickname = "getServiceLevelObjectiveV2")
  @Operation(operationId = "getServiceLevelObjectiveV2", summary = "Get SLO data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the SLO's data")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<ServiceLevelObjectiveV2Response>
  getServiceLevelObjectiveV2(@BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(serviceLevelObjectiveV2Service.get(projectParams, identifier));
  }
}
