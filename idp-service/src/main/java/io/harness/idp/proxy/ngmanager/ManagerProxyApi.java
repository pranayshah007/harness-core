/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.ngmanager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/v1/idp-proxy/manager/{url:.+}")
public interface ManagerProxyApi {
  @GET
  @Produces({"text/plain", "application/json"})
  @Operation(operationId = "getProxyManager", summary = "Forward GET calls to Manager",
      description = "Forward GET calls to Manager", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyManager"})
  @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
  Response
  getProxyManager(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);

  @POST
  @Consumes({"application/json"})
  @Produces({"text/plain", "application/json"})
  @Operation(operationId = "postProxyManager", summary = "Forward POST calls to Manager",
      description = "Forward POST calls to Manager", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyManager"})
  @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
  Response
  postProxyManager(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @PUT
  @Consumes({"application/json"})
  @Produces({"text/plain", "application/json"})
  @Operation(operationId = "putProxyManager", summary = "Forward POST calls to Manager",
      description = "Forward PUT calls to Manager", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyManager"})
  @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
  Response
  putProxyManager(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @DELETE
  @Consumes({"application/json"})
  @Operation(operationId = "deleteProxyManager", summary = "Forward DELETE calls to Manager",
      description = "Forward DELETE calls to NG Manager", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyManager"})
  @ApiResponses({ @ApiResponse(responseCode = "204", description = "No Content") })
  Response
  deleteProxyManager(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);
}
