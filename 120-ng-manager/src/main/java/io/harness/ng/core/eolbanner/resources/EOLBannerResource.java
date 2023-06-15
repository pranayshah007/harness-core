/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.eolbanner.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.eolbanner.EOLBannerService;
import io.harness.ng.core.eolbanner.dto.EOLBannerRequestDTO;
import io.harness.ng.core.eolbanner.dto.EOLBannerResponseDTO;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Hidden
@Api("/serviceV1-eol-banner")
@Path("/serviceV1-eol-banner")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EOLBannerResource {
  private final EOLBannerService eolBannerService;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  @POST
  @Path("/pipeline")
  @ApiOperation(value = "Returns list of stage identifiers using v1 stage", nickname = "checkIfPipelineUsingV1Stage")
  @Operation(operationId = "checkIfPipelineUsingV1Stage",
      summary = "checks if pipeline is using v1 stage and returns list of stage identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of stage identifiers")
      })
  public ResponseDTO<EOLBannerResponseDTO>
  checkIfPipelineUsingV1Stage(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Valid EOLBannerRequestDTO requestDto) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), accountId);

    EOLBannerResponseDTO responseDTO = eolBannerService.checkPipelineUsingV1Stage(accountId, requestDto);
    return ResponseDTO.newResponse(responseDTO);
  }

  @POST
  @Path("/template")
  @ApiOperation(value = "Returns list of stage identifiers using v1 stage", nickname = "checkIfTemplateUsingV1Stage")
  @Operation(operationId = "checkIfTemplateUsingV1Stage",
      summary = "checks if template is using v1 stage and returns list of stage identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of stage identifiers")
      })
  public ResponseDTO<EOLBannerResponseDTO>
  checkIfTemplateUsingV1Stage(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Valid EOLBannerRequestDTO requestDto) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        requestDto.getOrgIdentifier(), requestDto.getProjectIdentifier(), accountId);

    EOLBannerResponseDTO responseDTO = eolBannerService.checkTemplateUsingV1Stage(accountId, requestDto);
    return ResponseDTO.newResponse(responseDTO);
  }
}
