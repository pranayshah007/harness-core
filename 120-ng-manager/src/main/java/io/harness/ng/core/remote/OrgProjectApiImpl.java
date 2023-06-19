/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGCommonEntityConstants.DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.OrgProjectApi;
import io.harness.spec.server.ng.v1.model.CreateProjectRequest;
import io.harness.spec.server.ng.v1.model.ProjectResponse;
import io.harness.spec.server.ng.v1.model.UpdateProjectRequest;
import io.harness.utils.ApiUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.Max;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class OrgProjectApiImpl implements OrgProjectApi {
  private final ProjectService projectService;
  private final ProjectApiUtils projectApiUtils;

  @NGAccessControlCheck(resourceType = PROJECT, permission = CREATE_PROJECT_PERMISSION)
  @Override
  public Response createOrgScopedProject(
      CreateProjectRequest createProjectRequest, @OrgIdentifier String org, @AccountIdentifier String account) {
    return createProject(createProjectRequest, account, org);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = DELETE_PROJECT_PERMISSION)
  @Override
  public Response deleteOrgScopedProject(
      @OrgIdentifier String org, @ResourceIdentifier String identifier, @AccountIdentifier String account) {
    return deleteProject(identifier, account, org);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public Response getOrgScopedProject(
      @OrgIdentifier String org, @ResourceIdentifier String identifier, @AccountIdentifier String account) {
    return getProject(identifier, account, org);
  }

  @Override
  public Response getOrgScopedProjects(String org, List<String> projects, Boolean hasModule, String moduleType,
      Boolean onlyFavorites, String searchTerm, Integer page, @Max(1000L) Integer limit, String account, String sort,
      String order) {
    Page<ProjectResponse> projectPageResponses = getProjects(account, org == null ? null : Sets.newHashSet(org),
        projects, hasModule, moduleType == null ? null : ModuleType.fromString(moduleType), searchTerm,
        onlyFavorites == null ? Boolean.FALSE : onlyFavorites, page, limit, sort, order);

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, projectPageResponses.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(projectPageResponses.getContent()).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  @Override
  public Response updateOrgScopedProject(UpdateProjectRequest updateProjectRequest, @OrgIdentifier String org,
      @ResourceIdentifier String project, @AccountIdentifier String account) {
    if (!Objects.equals(updateProjectRequest.getProject().getOrg(), org)) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (!Objects.equals(updateProjectRequest.getProject().getIdentifier(), project)) {
      throw new InvalidRequestException(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM, USER);
    }
    return updateProject(project, updateProjectRequest, account, org);
  }

  private Response createProject(CreateProjectRequest createProjectRequest, String account, String org) {
    if (!Objects.equals(org, createProjectRequest.getProject().getOrg())) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    Project createdProject = projectService.create(account, org, projectApiUtils.getProjectDto(createProjectRequest));
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(createdProject);

    return Response.status(Response.Status.CREATED)
        .entity(projectResponse)
        .tag(createdProject.getVersion().toString())
        .build();
  }

  private Response updateProject(
      String identifier, UpdateProjectRequest updateProjectRequest, String account, String org) {
    Project updatedProject =
        projectService.update(account, org, identifier, projectApiUtils.getProjectDto(updateProjectRequest));
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(updatedProject);

    return Response.ok().entity(projectResponse).tag(updatedProject.getVersion().toString()).build();
  }

  private Response getProject(String identifier, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, identifier);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with org [%s] and identifier [%s] not found", org, identifier));
    }
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(projectOptional.get());

    return Response.ok().entity(projectResponse).tag(projectOptional.get().getVersion().toString()).build();
  }

  private Page<ProjectResponse> getProjects(String account, Set<String> orgs, List<String> projects, Boolean hasModule,
      ModuleType moduleType, String searchTerm, Boolean onlyFavorites, Integer page, Integer limit, String sort,
      String order) {
    ProjectFilterDTO projectFilterDTO = ProjectFilterDTO.builder()
                                            .searchTerm(searchTerm)
                                            .orgIdentifiers(orgs)
                                            .hasModule(hasModule)
                                            .moduleType(moduleType)
                                            .identifiers(projects)
                                            .build();
    Page<Project> projectPages = projectService.listPermittedProjects(
        account, projectApiUtils.getPageRequest(page, limit, sort, order), projectFilterDTO, onlyFavorites);

    return projectPages.map(projectApiUtils::getProjectResponse);
  }

  private Response deleteProject(String identifier, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, identifier);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with org [%s] and identifier [%s] not found", org, identifier));
    }
    boolean deleted = projectService.delete(account, org, identifier, null);
    if (!deleted) {
      throw new NotFoundException(format("Project with identifier [%s] could not be deleted", identifier));
    }
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(projectOptional.get());

    return Response.ok().entity(projectResponse).build();
  }
}