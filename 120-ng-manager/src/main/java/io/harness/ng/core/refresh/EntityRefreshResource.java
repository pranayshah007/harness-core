/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.refresh.service.EntityRefreshService;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.refresh.NgManagerRefreshRequestDTO;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import retrofit2.http.Body;

@OwnedBy(HarnessTeam.CDC)
@Api("refresh-inputs")
@Path("refresh-inputs")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class EntityRefreshResource {
  @Inject EntityRefreshService entityRefreshService;

  @POST
  @Path("validate-inputs-yaml")
  @ApiOperation(value = "This validates whether inputs provided to different references in yaml is valid or not",
      nickname = "validateInputsForYaml")
  public ResponseDTO<InputsValidationResponse>
  validateInputsForYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "YAML") @NotNull @Body NgManagerRefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(entityRefreshService.validateInputsForYaml(
        accountId, orgId, projectId, refreshRequestDTO.getYaml(), refreshRequestDTO.getResolvedTemplatesYaml()));
  }

  @POST
  @Path("refreshed-yaml")
  @ApiOperation(value = "This refreshes and update inputs of entities in given yaml", nickname = "getRefreshedYaml")
  @Hidden
  public ResponseDTO<RefreshResponseDTO> getRefreshedYaml(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "YAML") @NotNull @Body NgManagerRefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(
        RefreshResponseDTO.builder()
            .refreshedYaml(entityRefreshService.refreshLinkedInputs(
                accountId, orgId, projectId, refreshRequestDTO.getYaml(), refreshRequestDTO.getResolvedTemplatesYaml()))
            .build());
  }
}
