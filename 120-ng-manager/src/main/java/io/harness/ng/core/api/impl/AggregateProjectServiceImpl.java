/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.api.AggregateProjectService;
import io.harness.ng.core.dto.ProjectAggregateDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.remote.OrganizationMapper;
import io.harness.ng.core.remote.ProjectMapper;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.utils.UserHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
@Singleton
@Slf4j
public class AggregateProjectServiceImpl implements AggregateProjectService {
  private static final String PROJECT_ADMIN_ROLE = "_project_admin";
  private final ProjectService projectService;
  private final OrganizationService organizationService;
  private final NgUserService ngUserService;
  private final ExecutorService executorService;
  private final UserHelperService userHelperService;

  @Inject
  public AggregateProjectServiceImpl(ProjectService projectService, OrganizationService organizationService,
      NgUserService ngUserService, @Named("aggregate-projects") ExecutorService executorService,
      UserHelperService userHelperService) {
    this.projectService = projectService;
    this.organizationService = organizationService;
    this.ngUserService = ngUserService;
    this.executorService = executorService;
    this.userHelperService = userHelperService;
  }

  @Override
  public ProjectAggregateDTO getProjectAggregateDTO(String accountIdentifier, String orgIdentifier, String identifier) {
    Optional<Project> projectOptional = projectService.get(accountIdentifier, orgIdentifier, identifier);
    if (!projectOptional.isPresent()) {
      throw new NotFoundException(
          String.format("Project with orgIdentifier [%s] and identifier [%s] not found", orgIdentifier, identifier));
    }
    return buildAggregateDTO(projectOptional.get());
  }

  @Override
  public Page<ProjectAggregateDTO> listProjectAggregateDTO(
      String accountIdentifier, Pageable pageable, ProjectFilterDTO projectFilterDTO, Boolean onlyFavorites) {
    Page<Project> permittedProjects =
        projectService.listPermittedProjects(accountIdentifier, pageable, projectFilterDTO, onlyFavorites);
    List<Project> projectList = permittedProjects.getContent();

    List<Callable<ProjectAggregateDTO>> tasks = new ArrayList<>();
    projectList.forEach(project -> tasks.add(() -> buildAggregateDTO(project)));

    List<Future<ProjectAggregateDTO>> futures;
    try {
      futures = executorService.invokeAll(tasks, 10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Page.empty();
    }

    List<ProjectAggregateDTO> aggregates = new ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
      try {
        aggregates.add(futures.get(i).get());
      } catch (CancellationException e) {
        Project project = projectList.get(i);
        log.error("Project aggregate task cancelled for project [{}/{}/{}]", project.getAccountIdentifier(),
            project.getOrgIdentifier(), project.getIdentifier(), e);
        aggregates.add(
            ProjectAggregateDTO.builder()
                .projectResponse(ProjectMapper.toProjectResponseBuilder(project)
                                     .isFavorite(projectService.isFavorite(project, userHelperService.getUserId()))
                                     .build())
                .build());
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return Page.empty();
      } catch (ExecutionException e) {
        Project project = projectList.get(i);
        log.error("Error occurred while computing aggregate for project [{}/{}/{}]", project.getAccountIdentifier(),
            project.getOrgIdentifier(), project.getIdentifier(), e);
        aggregates.add(
            ProjectAggregateDTO.builder()
                .projectResponse(ProjectMapper.toProjectResponseBuilder(project)
                                     .isFavorite(projectService.isFavorite(project, userHelperService.getUserId()))
                                     .build())
                .build());
      }
    }

    return new PageImpl<>(aggregates, permittedProjects.getPageable(), permittedProjects.getTotalElements());
  }

  private ProjectAggregateDTO buildAggregateDTO(Project project) {
    Optional<Organization> organizationOptional =
        organizationService.get(project.getAccountIdentifier(), project.getOrgIdentifier());
    Scope projectScope = Scope.builder()
                             .accountIdentifier(project.getAccountIdentifier())
                             .orgIdentifier(project.getOrgIdentifier())
                             .projectIdentifier(project.getIdentifier())
                             .build();

    List<UserMetadataDTO> collaborators = ngUserService.listUsers(projectScope);
    List<UserMetadataDTO> projectAdmins = ngUserService.listUsersHavingRole(projectScope, PROJECT_ADMIN_ROLE);
    collaborators.removeAll(projectAdmins);

    return ProjectAggregateDTO.builder()
        .projectResponse(ProjectMapper.toProjectResponseBuilder(project)
                             .isFavorite(projectService.isFavorite(project, userHelperService.getUserId()))
                             .build())
        .organization(organizationOptional.map(OrganizationMapper::writeDto).orElse(null))
        .harnessManagedOrg(organizationOptional.map(Organization::getHarnessManaged).orElse(Boolean.FALSE))
        .admins(projectAdmins)
        .collaborators(collaborators)
        .build();
  }
}
