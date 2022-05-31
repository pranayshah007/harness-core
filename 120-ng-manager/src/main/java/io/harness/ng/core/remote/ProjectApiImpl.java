/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.ng.core.remote.ProjectApiMapper.getPageRequest;
import static io.harness.ng.core.remote.ProjectApiMapper.getProjectDto;
import static io.harness.ng.core.remote.ProjectApiMapper.getProjectResponse;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.ProjectsApi;
import io.harness.spec.server.ng.model.CreateProjectRequest;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.spec.server.ng.model.UpdateProjectRequest;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class ProjectApiImpl implements ProjectsApi {
  private final ProjectService projectService;

  @NGAccessControlCheck(resourceType = PROJECT, permission = CREATE_PROJECT_PERMISSION)
  @Override
  public ProjectResponse createProject(String account, String org, CreateProjectRequest project) {
    Project createdProject = projectService.create(account, org, getProjectDto(org, project));
    return getProjectResponse(createdProject);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public ProjectResponse getProject(String id, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, id);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(
          String.format("Project with orgIdentifier [%s] and identifier [%s] not found", org, id));
    }
    return getProjectResponse(projectOptional.get());
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Override
  public List<ProjectResponse> getProjects(String account, List org, List project, Boolean hasModule, String moduleType,
      String searchTerm, Integer page, Integer limit) {
    ProjectFilterDTO projectFilterDTO =
        ProjectFilterDTO.builder()
            .searchTerm(searchTerm)
            .orgIdentifiers(org == null ? null : Sets.newHashSet(org))
            .hasModule(hasModule)
            .moduleType(moduleType == null ? null : io.harness.ModuleType.fromString(moduleType))
            .identifiers(project)
            .build();
    Page<Project> projectPages =
        projectService.listPermittedProjects(account, getPageRequest(page, limit), projectFilterDTO);

    Page<ProjectResponse> projectResponsePage = projectPages.map(ProjectApiMapper::getProjectResponse);

    List<ProjectResponse> projectResponses = new ArrayList<>();
    projectResponses.addAll(projectResponsePage.getContent());

    return projectResponses;
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = EDIT_PROJECT_PERMISSION)
  @Override
  public ProjectResponse updateProject(
      String account, String org, String id, UpdateProjectRequest updateProjectRequest) {
    Project updatedProject = projectService.update(account, org, id, getProjectDto(org, id, updateProjectRequest));
    return getProjectResponse(updatedProject);
  }

  @NGAccessControlCheck(resourceType = PROJECT, permission = DELETE_PROJECT_PERMISSION)
  @Override
  public ProjectResponse deleteProject(String id, String account, String org) {
    Optional<Project> projectOptional = projectService.get(account, org, id);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(
          String.format("Project with orgIdentifier [%s] and identifier [%s] not found", org, id));
    }
    boolean deleted = projectService.delete(account, org, id, null);
    if (!deleted) {
      // TODO: Throw exception and possibly return un processable entity
    }
    return getProjectResponse(projectOptional.get());
  }
}
