/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.favorites.services.FavoritesService;
import io.harness.ng.core.dto.ProjectAggregateDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;
import io.harness.utils.UserHelperService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class AggregateProjectServiceImplTest extends CategoryTest {
  private ProjectService projectService;
  private OrganizationService organizationService;
  private NgUserService ngUserService;
  private AggregateProjectServiceImpl aggregateProjectService;
  private FavoritesService favoritesService;
  private UserHelperService userHelperService;

  @Before
  public void setup() {
    projectService = mock(ProjectService.class);
    organizationService = mock(OrganizationService.class);
    ngUserService = mock(NgUserService.class);
    favoritesService = mock(FavoritesService.class);
    userHelperService = mock(UserHelperService.class);

    ExecutorService executorService = Executors.newFixedThreadPool(1);
    aggregateProjectService = spy(new AggregateProjectServiceImpl(
        projectService, organizationService, ngUserService, executorService, userHelperService));
  }

  private Project getProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Project.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .identifier(projectIdentifier)
        .name(randomAlphabetic(10))
        .build();
  }

  private Optional<Organization> getOrganization(String accountIdentifier, String orgIdentifier) {
    return Optional.of(Organization.builder()
                           .accountIdentifier(accountIdentifier)
                           .identifier(orgIdentifier)
                           .name(randomAlphabetic(10))
                           .build());
  }

  private void setupNgUserService() {
    List<UserMetadataDTO> userMetadataDTOs = new ArrayList<>();
    IntStream.range(0, 8).forEach(
        e -> userMetadataDTOs.add(UserMetadataDTO.builder().uuid(randomAlphabetic(10)).build()));
    when(ngUserService.listUsers(any())).thenReturn(userMetadataDTOs);
    when(ngUserService.listUsersHavingRole(any(), any())).thenReturn(new ArrayList<>(userMetadataDTOs.subList(0, 4)));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    Project project = getProject(accountIdentifier, orgIdentifier, projectIdentifier);
    when(projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(Optional.of(project));

    Optional<Organization> organizationOpt = getOrganization(accountIdentifier, orgIdentifier);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(organizationOpt);
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
    setupNgUserService();

    ProjectAggregateDTO projectAggregateDTO =
        aggregateProjectService.getProjectAggregateDTO(accountIdentifier, orgIdentifier, projectIdentifier);

    // project
    assertEquals(orgIdentifier, projectAggregateDTO.getProjectResponse().getProject().getOrgIdentifier());
    assertEquals(projectIdentifier, projectAggregateDTO.getProjectResponse().getProject().getIdentifier());
    assertEquals(project.getName(), projectAggregateDTO.getProjectResponse().getProject().getName());

    // organization
    assertEquals(orgIdentifier, projectAggregateDTO.getOrganization().getIdentifier());
    assertEquals(organizationOpt.get().getName(), projectAggregateDTO.getOrganization().getName());

    // admins and collaborators
    assertEquals(4, projectAggregateDTO.getAdmins().size());
    assertEquals(4, projectAggregateDTO.getCollaborators().size());
    assertEquals(Boolean.FALSE, projectAggregateDTO.getProjectResponse().getIsFavorite());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet_OtherFieldsMissing() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    Project project = getProject(accountIdentifier, orgIdentifier, projectIdentifier);
    when(projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(Optional.of(project));

    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.empty());

    when(ngUserService.listUsers(any())).thenReturn(emptyList());

    when(ngUserService.listCurrentGenUsers(any(), any())).thenReturn(emptyList());

    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    ProjectAggregateDTO projectAggregateDTO =
        aggregateProjectService.getProjectAggregateDTO(accountIdentifier, orgIdentifier, projectIdentifier);

    // project
    assertEquals(orgIdentifier, projectAggregateDTO.getProjectResponse().getProject().getOrgIdentifier());
    assertEquals(projectIdentifier, projectAggregateDTO.getProjectResponse().getProject().getIdentifier());
    assertEquals(project.getName(), projectAggregateDTO.getProjectResponse().getProject().getName());
    assertEquals(Boolean.FALSE, projectAggregateDTO.getProjectResponse().getIsFavorite());

    // organization
    assertNull(projectAggregateDTO.getOrganization());

    // admins and collaborators
    assertEquals(0, projectAggregateDTO.getAdmins().size());
    assertEquals(0, projectAggregateDTO.getCollaborators().size());
  }

  private List<Project> getProjects(String accountIdentifier, String orgIdentifier, int count) {
    List<Project> projects = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      projects.add(getProject(accountIdentifier, orgIdentifier, randomAlphabetic(10)));
    }
    return projects;
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier1 = randomAlphabetic(10);
    String orgIdentifier2 = randomAlphabetic(10);

    List<Project> projects = getProjects(accountIdentifier, orgIdentifier1, 2);
    projects.addAll(getProjects(accountIdentifier, orgIdentifier2, 3));
    when(projectService.listPermittedProjects(accountIdentifier, Pageable.unpaged(), null, Boolean.FALSE))
        .thenReturn(getPage(projects, 5));
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
    when(organizationService.get(accountIdentifier, orgIdentifier1))
        .thenReturn(getOrganization(accountIdentifier, orgIdentifier1));
    when(organizationService.get(accountIdentifier, orgIdentifier2))
        .thenReturn(getOrganization(accountIdentifier, orgIdentifier2));
    setupNgUserService();

    Page<ProjectAggregateDTO> projectAggregateDTOs =
        aggregateProjectService.listProjectAggregateDTO(accountIdentifier, Pageable.unpaged(), null, Boolean.FALSE);

    // projects
    assertEquals(5, projectAggregateDTOs.getContent().size());

    // organizations
    projectAggregateDTOs.getContent().forEach(projectAggregateDTO
        -> assertEquals(projectAggregateDTO.getProjectResponse().getProject().getOrgIdentifier(),
            projectAggregateDTO.getOrganization().getIdentifier()));
    // Favorites wrapper
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertFalse(projectAggregateDTO.getProjectResponse().getIsFavorite()));

    // admins and collaborators
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertEquals(4, projectAggregateDTO.getAdmins().size()));
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertEquals(4, projectAggregateDTO.getCollaborators().size()));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testListWithFavorites() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier1 = randomAlphabetic(10);
    String orgIdentifier2 = randomAlphabetic(10);
    String favProjectIdentifier = randomAlphabetic(10);
    String userIdentifier = randomAlphabetic(10);
    Project favProject = getProject(accountIdentifier, orgIdentifier1, favProjectIdentifier);
    List<Project> projects = getProjects(accountIdentifier, orgIdentifier1, 2);
    projects.add(favProject);
    projects.addAll(getProjects(accountIdentifier, orgIdentifier2, 3));
    when(projectService.listPermittedProjects(accountIdentifier, Pageable.unpaged(), null, Boolean.FALSE))
        .thenReturn(getPage(projects, 6));
    when(userHelperService.getUserId()).thenReturn(userIdentifier);
    when(projectService.isFavorite(favProject, userIdentifier)).thenReturn(Boolean.TRUE);
    when(organizationService.get(accountIdentifier, orgIdentifier1))
        .thenReturn(getOrganization(accountIdentifier, orgIdentifier1));
    when(organizationService.get(accountIdentifier, orgIdentifier2))
        .thenReturn(getOrganization(accountIdentifier, orgIdentifier2));
    setupNgUserService();

    Page<ProjectAggregateDTO> projectAggregateDTOs =
        aggregateProjectService.listProjectAggregateDTO(accountIdentifier, Pageable.unpaged(), null, Boolean.FALSE);

    // projects
    assertEquals(6, projectAggregateDTOs.getContent().size());

    // organizations
    projectAggregateDTOs.getContent().forEach(projectAggregateDTO
        -> assertEquals(projectAggregateDTO.getProjectResponse().getProject().getOrgIdentifier(),
            projectAggregateDTO.getOrganization().getIdentifier()));
    // Favorites wrapper
    assertEquals(
        projectAggregateDTOs.getContent()
            .stream()
            .filter(
                projectAggregateDTO -> projectAggregateDTO.getProjectResponse().getIsFavorite().equals(Boolean.TRUE))
            .collect(Collectors.toList())
            .get(0)
            .getProjectResponse()
            .getProject()
            .getIdentifier(),
        favProjectIdentifier);

    // admins and collaborators
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertEquals(4, projectAggregateDTO.getAdmins().size()));
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertEquals(4, projectAggregateDTO.getCollaborators().size()));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList_OtherFieldsMissing() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier1 = randomAlphabetic(10);
    String orgIdentifier2 = randomAlphabetic(10);

    List<Project> projects = getProjects(accountIdentifier, orgIdentifier1, 2);
    projects.addAll(getProjects(accountIdentifier, orgIdentifier2, 3));
    when(projectService.listPermittedProjects(accountIdentifier, Pageable.unpaged(), null, Boolean.FALSE))
        .thenReturn(getPage(projects, 5));
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
    when(organizationService.get(any(), any())).thenReturn(Optional.empty());

    when(ngUserService.listUsers(any())).thenReturn(emptyList());
    when(ngUserService.listUsersHavingRole(any(), any())).thenReturn(emptyList());

    Page<ProjectAggregateDTO> projectAggregateDTOs =
        aggregateProjectService.listProjectAggregateDTO(accountIdentifier, Pageable.unpaged(), null, Boolean.FALSE);

    // projects
    assertEquals(5, projectAggregateDTOs.getContent().size());

    // organizations
    projectAggregateDTOs.getContent().forEach(projectAggregateDTO -> assertNull(projectAggregateDTO.getOrganization()));

    // admins and collaborators
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertEquals(0, projectAggregateDTO.getAdmins().size()));
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertEquals(0, projectAggregateDTO.getCollaborators().size()));
    projectAggregateDTOs.getContent().forEach(
        projectAggregateDTO -> assertFalse(projectAggregateDTO.getProjectResponse().getIsFavorite()));
  }
}
