/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.spec.server.ng;

import io.harness.spec.server.ng.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.model.OrganizationResponse;
import io.harness.spec.server.ng.model.UpdateOrganizationRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("/v1/organizations")

public interface OrganizationsApi {
  @POST
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @Operation(summary = "Create Organization", description = "Create a new Organization.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Organization"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Organization response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = OrganizationResponse.class)))
      })
  OrganizationResponse
  createOrganization(@NotNull @QueryParam("account")

                     @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @Valid CreateOrganizationRequest body);
  @DELETE
  @Path("/{id}")
  @Produces({"application/json"})
  @Operation(summary = "Delete Organization",
      description = "Delete the information of the organization with the matching organization identifier.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Organization"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Organization response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = OrganizationResponse.class)))
      })
  OrganizationResponse
  deleteOrganization(@PathParam("id")

                     @Parameter(description = "Organization identifier") String id,
      @NotNull @QueryParam("account")

      @Parameter(description = "Slug field of the account the resource is scoped to") String account);
  @GET
  @Path("/{id}")
  @Produces({"application/json"})
  @Operation(summary = "Get Organization",
      description = "Retrieve the information of the organization with the matching organization identifier.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Organization"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Organization response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = OrganizationResponse.class)))
      })
  OrganizationResponse
  getOrganization(@PathParam("id")

                  @Parameter(description = "Organization identifier") String id,
      @NotNull @QueryParam("account")

      @Parameter(description = "Slug field of the account the resource is scoped to") String account);
  @GET
  @Produces({"application/json"})
  @Operation(summary = "Get Organizations", description = "Retrieve the information of the Organizations.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Organization"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Organization lsit response",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = OrganizationResponse.class))))
      })
  List<OrganizationResponse>
  getOrganizations(@NotNull @QueryParam("account")

                   @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @QueryParam("org")

      @Parameter(description = "Slug field of the organizations the resource is scoped to") List org,
      @QueryParam("search_term")

      @Parameter(description = "This would be used to filter resources having attributes matching with search term.")
      String searchTerm,
      @QueryParam("page") @DefaultValue("1")

      @Parameter(
          description =
              "Pagination page number strategy: Specify the page number within the paginated collection related to the number of items in each page ")
      Integer page,
      @QueryParam("limit") @DefaultValue("30")

      @Parameter(description = "Pagination: Number of items to return") Integer limit);
  @PUT
  @Path("/{id}")
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @Operation(summary = "Update Organization",
      description = "Update the information of the organization with the matching organization identifier.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Organization"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Organization response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = OrganizationResponse.class)))
      })
  OrganizationResponse
  updateOrganization(@NotNull @QueryParam("account")

                     @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @PathParam("id")

      @Parameter(description = "Organization identifier") String id, @Valid UpdateOrganizationRequest body);
}
