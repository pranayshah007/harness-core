/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

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
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.AccountProjectApi;
import io.harness.spec.server.ng.model.CreateProjectRequest;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.spec.server.ng.model.UpdateProjectRequest;

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
public class AccountProjectApiImpl implements AccountProjectApi {
  private final ProjectService projectService;
  private final ProjectApiMapper projectApiMapper;

  @NGAccessControlCheck(resourceType = PROJECT, permission = CREATE_PROJECT_PERMISSION)
  @Override
  public Response createAccountScopedProject(
      CreateProjectRequest createProjectRequest, @AccountIdentifier String account) {
    return createProject(createProjectRequest, account);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = DELETE_PROJECT_PERMISSION)
  @Override
  public Response deleteAccountScopedProject(@ResourceIdentifier String id, @AccountIdentifier String account) {
    return deleteProject(id, account);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public Response getAccountScopedProject(@ResourceIdentifier String id, @AccountIdentifier String account) {
    return getProject(id, account);
  }

  @Override
  public Response getAccountScopedProjects(String account, List<String> org, List<String> project, Boolean hasModule,
      String moduleType, String searchTerm, Integer page, Integer limit) {
    List<ProjectResponse> projects = getProjects(account, org == null ? null : Sets.newHashSet(org), project, hasModule,
        moduleType == null ? null : ModuleType.fromString(moduleType), searchTerm, page, limit);

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        projectApiMapper.addLinksHeader(responseBuilder, "/v1/projects", projects.size(), page, limit);

    return responseBuilderWithLinks.entity(projects).build();
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  @Override
  public Response updateAccountScopedProject(UpdateProjectRequest updateProjectRequest,
      @ResourceIdentifier String project, @AccountIdentifier String account) {
    if (!Objects.equals(updateProjectRequest.getProject().getSlug(), project)) {
      throw new InvalidRequestException(
          "Account scoped request is having different secret slug in payload and param", USER);
    }
    if (Objects.nonNull(updateProjectRequest.getProject().getOrg())) {
      throw new InvalidRequestException("Org scoped request is having different org in payload and param", USER);
    }
    return updateProject(project, updateProjectRequest, account);
  }

  private Response createProject(CreateProjectRequest createProjectRequest, String account) {
    if (Objects.nonNull(createProjectRequest.getProject().getOrg())) {
      throw new InvalidRequestException("Account scoped request is having nonnull org in payload", USER);
    }
    Project createdProject = projectService.create(account, null, projectApiMapper.getProjectDto(createProjectRequest));
    ProjectResponse projectResponse = projectApiMapper.getProjectResponse(createdProject);

    return Response.status(Response.Status.CREATED)
        .entity(projectResponse)
        .tag(createdProject.getVersion().toString())
        .build();
  }

  private Response updateProject(String project, UpdateProjectRequest updateProjectRequest, String account) {
    Project updatedProject =
        projectService.update(account, null, project, projectApiMapper.getProjectDto(updateProjectRequest));
    ProjectResponse projectResponse = projectApiMapper.getProjectResponse(updatedProject);

    return Response.ok().entity(projectResponse).tag(updatedProject.getVersion().toString()).build();
  }

  private Response getProject(String id, String account) {
    Optional<Project> projectOptional = projectService.get(account, null, id);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with slug [%s] not found", id));
    }
    ProjectResponse projectResponse = projectApiMapper.getProjectResponse(projectOptional.get());

    return Response.ok().entity(projectResponse).tag(projectOptional.get().getVersion().toString()).build();
  }

  private List<ProjectResponse> getProjects(String account, Set<String> org, List<String> project, Boolean hasModule,
      ModuleType moduleType, String searchTerm, Integer page, Integer limit) {
    ProjectFilterDTO projectFilterDTO = ProjectFilterDTO.builder()
                                            .searchTerm(searchTerm)
                                            .orgIdentifiers(org)
                                            .hasModule(hasModule)
                                            .moduleType(moduleType)
                                            .identifiers(project)
                                            .build();
    Page<Project> projectPages =
        projectService.listPermittedProjects(account, projectApiMapper.getPageRequest(page, limit), projectFilterDTO);

    Page<ProjectResponse> projectResponsePage = projectPages.map(projectApiMapper::getProjectResponse);

    return new ArrayList<>(projectResponsePage.getContent());
  }

  private Response deleteProject(String id, String account) {
    Optional<Project> projectOptional = projectService.get(account, null, id);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(format("Project with slug [%s] not found", id));
    }
    boolean deleted = projectService.delete(account, null, id, null);
    if (!deleted) {
      throw new NotFoundException(format("Project with slug [%s] not found", id));
    }
    ProjectResponse projectResponse = projectApiMapper.getProjectResponse(projectOptional.get());

    return Response.ok().entity(projectResponse).build();
  }
}