/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.terraformcloud.resources;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.provision.terraformcloud.resources.dtos.OrganizationsDTO;
import io.harness.cdng.provision.terraformcloud.resources.dtos.WorkspacesDTO;
import io.harness.cdng.provision.terraformcloud.resources.service.TerraformCloudResourceService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDP)
@Api("terraform-cloud")
@Path("/terraform-cloud")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class TerraformCloudResource {
  private final TerraformCloudResourceService terraformCloudResourceService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  @GET
  @Path("organizations")
  @ApiOperation(value = "Gets terraform cloud organizations", nickname = "getTerraformCloudOrganizations")
  public ResponseDTO<OrganizationsDTO> getOrganizations(
      @NotNull @QueryParam("connectorRef") String terraformCloudConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        terraformCloudConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        terraformCloudResourceService.getOrganizations(connectorRef, orgIdentifier, projectIdentifier));
  }

  @GET
  @Path("workspaces")
  @ApiOperation(value = "Gets terraform cloud workspaces", nickname = "getTerraformCloudWorkspaces")
  public ResponseDTO<WorkspacesDTO> getWorkspaces(@QueryParam("connectorRef") String terraformCloudConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotEmpty @NotNull @QueryParam("organization") String organization) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        terraformCloudConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        terraformCloudResourceService.getWorkspaces(connectorRef, orgIdentifier, projectIdentifier, organization));
  }
}
