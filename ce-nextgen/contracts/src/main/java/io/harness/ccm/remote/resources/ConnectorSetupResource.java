/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.AwsAccountConnectionDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.springframework.stereotype.Service;

@Api("connector")
@Path("/connector")
@Produces({MediaType.APPLICATION_JSON})
@NextGenManagerAuth
@Service
@OwnedBy(CE)
public interface ConnectorSetupResource {
  @GET
  @Path("/azureappclientid")
  @ApiOperation(value = "Get Azure application client Id", nickname = "azureappclientid")
  ResponseDTO<String> getAzureAppClientId();

  @GET
  @Path("/awsaccountconnectiondetail")
  @ApiOperation(value = "Get Aws account connection details", nickname = "awsaccountconnectiondetail")
  public ResponseDTO<AwsAccountConnectionDetail> getAwsAccountConnectionDetail(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId);

  @GET
  @Path("/gcpserviceaccount")
  @ApiOperation(value = "Provision and Get GCP Service Account", nickname = "gcpserviceaccount")
  public ResponseDTO<String> getGcpServiceAccount(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId);
}
