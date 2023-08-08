/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.services;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/v1/idp-proxy-service/{service}/{url:.+}")
public interface ProxyApi {
  @GET
  @Produces({"text/plain", "application/json"})
  @Operation(operationId = "getProxyService", summary = "Forward GET calls to requested service",
      description = "Forward GET calls to requested service", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyService"})
  @ApiResponse(responseCode = "200", description = "OK")
  Response
  getProxyService(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("service") String service,
      @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);

  @POST
  @Consumes({"application/json", "text/plain; charset=utf-8"})
  @Produces({"text/plain", "application/json"})
  @Operation(operationId = "postProxyService", summary = "Forward POST calls to requested service",
      description = "Forward POST calls to requested service", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyService"})
  @ApiResponse(responseCode = "200", description = "OK")
  Response
  postProxyService(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("service") String service,
      @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @PUT
  @Consumes({"application/json"})
  @Produces({"text/plain", "application/json"})
  @Operation(operationId = "putProxyService", summary = "Forward POST calls to requested service",
      description = "Forward PUT calls to requested service", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"IdpProxyService"})
  @ApiResponse(responseCode = "200", description = "OK")
  Response
  putProxyService(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("service") String service,
      @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @DELETE
  @Consumes({"application/json"})
  @Operation(operationId = "deleteProxyService", summary = "Forward DELETE calls to requested service",
      description = "Forward DELETE calls to requested service",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"IdpProxyService"})
  @ApiResponse(responseCode = "204", description = "No Content")
  Response
  deleteProxyService(@Context UriInfo uriInfo, @Context HttpHeaders headers, @PathParam("service") String service,
      @PathParam("url:.+") String url,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);
}
