/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.spec.server.ng;

import io.harness.spec.server.ng.model.CreateProjectRequest;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.spec.server.ng.model.UpdateProjectRequest;

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

@Path("/v1/projects")

public interface ProjectsApi {
  @POST
  @Consumes({"application/json", "application/yaml"})
  @Produces({"application/json", "application/yaml"})
  @Operation(summary = "Create Project", description = "Creates a new Project",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Project"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Project response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = ProjectResponse.class)))
      })
  ProjectResponse
  createProject(@NotNull @QueryParam("account")

                @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @NotNull @QueryParam("org")

      @Parameter(description = "Slug field of the organization the resource is scoped to") String org,
      @Valid CreateProjectRequest body);
  @DELETE
  @Path("/{id}")
  @Produces({"application/json", "application/yaml"})
  @Operation(summary = "Delete Project",
      description = "Deletes the information of the Project with the matching Project identifier.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Project"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Project response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = ProjectResponse.class)))
      })
  ProjectResponse
  deleteProject(@PathParam("id")

                @Parameter(description = "Project identifier") String id,
      @NotNull @QueryParam("account")

      @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @NotNull @QueryParam("org")

      @Parameter(description = "Slug field of the organization the resource is scoped to") String org);
  @GET
  @Path("/{id}")
  @Produces({"application/json", "application/yaml"})
  @Operation(summary = "Get Project",
      description = "Retrieve the information of the Project with the matching Project identifier.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Project"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Project response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = ProjectResponse.class)))
      })
  ProjectResponse
  getProject(@PathParam("id")

             @Parameter(description = "Project identifier") String id,
      @NotNull @QueryParam("account")

      @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @NotNull @QueryParam("org")

      @Parameter(description = "Slug field of the organization the resource is scoped to") String org);
  @GET
  @Produces({"application/json"})
  @Operation(summary = "Get Projects", description = "Retrieves the information of the Projects.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Project"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Project list response",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = ProjectResponse.class))))
      })
  List<ProjectResponse>
  getProjects(@NotNull @QueryParam("account")

              @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @QueryParam("org")

      @Parameter(description = "Slug field of the organizations the resource is scoped to") List org,
      @QueryParam("project")

      @Parameter(description = "Slug field of the projects the resource is scoped to") List project,
      @QueryParam("has_module") @DefaultValue("true")

      @Parameter(
          description =
              "This boolean specifies whether to Filter Projects which has the Module of type passed in the moduleType parameter or not")
      Boolean hasModule,
      @QueryParam("module_type")

      @Parameter(description = "Project&#x27;s module type") String moduleType,
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
  @Produces({"application/json", "application/yaml"})
  @Operation(summary = "Update Project",
      description = "Updates the information of the Project with the matching Project identifier.",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Project"})
  @ApiResponses(value =
      {
        @ApiResponse(responseCode = "200", description = "Project response",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = ProjectResponse.class)))
      })
  ProjectResponse
  updateProject(@NotNull @QueryParam("account")

                @Parameter(description = "Slug field of the account the resource is scoped to") String account,
      @NotNull @QueryParam("org")

      @Parameter(description = "Slug field of the organization the resource is scoped to") String org,
      @PathParam("id")

      @Parameter(description = "Project identifier") String id, @Valid UpdateProjectRequest body);
}
