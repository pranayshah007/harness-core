/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.trialsignup;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(CI)
@Api("trial-signup")
@Path("/trial-signup")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class CIProvisionResource {
  @Inject ProvisionService provisionService;
  @PUT
  @Path("provision")
  @ApiOperation(value = "Provision resources for signup", nickname = "provisionResourcesForCI")
  public ResponseDTO<ProvisionResponse.SetupStatus> provisionCIResources(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return ResponseDTO.newResponse(provisionService.provisionCIResources(accountId));
  }

  @GET
  @Path("delegate-install-status")
  @ApiOperation(value = "Provision resources for signup", nickname = "getDelegateInstallStatus")
  public ResponseDTO<ProvisionResponse.DelegateStatus> getDelegateInstallStatus(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    return ResponseDTO.newResponse(provisionService.getDelegateInstallStatus(accountId));
  }

  @POST
  @Path("create-scm-connector")
  @ApiOperation(value = "Creates default scm Connector", nickname = "createDefaultScmConnector")
  public ResponseDTO<ScmConnectorResponse> createScmConnector(
      @RequestBody(required = true,
          description = "Details of the Connector to create") @Valid @NotNull ScmConnectorDTO scmConnectorDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(
        provisionService.createDefaultScm(accountIdentifier, orgIdentifier, projectIdentifier, scmConnectorDTO));
  }

  @GET
  @Path("generate-yaml")
  @ApiOperation(value = "generate yaml", nickname = "generateYaml")
  public ResponseDTO<String> autogenerateYaml(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank
                                              @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "connectorIdentifier") @QueryParam("connectorIdentifier") String connectorIdentifier,
      @Parameter(description = "repo") @QueryParam("repo") String repo,
      @Parameter(description = "yamlVersion") @QueryParam("yamlVersion") String yamlVersion) {
    return ResponseDTO.newResponse(provisionService.generateYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, repo, yamlVersion));
  }
}
