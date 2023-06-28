/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.ProjectMapper.toProject;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.utils.PageTestUtils.getPage;

import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.rule.Owner;
import io.harness.utils.UserHelperService;

import java.util.Collections;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class ProjectResourceTest extends CategoryTest {
  private ProjectService projectService;
  private OrganizationService organizationService;
  private AccessControlClient accessControlClient;
  private ProjectResource projectResource;

  private FavoritesService favoritesService;
  private UserHelperService userHelperService;

  String accountIdentifier = randomAlphabetic(10);
  String orgIdentifier = randomAlphabetic(10);
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    projectService = mock(ProjectService.class);
    organizationService = mock(OrganizationService.class);
    accessControlClient = mock(AccessControlClient.class);
    favoritesService = mock(FavoritesService.class);
    userHelperService = mock(UserHelperService.class);
    projectResource = new ProjectResource(projectService, organizationService, favoritesService, userHelperService);
  }

  private ProjectDTO getProjectDTO(String orgIdentifier, String identifier, String name) {
    return ProjectDTO.builder().orgIdentifier(orgIdentifier).identifier(identifier).name(name).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    ProjectRequest projectRequestWrapper = ProjectRequest.builder().project(projectDTO).build();
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);

    when(projectService.create(accountIdentifier, orgIdentifier, projectDTO)).thenReturn(project);
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    ResponseDTO<ProjectResponse> responseDTO =
        projectResource.create(accountIdentifier, orgIdentifier, projectRequestWrapper);

    assertEquals(project.getVersion().toString(), responseDTO.getEntityTag());
    assertEquals(orgIdentifier, responseDTO.getData().getProject().getOrgIdentifier());
    assertEquals(identifier, responseDTO.getData().getProject().getIdentifier());
    assertEquals(Boolean.FALSE, responseDTO.getData().getIsFavorite());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    ProjectRequest projectRequestWrapper = ProjectRequest.builder().project(projectDTO).build();
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);

    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.of(project));
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    ResponseDTO<ProjectResponse> responseDTO = projectResource.get(identifier, accountIdentifier, orgIdentifier);

    assertEquals(project.getVersion().toString(), responseDTO.getEntityTag());
    assertEquals(orgIdentifier, responseDTO.getData().getProject().getOrgIdentifier());
    assertEquals(identifier, responseDTO.getData().getProject().getIdentifier());

    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.empty());

    boolean exceptionThrown = false;
    try {
      projectResource.get(identifier, accountIdentifier, orgIdentifier);
    } catch (NotFoundException exception) {
      exceptionThrown = true;
    }

    assertTrue(exceptionThrown);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response = projectResource.list(accountIdentifier, orgIdentifier, true,
        Collections.EMPTY_LIST, ModuleType.CD, searchTerm, Boolean.FALSE, pageRequest);

    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertEquals(1, response.getData().getPageItemCount());
    assertEquals(orgIdentifier, response.getData().getContent().get(0).getProject().getOrgIdentifier());
    assertEquals(identifier, response.getData().getContent().get(0).getProject().getIdentifier());
    assertEquals(Boolean.FALSE, response.getData().getContent().get(0).getIsFavorite());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testListWithFavorites() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    when(favoritesService.getFavorites(accountIdentifier, orgIdentifier, null, null, ResourceType.PROJECT.toString()))
        .thenReturn(Collections.singletonList(
            Favorite.builder().resourceIdentifier(project.getIdentifier()).resourceType(ResourceType.PROJECT).build()));

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response = projectResource.list(accountIdentifier, orgIdentifier, true,
        Collections.EMPTY_LIST, ModuleType.CD, searchTerm, Boolean.FALSE, pageRequest);

    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertEquals(1, response.getData().getPageItemCount());
    assertEquals(orgIdentifier, response.getData().getContent().get(0).getProject().getOrgIdentifier());
    assertEquals(identifier, response.getData().getContent().get(0).getProject().getIdentifier());
    assertEquals(Boolean.TRUE, response.getData().getContent().get(0).getIsFavorite());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    String ifMatch = "0";
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    ProjectRequest projectRequestWrapper = ProjectRequest.builder().project(projectDTO).build();
    Project project = toProject(projectDTO);
    project.setVersion(parseLong(ifMatch) + 1);

    when(projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO)).thenReturn(project);

    ResponseDTO<ProjectResponse> response =
        projectResource.update(ifMatch, identifier, accountIdentifier, orgIdentifier, projectRequestWrapper);

    assertEquals("1", response.getEntityTag());
    assertEquals(orgIdentifier, response.getData().getProject().getOrgIdentifier());
    assertEquals(identifier, response.getData().getProject().getIdentifier());
    when(favoritesService.getFavorites(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String ifMatch = "0";

    when(projectService.delete(accountIdentifier, orgIdentifier, identifier, Long.valueOf(ifMatch))).thenReturn(true);

    ResponseDTO<Boolean> response = projectResource.delete(ifMatch, identifier, accountIdentifier, orgIdentifier);

    assertNull(response.getEntityTag());
    assertTrue(response.getData());
  }
}
