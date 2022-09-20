/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.ProjectMapper.toProject;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.utils.PageTestUtils.getPage;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.model.CreateProjectRequest;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.spec.server.ng.model.UpdateProjectRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class ProjectApiImplTest extends CategoryTest {
  private ProjectService projectService;
  private OrganizationService organizationService;
  private AccessControlClient accessControlClient;
  private AccountProjectApiImpl accountProjectApi;
  private OrgProjectApiImpl orgProjectApi;
  private Validator validator;
  private ProjectApiMapper projectApiMapper;

  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  int page = 0;
  int limit = 1;

  @Before
  public void setup() {
    projectService = mock(ProjectService.class);
    organizationService = mock(OrganizationService.class);
    accessControlClient = mock(AccessControlClient.class);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    projectApiMapper = new ProjectApiMapper(validator);

    accountProjectApi = new AccountProjectApiImpl(projectService, projectApiMapper);
    orgProjectApi = new OrgProjectApiImpl(projectService, projectApiMapper);
  }

  private ProjectDTO getProjectDTO(String orgIdentifier, String identifier, String name) {
    return ProjectDTO.builder().orgIdentifier(orgIdentifier).identifier(identifier).name(name).build();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAccountScopedProjectCreate() {
    CreateProjectRequest request = new CreateProjectRequest();
    io.harness.spec.server.ng.model.Project proj = new io.harness.spec.server.ng.model.Project();
    proj.setSlug(slug);
    proj.setName(name);
    request.setProject(proj);

    ProjectDTO projectDTO = projectApiMapper.getProjectDto(request);
    Project project = toProject(projectDTO);
    project.setVersion(0L);

    when(projectService.create(account, null, projectDTO)).thenReturn(project);

    Response response = accountProjectApi.createAccountScopedProject(request, account);
    assertEquals(201, response.getStatus());

    assertEquals(project.getVersion().toString(), response.getEntityTag().getValue());
    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(slug, entity.getProject().getSlug());
    assertNull(entity.getProject().getOrg());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOrgScopedProjectCreate() {
    CreateProjectRequest request = new CreateProjectRequest();
    io.harness.spec.server.ng.model.Project proj = new io.harness.spec.server.ng.model.Project();
    proj.setSlug(slug);
    proj.setName(name);
    proj.setOrg(org);
    request.setProject(proj);

    ProjectDTO projectDTO = projectApiMapper.getProjectDto(request);
    Project project = toProject(projectDTO);
    project.setVersion(0L);

    when(projectService.create(account, org, projectDTO)).thenReturn(project);

    Response response = orgProjectApi.createOrgScopedProject(request, org, account);
    assertEquals(201, response.getStatus());

    assertEquals(project.getVersion().toString(), response.getEntityTag().getValue());
    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(slug, entity.getProject().getSlug());
    assertEquals(org, entity.getProject().getOrg());
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAccountScopedProjectNotFoundException() {
    accountProjectApi.getAccountScopedProject(slug, account);
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgScopedProjectNotFoundException() {
    orgProjectApi.getOrgScopedProject(org, slug, account);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAccountScopedProject() {
    Project project = Project.builder().identifier(slug).name(name).version(0L).build();
    when(projectService.get(account, null, slug)).thenReturn(Optional.of(project));

    Response response = accountProjectApi.getAccountScopedProject(slug, account);

    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(slug, entity.getProject().getSlug());
    assertEquals(project.getVersion().toString(), response.getEntityTag().getValue());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgScopedProject() {
    Project project = Project.builder().identifier(slug).name(name).orgIdentifier(org).version(0L).build();
    when(projectService.get(account, org, slug)).thenReturn(Optional.of(project));

    Response response = orgProjectApi.getOrgScopedProject(org, slug, account);

    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(slug, entity.getProject().getSlug());
    assertEquals(org, entity.getProject().getOrg());
    assertEquals(project.getVersion().toString(), response.getEntityTag().getValue());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAccountScopedProjectList() {
    String searchTerm = randomAlphabetic(10);
    ProjectDTO projectDTO = getProjectDTO(org, slug, name);
    projectDTO.setModules(Collections.singletonList(ModuleType.CD));
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);

    when(projectService.listPermittedProjects(eq(account), any(), any()))
        .thenReturn(getPage(Collections.singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(
            AccessCheckResponseDTO.builder()
                .accessControlList(Collections.singletonList(AccessControlDTO.builder()
                                                                 .resourceIdentifier(null)
                                                                 .resourceScope(ResourceScope.of(account, org, null))
                                                                 .permitted(true)
                                                                 .build()))
                .build());

    Response response = accountProjectApi.getAccountScopedProjects(account, Collections.singletonList(org),
        Collections.singletonList(slug), true, io.harness.spec.server.ng.model.ModuleType.CD.name(), searchTerm, page,
        limit);

    verify(projectService, times(1)).listPermittedProjects(eq(account), any(), argumentCaptor.capture());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    List<ProjectResponse> entity = (List<ProjectResponse>) response.getEntity();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(org, entity.get(0).getProject().getOrg());
    assertEquals(slug, entity.get(0).getProject().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgScopedProjectList() {
    String searchTerm = randomAlphabetic(10);
    ProjectDTO projectDTO = getProjectDTO(org, slug, name);
    projectDTO.setModules(Collections.singletonList(ModuleType.CD));
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);

    when(projectService.listPermittedProjects(eq(account), any(), any()))
        .thenReturn(getPage(Collections.singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(
            AccessCheckResponseDTO.builder()
                .accessControlList(Collections.singletonList(AccessControlDTO.builder()
                                                                 .resourceIdentifier(null)
                                                                 .resourceScope(ResourceScope.of(account, org, null))
                                                                 .permitted(true)
                                                                 .build()))
                .build());

    Response response = orgProjectApi.getOrgScopedProjects(org, account, Collections.singletonList(slug), true,
        io.harness.spec.server.ng.model.ModuleType.CD.name(), searchTerm, page, limit);

    verify(projectService, times(1)).listPermittedProjects(eq(account), any(), argumentCaptor.capture());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    List<ProjectResponse> entity = (List<ProjectResponse>) response.getEntity();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(org, entity.get(0).getProject().getOrg());
    assertEquals(slug, entity.get(0).getProject().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateAccountScopedProject() {
    UpdateProjectRequest request = new UpdateProjectRequest();
    io.harness.spec.server.ng.model.Project proj = new io.harness.spec.server.ng.model.Project();
    proj.setSlug(slug);
    proj.setName("updated_name");
    request.setProject(proj);

    ProjectDTO projectDTO = projectApiMapper.getProjectDto(request);
    Project project = toProject(projectDTO);
    project.setVersion(0L);

    when(projectService.update(account, null, slug, projectDTO)).thenReturn(project);

    Response response = accountProjectApi.updateAccountScopedProject(request, slug, account);

    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(project.getVersion().toString(), response.getEntityTag().getValue());
    assertNull(entity.getProject().getOrg());
    assertEquals(slug, entity.getProject().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateOrgScopedProject() {
    UpdateProjectRequest request = new UpdateProjectRequest();
    io.harness.spec.server.ng.model.Project proj = new io.harness.spec.server.ng.model.Project();
    proj.setSlug(slug);
    proj.setName("updated_name");
    proj.setOrg(org);
    request.setProject(proj);

    ProjectDTO projectDTO = projectApiMapper.getProjectDto(request);
    Project project = toProject(projectDTO);
    project.setVersion(0L);

    when(projectService.update(account, org, slug, projectDTO)).thenReturn(project);

    Response response = orgProjectApi.updateOrgScopedProject(request, org, slug, account);

    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(project.getVersion().toString(), response.getEntityTag().getValue());
    assertEquals(org, entity.getProject().getOrg());
    assertEquals(slug, entity.getProject().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAccountScopedProjectDelete() {
    when(projectService.delete(account, null, slug, null)).thenReturn(true);
    Project project = Project.builder().identifier(slug).name(name).build();
    when(projectService.get(account, null, slug)).thenReturn(Optional.of(project));

    Response response = accountProjectApi.deleteAccountScopedProject(slug, account);

    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(slug, entity.getProject().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOrgScopedProjectDelete() {
    Project project = Project.builder().identifier(slug).name(name).build();

    when(projectService.delete(account, org, slug, null)).thenReturn(true);
    when(projectService.get(account, org, slug)).thenReturn(Optional.of(project));

    Response response = orgProjectApi.deleteOrgScopedProject(org, slug, account);

    ProjectResponse entity = (ProjectResponse) response.getEntity();

    assertEquals(slug, entity.getProject().getSlug());
  }
}
