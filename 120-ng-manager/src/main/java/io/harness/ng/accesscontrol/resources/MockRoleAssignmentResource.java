/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("roleassignments")
@Path("roleassignments")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class MockRoleAssignmentResource {
  @POST
  @Path("filter")
  @ApiOperation(value = "(Stub) Get Filtered Role Assignments", nickname = "getFilteredRoleAssignmentList")
  public ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> get(@BeanParam PageRequest pageRequest,
      @NotNull @QueryParam(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body RoleAssignmentFilterDTO roleAssignmentFilter) {
    PageResponse<RoleAssignmentResponseDTO> pageResponse =
        PageResponse.<RoleAssignmentResponseDTO>builder().content(new ArrayList<>()).empty(true).build();
    return ResponseDTO.newResponse(pageResponse);
  }

  @POST
  @Path("aggregate")
  @ApiOperation(value = "Get Role Assignments Aggregate", nickname = "getRoleAssignmentsAggregate")
  public ResponseDTO<RoleAssignmentAggregateResponseDTO> getAggregated(
      @NotNull @QueryParam(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body RoleAssignmentFilterDTO roleAssignmentFilter) {
    RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO =
        RoleAssignmentAggregateResponseDTO.builder()
            .roleAssignments(new ArrayList<>())
            .roles(new ArrayList<>())
            .resourceGroups(new ArrayList<>())
            .scope(ScopeDTO.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .build())
            .build();
    return ResponseDTO.newResponse(roleAssignmentAggregateResponseDTO);
  }

  @POST
  @ApiOperation(value = "(Stub) Create Role Assignment", nickname = "createRoleAssignment")
  public ResponseDTO<RoleAssignmentResponseDTO> create(
      @NotNull @QueryParam(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, RoleAssignmentDTO roleAssignmentDTO) {
    RoleAssignmentResponseDTO responseDTO =
        RoleAssignmentResponseDTO.builder().roleAssignment(roleAssignmentDTO).build();
    return ResponseDTO.newResponse(responseDTO);
  }

  @POST
  @Path("/multi/internal")
  @ApiOperation(value = "(Stub) Create Multiple Role Assignments", nickname = "createRoleAssignments")
  public ResponseDTO<List<RoleAssignmentResponseDTO>> create(
      @NotNull @QueryParam(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("managed") @DefaultValue("false") Boolean managed,
      RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO) {
    List<RoleAssignmentDTO> roleAssignmentsPayload = roleAssignmentCreateRequestDTO.getRoleAssignments();
    if (roleAssignmentsPayload == null) {
      return ResponseDTO.newResponse(new ArrayList<>());
    }
    return ResponseDTO.newResponse(
        roleAssignmentsPayload.stream()
            .map(dto -> RoleAssignmentResponseDTO.builder().roleAssignment(dto).harnessManaged(managed).build())
            .collect(Collectors.toList()));
  }
}
