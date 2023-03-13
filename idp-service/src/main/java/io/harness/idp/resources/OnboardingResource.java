/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.resources;

import io.harness.spec.server.idp.v1.model.HarnessEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportHarnessEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingAccessCheckResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Api(value = "/v1/account/{accountIdentifier}/onboarding", tags = "OnboardingResource")
@Path("/v1/account/{accountIdentifier}/onboarding")
public interface OnboardingResource {
  @GET
  @Path("/access-check")
  @Produces({"application/json"})
  @Operation(operationId = "onboardingAccessCheckV1",
      summary = "Check if User is allowed to perform IDP onboarding workflow",
      description = "Check if User is allowed to perform IDP onboarding workflow",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"OnboardingResource"})
  @ApiResponses({
    @ApiResponse(code = 200, message = "IDP Onboarding AccessCheck response for given user account",
        response = OnboardingAccessCheckResponse.class)
  })
  Response
  onboardingAccessCheckV1(
      @PathParam("accountIdentifier") @Parameter(
          description = "Identifier field of the account the resource is scoped to") String accountIdentifier,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);

  @GET
  @Path("/harness-entities")
  @Produces({"application/json"})
  @Operation(operationId = "getHarnessEntitiesV1", summary = "Get Harness Entities",
      description = "Get Harness Entities", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"OnboardingResource"})
  @ApiResponses({
    @ApiResponse(code = 200, message = "Response for harness entities mapping with backstage entities",
        response = HarnessEntitiesResponse.class, responseHeaders = {
          @ResponseHeader(name = "X-Total-Elements",
              description = "Total number of elements returned in Paginated response.", response = Integer.class)
          ,
              @ResponseHeader(
                  name = "X-Page-Number", description = "Page number in Paginated response.", response = Integer.class),
              @ResponseHeader(name = "X-Page-Size", description = "Maximum page size in Paginated response.",
                  response = Integer.class)
        })
  })
  Response
  getHarnessEntitiesV1(
      @PathParam("accountIdentifier") @Parameter(
          description = "Identifier field of the account the resource is scoped to") String accountIdentifier,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      @QueryParam("page") @Parameter(
          description =
              "Pagination page number strategy: Specify the page number within the paginated collection related to the number of items in each page ")
      Integer page,
      @QueryParam("limit") @Parameter(description = "Pagination: Number of items to return") Integer limit,
      @QueryParam("sort") @Parameter(description = "Parameter on the basis of which sorting is done.") String sort,
      @QueryParam("order") @Parameter(description = "Order on the basis of which sorting is done.") String order,
      @QueryParam("search_term") @Parameter(
          description = "This would be used to filter resources having attributes matching the search term.")
      String searchTerm);

  @POST
  @Path("/import-harness-entities")
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @Operation(operationId = "importHarnessEntitiesV1", summary = "Import Harness Entities to IDP",
      description = "Import Harness Entities to IDP", security = { @SecurityRequirement(name = "x-api-key") },
      tags = {"OnboardingResource"})
  @ApiResponses({
    @ApiResponse(code = 200, message = "Response for import / save harness entities to IDP",
        response = ImportEntitiesResponse.class)
  })
  Response
  importHarnessEntitiesV1(
      @PathParam("accountIdentifier") @Parameter(
          description = "Identifier field of the account the resource is scoped to") String accountIdentifier,
      @Valid ImportHarnessEntitiesRequest body,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);
}
