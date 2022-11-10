/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGCommonEntityConstants.DIFFERENT_SLUG_IN_PAYLOAD_AND_PARAM;
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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
      @OrgIdentifier String org, @ResourceIdentifier String slug, @AccountIdentifier String account) {
    return deleteProject(slug, account, org);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public Response getOrgScopedProject(
      @OrgIdentifier String org, @ResourceIdentifier String slug, @AccountIdentifier String account) {
    return getProject(slug, account, org);
  }

  @Override
  public Response getOrgScopedProjects(String org, List<String> projects, Boolean hasModule, String moduleType,
      String searchTerm, Integer page, Integer limit, String account, String sort, String order) {
    List<ProjectResponse> projectResponses = getProjects(account, org == null ? null : Sets.newHashSet(org), projects,
        hasModule, moduleType == null ? null : ModuleType.fromString(moduleType), searchTerm, page, limit, sort, order);

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks = projectApiUtils.addLinksHeader(
        responseBuilder, format("/v1/orgs/%s/projects", org), projectResponses.size(), page, limit);

    return responseBuilderWithLinks.entity(projectResponses).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  @Override
  public Response updateOrgScopedProject(UpdateProjectRequest updateProjectRequest, @OrgIdentifier String org,
      @ResourceIdentifier String project, @AccountIdentifier String account) {
    if (!Objects.equals(updateProjectRequest.getProject().getOrg(), org)) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (!Objects.equals(updateProjectRequest.getProject().getSlug(), project)) {
      throw new InvalidRequestException(DIFFERENT_SLUG_IN_PAYLOAD_AND_PARAM, USER);
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

  private Response updateProject(String slug, UpdateProjectRequest updateProjectRequest, String account, String org) {
    Project updatedProject =
        projectService.update(account, org, slug, projectApiUtils.getProjectDto(updateProjectRequest));
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(updatedProject);

    return Response.ok().entity(projectResponse).tag(updatedProject.getVersion().toString()).build();
  }

  private Response getProject(String slug, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, slug);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with org [%s] and slug [%s] not found", org, slug));
    }
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(projectOptional.get());

    return Response.ok().entity(projectResponse).tag(projectOptional.get().getVersion().toString()).build();
  }

  private List<ProjectResponse> getProjects(String account, Set<String> orgs, List<String> projects, Boolean hasModule,
      ModuleType moduleType, String searchTerm, Integer page, Integer limit, String sort, String order) {
    ProjectFilterDTO projectFilterDTO = ProjectFilterDTO.builder()
                                            .searchTerm(searchTerm)
                                            .orgIdentifiers(orgs)
                                            .hasModule(hasModule)
                                            .moduleType(moduleType)
                                            .identifiers(projects)
                                            .build();
    Page<Project> projectPages = projectService.listPermittedProjects(
        account, projectApiUtils.getPageRequest(page, limit, sort, order), projectFilterDTO);

    Page<ProjectResponse> projectResponsePage = projectPages.map(projectApiUtils::getProjectResponse);

    return new ArrayList<>(projectResponsePage.getContent());
  }

  private Response deleteProject(String slug, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, slug);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with org [%s] and slug [%s] not found", org, slug));
    }
    boolean deleted = projectService.delete(account, org, slug, null);
    if (!deleted) {
      throw new NotFoundException(format("Project with slug [%s] could not be deleted", slug));
    }
    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(projectOptional.get());

    return Response.ok().entity(projectResponse).build();
  }
}