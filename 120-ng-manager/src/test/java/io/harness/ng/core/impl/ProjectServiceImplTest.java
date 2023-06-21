/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.ModuleType.CD;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.ng.core.remote.ProjectMapper.toProject;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.utils.PageTestUtils.getPage;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.Pageable.unpaged;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesService;
import io.harness.ff.FeatureFlagService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.beans.ProjectsPerOrganizationCount;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.remote.ProjectMapper;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.core.spring.ProjectRepository;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.helpers.ProjectInstrumentationHelper;
import io.harness.utils.UserHelperService;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ProjectServiceImplTest extends CategoryTest {
  @Mock private ProjectRepository projectRepository;
  @Mock private OrganizationService organizationService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;
  @Mock private NgUserService ngUserService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ScopeAccessHelper scopeAccessHelper;
  @Mock private YamlGitConfigService yamlGitConfigService;
  @InjectMocks ProjectInstrumentationHelper instrumentationHelper;
  private ProjectServiceImpl projectService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DefaultUserGroupService defaultUserGroupService;
  @Mock private FavoritesService favoritesService;
  @Mock private UserHelperService userHelperService;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    projectService = spy(new ProjectServiceImpl(projectRepository, organizationService, transactionTemplate,
        outboxService, ngUserService, accessControlClient, scopeAccessHelper, instrumentationHelper,
        yamlGitConfigService, featureFlagService, defaultUserGroupService, favoritesService, userHelperService));
    when(scopeAccessHelper.getPermittedScopes(any())).then(returnsFirstArg());
  }

  private ProjectDTO createProjectDTO(String orgIdentifier, String identifier) {
    return ProjectDTO.builder()
        .orgIdentifier(orgIdentifier)
        .identifier(identifier)
        .name(randomAlphabetic(10))
        .color(randomAlphabetic(10))
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateProject_CorrectPayload() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    setContextData(accountIdentifier);

    when(projectRepository.save(project)).thenReturn(project);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));

    projectService.create(accountIdentifier, orgIdentifier, projectDTO);
    try {
      verify(transactionTemplate, times(1)).execute(any());
      Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectDTO.getIdentifier());
      verify(defaultUserGroupService, times(1)).create(scope, emptyList());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void setContextData(String accountIdentifier) {
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal("user", "admin@harness.io", "user", accountIdentifier))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);
    GlobalContextManager.set(globalContext);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateProject_IncorrectPayload() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    setContextData(accountIdentifier);

    projectService.create(accountIdentifier, orgIdentifier + randomAlphabetic(1), projectDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateProject_Duplicate() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    setContextData(accountIdentifier);
    exceptionRule.expect(DuplicateFieldException.class);
    exceptionRule.expectMessage(
        String.format("A project with identifier [%s] and orgIdentifier [%s] is already present",
            project.getIdentifier(), orgIdentifier));
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(projectRepository.save(any())).thenThrow(new DuplicateKeyException("Key already exists"));

    projectService.create(accountIdentifier, orgIdentifier, projectDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateExistentProject() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String id = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, identifier);
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    project.setIdentifier(identifier);
    project.setId(id);
    when(projectRepository.save(any())).thenReturn(project);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));
    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.of(project));
    projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO);

    try {
      verify(transactionTemplate, times(1)).execute(any());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testUpdateProject_exitingCreatedAtAsNull() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    long lastModifiedTime = 1234567890;

    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, identifier);
    Project newProject = toProject(projectDTO);
    Project exitingProject = newProject;
    exitingProject.setAccountIdentifier(accountIdentifier);
    exitingProject.setOrgIdentifier(orgIdentifier);
    exitingProject.setIdentifier(identifier);
    exitingProject.setId(exitingProject.getId());
    exitingProject.setCreatedAt(null);
    exitingProject.setLastModifiedAt(lastModifiedTime);
    exitingProject.setDescription("description");
    setContextData(accountIdentifier);

    when(projectRepository.save(any())).thenReturn(newProject);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));
    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.of(exitingProject));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ArgumentCaptor<Project> updatedProjectCapture = ArgumentCaptor.forClass(Project.class);

    projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO);

    verify(projectRepository, times(1)).save(updatedProjectCapture.capture());

    Project updatedProject = updatedProjectCapture.getValue();
    assertThat(updatedProject.getCreatedAt()).isNotNull();
    assertThat(updatedProject.getCreatedAt()).isEqualTo(lastModifiedTime);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testUpdateProject_withValidExistingCreatedAt() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    long lastModifiedTime = 23456789;
    long createdAtTime = 1234567890;

    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, identifier);
    Project newProject = toProject(projectDTO);
    Project exitingProject = newProject;
    exitingProject.setAccountIdentifier(accountIdentifier);
    exitingProject.setOrgIdentifier(orgIdentifier);
    exitingProject.setIdentifier(identifier);
    exitingProject.setId(exitingProject.getId());
    exitingProject.setCreatedAt(createdAtTime);
    exitingProject.setLastModifiedAt(lastModifiedTime);
    exitingProject.setDescription("description");
    setContextData(accountIdentifier);

    when(projectRepository.save(any())).thenReturn(newProject);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));
    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.of(exitingProject));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ArgumentCaptor<Project> updatedProjectCapture = ArgumentCaptor.forClass(Project.class);

    projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO);

    verify(projectRepository, times(1)).save(updatedProjectCapture.capture());

    Project updatedProject = updatedProjectCapture.getValue();
    assertThat(updatedProject.getCreatedAt()).isNotNull();
    assertThat(updatedProject.getCreatedAt()).isEqualTo(createdAtTime);
  }
  @Test(expected = JerseyViolationException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateProject_IncorrectPayload() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, identifier);
    projectDTO.setName("");
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    project.setName(randomAlphabetic(10));
    project.setId(randomAlphabetic(10));
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));
    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.of(project));
    projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNonExistentProject() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, identifier);
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    project.setIdentifier(identifier);

    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(random(Organization.class)));
    when(projectService.get(accountIdentifier, orgIdentifier, identifier)).thenReturn(Optional.empty());

    Project updatedProject = projectService.update(accountIdentifier, orgIdentifier, identifier, projectDTO);

    assertNull(updatedProject);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListProject() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String searchTerm = randomAlphabetic(5);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(projectRepository.findAll(any(Criteria.class))).thenReturn(Collections.emptyList());
    when(projectRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(getPage(emptyList(), 0));

    Set<String> orgIdentifiers = Collections.singleton(orgIdentifier);
    Page<Project> projectPage = projectService.listPermittedProjects(accountIdentifier, unpaged(),
        ProjectFilterDTO.builder().orgIdentifiers(orgIdentifiers).searchTerm(searchTerm).moduleType(CD).build(),
        Boolean.FALSE);

    verify(projectRepository, times(1)).findAll(criteriaArgumentCaptor.capture());

    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaObject = criteria.getCriteriaObject();
    System.out.println(criteriaObject);
    assertEquals(5, criteriaObject.size());
    assertEquals(accountIdentifier, criteriaObject.get(ProjectKeys.accountIdentifier));
    assertTrue(criteriaObject.containsKey(ProjectKeys.orgIdentifier));
    assertTrue(criteriaObject.containsKey(ProjectKeys.deleted));
    assertTrue(criteriaObject.containsKey(ProjectKeys.modules));

    assertEquals(0, projectPage.getTotalElements());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testlistPermittedProjects() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String searchTerm = randomAlphabetic(5);
    Project project = Project.builder()
                          .id(randomAlphabetic(10))
                          .identifier(randomAlphabetic(10))
                          .accountIdentifier(accountIdentifier)
                          .orgIdentifier(orgIdentifier)
                          .build();
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(project.getIdentifier())
                      .build();
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(projectRepository.findAll(any(Criteria.class))).thenReturn(List.of(project));
    when(projectRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(getPage(List.of(project), 1));
    when(scopeAccessHelper.getPermittedScopes(any())).thenReturn(List.of(scope));

    Set<String> orgIdentifiers = Collections.singleton(orgIdentifier);
    Page<Project> projectPage = projectService.listPermittedProjects(accountIdentifier, unpaged(),
        ProjectFilterDTO.builder().orgIdentifiers(orgIdentifiers).searchTerm(searchTerm).moduleType(CD).build(),
        Boolean.FALSE);

    verify(projectRepository, times(1)).findAll(any(Criteria.class));
    verify(projectRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), any(Pageable.class));

    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(1).containsKey("id");
    Document query = (Document) criteriaObject.get("id");
    assertThat(query).hasSize(1).containsExactly(Map.entry("$in", List.of(project.getId())));
    System.out.println(criteriaObject);

    assertEquals(1, projectPage.getTotalElements());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testListFavoriteProjects() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String searchTerm = randomAlphabetic(5);
    Project project = Project.builder()
                          .id(randomAlphabetic(10))
                          .identifier(randomAlphabetic(10))
                          .accountIdentifier(accountIdentifier)
                          .orgIdentifier(orgIdentifier)
                          .build();
    Project project2 = Project.builder()
                           .id(randomAlphabetic(10))
                           .identifier(randomAlphabetic(10))
                           .accountIdentifier(accountIdentifier)
                           .orgIdentifier(orgIdentifier)
                           .build();
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(project.getIdentifier())
                      .build();
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(projectRepository.findAll(any(Criteria.class))).thenReturn(List.of(project, project2));
    when(projectRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(getPage(List.of(project2), 1));
    when(scopeAccessHelper.getPermittedScopes(any())).thenReturn(List.of(scope));
    when(favoritesService.getFavorites(accountIdentifier, null, null, null, ResourceType.PROJECT.toString()))
        .thenReturn(List.of(Favorite.builder()
                                .resourceType(ResourceType.PROJECT)
                                .resourceIdentifier(project2.getIdentifier())
                                .build()));
    Set<String> orgIdentifiers = Collections.singleton(orgIdentifier);
    Page<Project> projectPage = projectService.listPermittedProjects(accountIdentifier, unpaged(),
        ProjectFilterDTO.builder().orgIdentifiers(orgIdentifiers).searchTerm(searchTerm).moduleType(CD).build(),
        Boolean.TRUE);
    verify(projectRepository, times(1)).findAll(any(Criteria.class));
    verify(projectRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), any(Pageable.class));

    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(1).containsKey("id");
    Document query = (Document) criteriaObject.get("id");
    assertThat(query).hasSize(1).containsExactly(Map.entry("$in", List.of(project.getId())));
    System.out.println(criteriaObject);

    assertEquals(1, projectPage.getTotalElements());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testProjectsCount() throws NoSuchFieldException, IllegalAccessException {
    String accountIdentifier = randomAlphabetic(10);
    ArgumentCaptor<Aggregation> aggregationArgumentCaptor = ArgumentCaptor.forClass(Aggregation.class);
    List<ProjectsPerOrganizationCount> projectsCount = new ArrayList<>();
    AggregationResults<ProjectsPerOrganizationCount> aggregationResults = mock(AggregationResults.class);
    when(projectRepository.aggregate(any(), eq(ProjectsPerOrganizationCount.class))).thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(projectsCount);
    projectService.getProjectsCountPerOrganization(accountIdentifier, null);

    verify(projectRepository, times(1))
        .aggregate(aggregationArgumentCaptor.capture(), eq(ProjectsPerOrganizationCount.class));
    Aggregation aggregation = aggregationArgumentCaptor.getValue();
    assertNotNull(aggregation);

    List<AggregationOperation> operations = aggregation.getPipeline().getOperations();
    assertEquals(4, operations.size());
    assertEquals(MatchOperation.class, operations.get(0).getClass());
    assertEquals(SortOperation.class, operations.get(1).getClass());
    assertEquals(GroupOperation.class, operations.get(2).getClass());
    assertEquals(ProjectionOperation.class, operations.get(3).getClass());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testListProjects() {
    String user = generateUuid();
    Principal principal = mock(Principal.class);
    when(principal.getType()).thenReturn(PrincipalType.USER);
    when(principal.getName()).thenReturn(user);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    Project proj1 =
        Project.builder().name("P1").accountIdentifier("accId1").orgIdentifier("orgId1").identifier("id1").build();
    Project proj2 =
        Project.builder().name("P2").accountIdentifier("accId1").orgIdentifier("orgId2").identifier("id2").build();
    List<Project> projects = Arrays.asList(proj1, proj2);
    UserMembership userMembership1 =
        UserMembership.builder()
            .userId(user)
            .scope(Scope.builder().accountIdentifier("accId1").orgIdentifier("orgId1").projectIdentifier("id1").build())
            .build();
    UserMembership userMembership2 =
        UserMembership.builder()
            .userId(user)
            .scope(Scope.builder().accountIdentifier("accId1").orgIdentifier("orgId2").projectIdentifier("id2").build())
            .build();
    doReturn(createCloseableIterator(Arrays.asList(userMembership1, userMembership2).iterator()))
        .when(ngUserService)
        .streamUserMemberships(any());
    doReturn(projects).when(projectService).list(any());
    doReturn(new PageImpl<>(projects, Pageable.unpaged(), 100)).when(projectRepository).findAll(any(), any());
    PageResponse<ProjectDTO> projectsResponse =
        projectService.listProjectsForUser(user, "account", PageRequest.builder().pageSize(2).pageIndex(0).build());
    assertNotNull(projectsResponse);
    assertEquals(
        projectsResponse.getContent(), projects.stream().map(ProjectMapper::writeDTO).collect(Collectors.toList()));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testListAllProjectsForUser() {
    String user = generateUuid();
    Principal principal = mock(Principal.class);
    when(principal.getType()).thenReturn(PrincipalType.USER);
    when(principal.getName()).thenReturn(user);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    Project proj1 =
        Project.builder().name("P1").accountIdentifier("accId1").orgIdentifier("orgId1").identifier("id1").build();
    Project proj2 =
        Project.builder().name("P2").accountIdentifier("accId1").orgIdentifier("orgId2").identifier("id2").build();
    List<Project> projects = Arrays.asList(proj1, proj2);
    UserMembership userMembership1 =
        UserMembership.builder()
            .userId(user)
            .scope(Scope.builder().accountIdentifier("accId1").orgIdentifier("orgId1").projectIdentifier("id1").build())
            .build();
    UserMembership userMembership2 =
        UserMembership.builder()
            .userId(user)
            .scope(Scope.builder().accountIdentifier("accId1").orgIdentifier("orgId2").projectIdentifier("id2").build())
            .build();
    doReturn(createCloseableIterator(Arrays.asList(userMembership1, userMembership2).iterator()))
        .when(ngUserService)
        .streamUserMemberships(any());
    doReturn(projects).when(projectService).list(any());
    doReturn(projects).when(projectRepository).findAll((Criteria) any());
    List<ProjectDTO> projectsResponse = projectService.listProjectsForUser(user, "account");
    assertNotNull(projectsResponse);
    assertEquals(projectsResponse, projects.stream().map(ProjectMapper::writeDTO).collect(Collectors.toList()));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testHardDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    Long version = 0L;
    Project project = Project.builder()
                          .name("name")
                          .accountIdentifier(accountIdentifier)
                          .orgIdentifier(orgIdentifier)
                          .identifier(projectIdentifier)
                          .build();
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

    when(yamlGitConfigService.deleteAll(any(), any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(projectRepository.hardDelete(any(), any(), any(), any())).thenReturn(project);

    projectService.delete(accountIdentifier, orgIdentifier, projectIdentifier, version);
    verify(projectRepository, times(1)).hardDelete(any(), any(), argumentCaptor.capture(), any());
    assertEquals(projectIdentifier, argumentCaptor.getValue());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(any());
    verify(favoritesService, times(1))
        .deleteFavorites(accountIdentifier, orgIdentifier, null, ResourceType.PROJECT.toString(), projectIdentifier);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testHardDeleteInvalidIdentifier() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    Long version = 0L;

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(projectRepository.hardDelete(any(), any(), any(), any())).thenReturn(null);

    projectService.delete(accountIdentifier, orgIdentifier, projectIdentifier, version);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldGetProjectIdentifierCaseInsensitive() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(projectRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndIdentifierIgnoreCaseAndDeletedNot(
            accountIdentifier, orgIdentifier, projectIdentifier, true);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldReturnProjectFavoritesFromAllOrgs() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String userid = randomAlphabetic(10);
    when(userHelperService.getUserId()).thenReturn(userid);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    Set<String> permittedOrgs = new HashSet<>();
    permittedOrgs.add(project.getIdentifier());
    when(organizationService.getPermittedOrganizations(accountIdentifier, null))
        .thenReturn(Collections.singleton(orgIdentifier));
    projectService.getProjectFavorites(accountIdentifier, null, userid);
    verify(favoritesService, times(1))
        .getFavorites(accountIdentifier, orgIdentifier, null, userid, ResourceType.PROJECT.toString());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldReturnProjectFavoritesFromAllOrgsInFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String userid = randomAlphabetic(10);
    when(userHelperService.getUserId()).thenReturn(userid);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    project.setOrgIdentifier(orgIdentifier);
    ProjectFilterDTO projectFilterDTO =
        ProjectFilterDTO.builder().orgIdentifiers(Collections.singleton(orgIdentifier)).build();
    projectService.getProjectFavorites(accountIdentifier, projectFilterDTO, userid);
    verify(favoritesService, times(1))
        .getFavorites(accountIdentifier, orgIdentifier, null, userid, ResourceType.PROJECT.toString());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldReturnTrueIfProjectIsFavorite() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String userid = randomAlphabetic(10);
    ProjectDTO projectDTO = createProjectDTO(orgIdentifier, randomAlphabetic(10));
    Project project = toProject(projectDTO);
    project.setAccountIdentifier(accountIdentifier);
    when(favoritesService.isFavorite(accountIdentifier, project.getOrgIdentifier(), null, userid,
             ResourceType.PROJECT.toString(), project.getIdentifier()))
        .thenReturn(true);
    boolean isFavorite = projectService.isFavorite(project, userid);
    assertThat(isFavorite).isTrue();
  }

  private static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}
