/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_LIST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.ng.core.remote.ProjectMapper.toResponseWithFavouritesInfo;
import static io.harness.utils.PageUtils.getNGPageResponse;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.UserHelperService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@Api("projects")
@Path("projects")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Project", description = "This contains APIs related to Project as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@NextGenManagerAuth
public class ProjectResource {
  private final ProjectService projectService;
  private final OrganizationService organizationService;
  private final FavoritesService favoritesService;
  private final UserHelperService userHelperService;

  @POST
  @ApiOperation(value = "Create a Project", nickname = "postProject")
  @NGAccessControlCheck(resourceType = PROJECT, permission = CREATE_PROJECT_PERMISSION)
  @Operation(operationId = "postProject", summary = "Create a Project", description = "Creates a new Harness Project.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Project")
      })
  public ResponseDTO<ProjectResponse>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(
          description =
              "Organization identifier for the Project. If left empty, the Project is created under Default Organization")
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @DefaultValue(
          DEFAULT_ORG_IDENTIFIER) @OrgIdentifier String orgIdentifier,
      @RequestBody(required = true,
          description = "Details of the Project to create") @NotNull @Valid ProjectRequest projectDTO) {
    Project createdProject = projectService.create(accountIdentifier, orgIdentifier, projectDTO.getProject());
    return ResponseDTO.newResponse(createdProject.getVersion().toString(),
        ProjectMapper.toProjectResponseBuilder(createdProject)
            .isFavorite(projectService.isFavorite(createdProject, userHelperService.getUserId()))
            .build());
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Gets a Project by ID", nickname = "getProject")
  @Operation(operationId = "getProject", summary = "List Project details",
      description = "Lists a Project's details for the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Project having ID as specified in request")
      })
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<ProjectResponse>
  get(@Parameter(description = PROJECT_PARAM_MESSAGE) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(
          description = "Organization identifier for the project. If left empty, Default Organization is assumed")
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @DefaultValue(
          DEFAULT_ORG_IDENTIFIER) @OrgIdentifier String orgIdentifier) {
    Optional<Project> projectOptional = projectService.get(accountIdentifier, orgIdentifier, identifier);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(
          String.format("Project with orgIdentifier [%s] and identifier [%s] not found", orgIdentifier, identifier));
    }
    return ResponseDTO.newResponse(projectOptional.get().getVersion().toString(),
        ProjectMapper.toProjectResponseBuilder(projectOptional.get())
            .isFavorite(projectService.isFavorite(projectOptional.get(), userHelperService.getUserId()))
            .build());
  }

  @GET
  @ApiOperation(value = "Get Project list", nickname = "getProjectList")
  @Operation(operationId = "getProjectList", summary = "List all Projects for a user",
      description = "Lists all Projects the user is a member of by using the user's API key token.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of Projects")
      })
  public ResponseDTO<PageResponse<ProjectResponse>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = "This boolean specifies whether to Filter Projects which has the Module of type "
              + "passed in the module type parameter or to Filter Projects which does not has the Module of type "
              + "passed in the module type parameter") @QueryParam("hasModule") @DefaultValue("true") boolean hasModule,
      @Parameter(description = "This is the list of Project IDs. Details specific to these IDs would be fetched.")
      @QueryParam(NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Parameter(description = "Filter Projects by module type") @QueryParam(
          NGResourceFilterConstants.MODULE_TYPE_KEY) ModuleType moduleType,
      @Parameter(
          description =
              "This would be used to filter Projects. Any Project having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam("onlyFavorites") @DefaultValue("false") Boolean onlyFavorites, @BeanParam PageRequest pageRequest) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    ProjectFilterDTO projectFilterDTO =
        ProjectFilterDTO.builder()
            .searchTerm(searchTerm)
            .orgIdentifiers(StringUtils.isNotBlank(orgIdentifier) ? Collections.singleton(orgIdentifier) : null)
            .hasModule(hasModule)
            .moduleType(moduleType)
            .identifiers(identifiers)
            .build();
    Page<Project> projects = projectService.listPermittedProjects(
        accountIdentifier, getPageRequest(pageRequest), projectFilterDTO, onlyFavorites);
    List<Favorite> favoriteProjects = favoritesService.getFavorites(
        accountIdentifier, orgIdentifier, null, userHelperService.getUserId(), ResourceType.PROJECT.toString());
    return ResponseDTO.newResponse(getNGPageResponse(toResponseWithFavouritesInfo(projects, favoriteProjects)));
  }

  @GET
  @Path("/list")
  @ApiOperation(value = "Get Project list", nickname = "getProjectListWithMultiOrgFilter")
  @Operation(operationId = "getProjectListWithMultiOrgFilter",
      summary = "List user's project with support to filter by multiple organizations",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of Projects")
      })
  public ResponseDTO<PageResponse<ProjectResponse>>
  listWithMultiOrg(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORGS_KEY) Set<String> orgIdentifiers,
      @Parameter(description = "This boolean specifies whether to Filter Projects which has the Module of type "
              + "passed in the module type parameter or to Filter Projects which does not has the Module of type "
              + "passed in the module type parameter") @QueryParam(NGResourceFilterConstants.HAS_MODULE_KEY)
      @DefaultValue("true") boolean hasModule,
      @Parameter(
          description = "This is the list of Project Identifiers. Details specific to these IDs would be fetched.")
      @QueryParam(NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Parameter(description = "Filter Projects by module type") @QueryParam(
          NGResourceFilterConstants.MODULE_TYPE_KEY) ModuleType moduleType,
      @Parameter(description = "Filter Projects by searching for this word in Name, Id, and Tag") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam("onlyFavorites") @DefaultValue("false") Boolean onlyFavorites, @BeanParam PageRequest pageRequest) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    ProjectFilterDTO projectFilterDTO = ProjectFilterDTO.builder()
                                            .searchTerm(searchTerm)
                                            .orgIdentifiers(isNotEmpty(orgIdentifiers) ? orgIdentifiers : null)
                                            .hasModule(hasModule)
                                            .moduleType(moduleType)
                                            .identifiers(identifiers)
                                            .build();
    Page<Project> projects = projectService.listPermittedProjects(
        accountIdentifier, getPageRequest(pageRequest), projectFilterDTO, onlyFavorites);
    List<Favorite> favoriteProjects =
        projectService.getProjectFavorites(accountIdentifier, projectFilterDTO, userHelperService.getUserId());
    return ResponseDTO.newResponse(getNGPageResponse(toResponseWithFavouritesInfo(projects, favoriteProjects)));
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update a Project by ID", nickname = "putProject")
  @Operation(operationId = "putProject", summary = "Update a Project",
      description = "Updates Project details for the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated Project details")
      })
  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  public ResponseDTO<ProjectResponse>
  update(@Parameter(description = "Version number of Project") @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(
          description = "Organization identifier for the Project. If left empty, Default Organization is assumed")
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @DefaultValue(
          DEFAULT_ORG_IDENTIFIER) @OrgIdentifier String orgIdentifier,
      @RequestBody(required = true,
          description =
              "This is the updated Project. Please provide values for all fields, not just the fields you are updating")
      @NotNull @Valid ProjectRequest projectDTO) {
    projectDTO.getProject().setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Project updatedProject =
        projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO.getProject());
    return ResponseDTO.newResponse(updatedProject.getVersion().toString(),
        ProjectMapper.toProjectResponseBuilder(updatedProject)
            .isFavorite(projectService.isFavorite(updatedProject, userHelperService.getUserId()))
            .build());
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a Project by identifier", nickname = "deleteProject")
  @Operation(operationId = "deleteProject", summary = "Delete a Project",
      description = "Deletes a Project corresponding to the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "It returns true if the Project is deleted successfully and false if the Project is not deleted")
      })
  @NGAccessControlCheck(resourceType = PROJECT, permission = DELETE_PROJECT_PERMISSION)
  public ResponseDTO<Boolean>
  delete(@Parameter(description = "Version number of Project") @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(
          description =
              "This is the Organization Identifier for the Project. By default, the Default Organization's Identifier is considered.")
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @DefaultValue(
          DEFAULT_ORG_IDENTIFIER) @OrgIdentifier String orgIdentifier) {
    return ResponseDTO.newResponse(projectService.delete(
        accountIdentifier, orgIdentifier, identifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @GET
  @Hidden
  @Path("all-projects")
  @ApiOperation(value = "Get ProjectDTO list", nickname = "getProjectDTOList", hidden = true)
  @Operation(operationId = "getProjectDTOList", summary = "Get ProjectDTO list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns complete list of accessible projects")
      },
      hidden = true)
  @InternalApi
  public ResponseDTO<List<ProjectDTO>>
  getProjectList(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = "This specifies if this Project has assigned modules.") @QueryParam(
          "hasModule") @DefaultValue("true") boolean hasModule,
      @Parameter(description = "Module type") @QueryParam(
          NGResourceFilterConstants.MODULE_TYPE_KEY) ModuleType moduleType,
      @Parameter(description = "Search Term") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Set<String> permittedOrgIds = organizationService.getPermittedOrganizations(accountIdentifier, orgIdentifier);
    ProjectFilterDTO projectFilterDTO = getProjectFilterDTO(searchTerm, permittedOrgIds, hasModule, moduleType);
    return ResponseDTO.newResponse(projectService.listPermittedProjects(accountIdentifier, projectFilterDTO));
  }

  @GET
  @Hidden
  @Path("projects-count")
  @ApiOperation(
      value = "Get total count of projects accessible to a user", nickname = "getPermittedProjectsCount", hidden = true)
  @Operation(operationId = "getPermittedProjectsCount", summary = "Get total count of projects accessible to a user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns count of accessible projects")
      },
      hidden = true)
  @InternalApi
  public ResponseDTO<ActiveProjectsCountDTO>
  getAccessibleProjectsCount(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                                 NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = "This specifies if this Project has assigned modules.", required = false) @QueryParam(
          "hasModule") @DefaultValue("true") boolean hasModule,
      @Parameter(description = "Module type") @QueryParam(
          NGResourceFilterConstants.MODULE_TYPE_KEY) ModuleType moduleType,
      @Parameter(description = "Search Term") @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Start time") @NotNull @QueryParam(
          NGResourceFilterConstants.START_TIME) long startInterval,
      @Parameter(description = "End time") @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    Set<String> permittedOrgIds = organizationService.getPermittedOrganizations(accountIdentifier, orgIdentifier);
    ProjectFilterDTO projectFilterDTO = getProjectFilterDTO(searchTerm, permittedOrgIds, hasModule, moduleType);
    return ResponseDTO.newResponse(
        projectService.permittedProjectsCount(accountIdentifier, projectFilterDTO, startInterval, endInterval));
  }

  private ProjectFilterDTO getProjectFilterDTO(
      String searchTerm, Set<String> orgIdentifiers, boolean hasModule, ModuleType moduleType) {
    return ProjectFilterDTO.builder()
        .searchTerm(searchTerm)
        .orgIdentifiers(orgIdentifiers)
        .hasModule(hasModule)
        .moduleType(moduleType)
        .build();
  }
}
