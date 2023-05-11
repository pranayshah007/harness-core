/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackRequestDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(HarnessTeam.CDP)
@Api("rollback")
@Path("rollback")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = io.harness.ng.core.dto.FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = io.harness.ng.core.dto.ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class PostProdRollbackResource {
  @Inject PostProdRollbackService postProdRollbackService;
  @POST
  @Path("/trigger")
  @ApiOperation(value = "Trigger the post-prod-rollback for the given instanceUuid", nickname = "triggerRollback")
  @Hidden
  public ResponseDTO<PostProdRollbackResponseDTO> triggerRollback(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull PostProdRollbackRequestDTO requestDTO) {
    return ResponseDTO.newResponse(postProdRollbackService.triggerRollback(
        accountIdentifier, requestDTO.getInstanceKey(), requestDTO.getInfrastructureMappingId()));
  }
  @POST
  @Path("/check")
  @ApiOperation(value = "Check if the post-prod-rollback is possible for the given instanceUuid",
      nickname = "checkIfInstanceCanBeRolledBack")
  @Hidden
  public ResponseDTO<PostProdRollbackCheckDTO>
  checkIfInstanceCanBeRolledBack(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull @Valid PostProdRollbackRequestDTO requestDTO) {
    return ResponseDTO.newResponse(postProdRollbackService.checkIfRollbackAllowed(
        accountIdentifier, requestDTO.getInstanceKey(), requestDTO.getInfrastructureMappingId()));
  }
}
