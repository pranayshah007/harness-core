/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.resources;

import io.harness.spec.server.idp.v1.model.StatusInfoRequest;
import io.harness.spec.server.idp.v1.model.StatusInfoResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Api(value = "/v1/status-info/{type}", tags = "StatusInfo")
@Path("/v1/status-info/{type}")
public interface StatusInfoResource {
  @GET
  @Produces({"application/json", "application/yaml"})
  @Operation(operationId = "getStatusInfoByType", summary = "Gets Status Info by type",
      description = "Get status info for the given type", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"StatusInfo"})
  @ApiResponses({ @ApiResponse(code = 200, message = "Example response", response = StatusInfoResponse.class) })
  Response
  getStatusInfoByType(@PathParam("type") String type,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);

  @POST
  @Consumes({"application/json", "application/yaml"})
  @Produces({"application/json", "application/yaml"})
  @Operation(operationId = "saveStatusInfoByType", summary = "Saves Status Info by type",
      description = "Saves status info for the given type", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"StatusInfo"})
  @ApiResponses({ @ApiResponse(code = 201, message = "Example response", response = StatusInfoResponse.class) })
  Response
  saveStatusInfoByType(@PathParam("type") String type, @Valid StatusInfoRequest body,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);
}
