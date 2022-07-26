/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.ng.core.remote.ProjectApiMapper.getPageRequest;
import static io.harness.ng.core.remote.ProjectApiMapper.getProjectDto;
import static io.harness.ng.core.remote.ProjectApiMapper.getProjectResponse;

import static java.lang.String.format;
import static javax.ws.rs.core.UriBuilder.fromPath;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.ProjectApi;
import io.harness.spec.server.ng.model.CreateProjectRequest;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.spec.server.ng.model.UpdateProjectRequest;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class ProjectApiImpl implements ProjectApi {
  private final ProjectService projectService;

  @NGAccessControlCheck(resourceType = PROJECT, permission = CREATE_PROJECT_PERMISSION)
  @Override
  public Response createAccountScopedProject(
      CreateProjectRequest createProjectRequest, @AccountIdentifier String account) {
    return Response.ok().entity(createProject(createProjectRequest, account, DEFAULT_ORG_IDENTIFIER)).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = CREATE_PROJECT_PERMISSION)
  @Override
  public Response createOrgScopedProject(
      @OrgIdentifier String org, CreateProjectRequest createProjectRequest, @AccountIdentifier String account) {
    return Response.ok().entity(createProject(createProjectRequest, account, org)).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = DELETE_PROJECT_PERMISSION)
  @Override
  public Response deleteAccountScopedProject(@ResourceIdentifier String id, @AccountIdentifier String account) {
    return Response.ok().entity(deleteProject(id, account, DEFAULT_ORG_IDENTIFIER)).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = DELETE_PROJECT_PERMISSION)
  @Override
  public Response deleteOrgScopedProject(
      @OrgIdentifier String org, @ResourceIdentifier String id, @AccountIdentifier String account) {
    return Response.ok().entity(deleteProject(id, account, org)).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public Response getAccountScopedProject(@ResourceIdentifier String id, @AccountIdentifier String account) {
    return Response.ok().entity(getProject(id, account, DEFAULT_ORG_IDENTIFIER)).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public Response getOrgScopedProject(
      @OrgIdentifier String org, @ResourceIdentifier String id, @AccountIdentifier String account) {
    return Response.ok().entity(getProject(id, account, org)).build();
  }

  @Override
  public Response getAccountScopedProjects(String account, List<String> org, List<String> project, Boolean hasModule,
      String moduleType, String searchTerm, Integer page, Integer limit) {
    List<ProjectResponse> projects = getProjects(account, org == null ? null : Sets.newHashSet(org), project, hasModule,
        moduleType == null ? null : ModuleType.fromString(moduleType), searchTerm, page, limit);

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        addLinksHeader(responseBuilder, "/v1/projects", projects.size(), page, limit);

    return responseBuilderWithLinks.entity(projects).build();
  }

  private ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= 1) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }

    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }

  @Override
  public Response getOrgScopedProjects(String org, String account, List<String> project, Boolean hasModule,
      String moduleType, String searchTerm, Integer page, Integer limit) {
    List<ProjectResponse> projects = getProjects(account, org == null ? null : Sets.newHashSet(org), project, hasModule,
        moduleType == null ? null : ModuleType.fromString(moduleType), searchTerm, page, limit);

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        addLinksHeader(responseBuilder, format("/v1/orgs/%s/projects)", org), projects.size(), page, limit);

    return responseBuilderWithLinks.entity(projects).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  @Override
  public Response updateAccountScopedProject(
      @ResourceIdentifier String id, UpdateProjectRequest updateProjectRequest, @AccountIdentifier String account) {
    return Response.ok().entity(updateProject(id, updateProjectRequest, account, DEFAULT_ORG_IDENTIFIER)).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  @Override
  public Response updateOrgScopedProject(@OrgIdentifier String org, @ResourceIdentifier String id,
      UpdateProjectRequest updateProjectRequest, @AccountIdentifier String account) {
    return Response.ok().entity(updateProject(id, updateProjectRequest, account, org)).build();
  }

  public ProjectResponse createProject(CreateProjectRequest project, String account, String org) {
    Project createdProject = projectService.create(account, org, getProjectDto(org, project));
    return getProjectResponse(createdProject);
  }

  public ProjectResponse updateProject(
      String id, UpdateProjectRequest updateProjectRequest, String account, String org) {
    Project updatedProject = projectService.update(account, org, id, getProjectDto(org, id, updateProjectRequest));
    return getProjectResponse(updatedProject);
  }

  public ProjectResponse getProject(String id, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, id);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with orgIdentifier [%s] and identifier [%s] not found", org, id));
    }
    return getProjectResponse(projectOptional.get());
  }

  public List<ProjectResponse> getProjects(String account, Set<String> org, List<String> project, Boolean hasModule,
      ModuleType moduleType, String searchTerm, Integer page, Integer limit) {
    ProjectFilterDTO projectFilterDTO = ProjectFilterDTO.builder()
                                            .searchTerm(searchTerm)
                                            .orgIdentifiers(org)
                                            .hasModule(hasModule)
                                            .moduleType(moduleType)
                                            .identifiers(project)
                                            .build();
    Page<Project> projectPages =
        projectService.listPermittedProjects(account, getPageRequest(page, limit), projectFilterDTO);

    Page<ProjectResponse> projectResponsePage = projectPages.map(ProjectApiMapper::getProjectResponse);

    return new ArrayList<>(projectResponsePage.getContent());
  }

  public ProjectResponse deleteProject(String id, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, id);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with orgIdentifier [%s] and identifier [%s] not found", org, id));
    }
    boolean deleted = projectService.delete(account, org, id, null);
    if (!deleted) {
      throw new NotFoundException(format("Project with orgIdentifier [%s] and identifier [%s] not found", org, id));
    }
    return getProjectResponse(projectOptional.get());
  }
}
