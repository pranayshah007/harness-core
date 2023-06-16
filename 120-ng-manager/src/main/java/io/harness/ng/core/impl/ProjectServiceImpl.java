/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_PROJECTS;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ng.accesscontrol.PlatformPermissions.INVITE_PERMISSION_IDENTIFIER;
import static io.harness.ng.core.remote.ProjectMapper.toProject;
import static io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import io.harness.ModuleType;
import io.harness.NgAutoLogContext;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeKeys;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesService;
import io.harness.ff.FeatureFlagService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.DefaultOrganization;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.beans.ProjectsPerOrganizationCount;
import io.harness.ng.core.beans.ProjectsPerOrganizationCount.ProjectsPerOrganizationCountKeys;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.events.ProjectCreateEvent;
import io.harness.ng.core.events.ProjectDeleteEvent;
import io.harness.ng.core.events.ProjectRestoreEvent;
import io.harness.ng.core.events.ProjectUpdateEvent;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.remote.ProjectMapper;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.core.spring.ProjectRepository;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.telemetry.helpers.ProjectInstrumentationHelper;
import io.harness.utils.PageUtils;
import io.harness.utils.ScopeUtils;
import io.harness.utils.UserHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ProjectServiceImpl implements ProjectService {
  private static final String PROJECT_ADMIN_ROLE = "_project_admin";
  private final ProjectRepository projectRepository;
  private final OrganizationService organizationService;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private final NgUserService ngUserService;
  private final AccessControlClient accessControlClient;
  private final ScopeAccessHelper scopeAccessHelper;
  private final ProjectInstrumentationHelper instrumentationHelper;
  private final YamlGitConfigService yamlGitConfigService;
  private final FeatureFlagService featureFlagService;
  private final DefaultUserGroupService defaultUserGroupService;
  private final FavoritesService favoritesService;
  private final UserHelperService userHelperService;

  @Inject
  public ProjectServiceImpl(ProjectRepository projectRepository, OrganizationService organizationService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      NgUserService ngUserService, AccessControlClient accessControlClient, ScopeAccessHelper scopeAccessHelper,
      ProjectInstrumentationHelper instrumentationHelper, YamlGitConfigService yamlGitConfigService,
      FeatureFlagService featureFlagService, DefaultUserGroupService defaultUserGroupService,
      FavoritesService favoritesService, UserHelperService userHelperService) {
    this.projectRepository = projectRepository;
    this.organizationService = organizationService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.ngUserService = ngUserService;
    this.accessControlClient = accessControlClient;
    this.scopeAccessHelper = scopeAccessHelper;
    this.instrumentationHelper = instrumentationHelper;
    this.yamlGitConfigService = yamlGitConfigService;
    this.featureFlagService = featureFlagService;
    this.defaultUserGroupService = defaultUserGroupService;
    this.favoritesService = favoritesService;
    this.userHelperService = userHelperService;
  }

  @Override
  @FeatureRestrictionCheck(MULTIPLE_PROJECTS)
  public Project create(@AccountIdentifier String accountIdentifier, String orgIdentifier, ProjectDTO projectDTO) {
    orgIdentifier = orgIdentifier == null ? DEFAULT_ORG_IDENTIFIER : orgIdentifier;
    validateCreateProjectRequest(accountIdentifier, orgIdentifier, projectDTO);
    Project project = toProject(projectDTO);

    project.setModules(ModuleType.getModules());
    project.setOrgIdentifier(orgIdentifier);
    project.setAccountIdentifier(accountIdentifier);
    try {
      validate(project);
      Project createdProject = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Project savedProject = projectRepository.save(project);
        outboxService.save(new ProjectCreateEvent(project.getAccountIdentifier(), ProjectMapper.writeDTO(project)));
        return savedProject;
      }));
      setupProject(Scope.of(accountIdentifier, orgIdentifier, projectDTO.getIdentifier()));
      log.info(String.format("Project with identifier [%s] and orgIdentifier [%s] was successfully created",
          project.getIdentifier(), projectDTO.getOrgIdentifier()));
      instrumentationHelper.sendProjectCreateEvent(createdProject, accountIdentifier);
      return createdProject;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("A project with identifier [%s] and orgIdentifier [%s] is already present",
              project.getIdentifier(), orgIdentifier),
          USER_SRE, ex);
    }
  }

  private void setupProject(Scope scope) {
    try {
      defaultUserGroupService.create(scope, emptyList());
    } catch (Exception ex) {
      log.error("Default User Group Creation failed for Project: " + scope.toString(), ex);
    }
    if (featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)) {
      if (DEFAULT_PROJECT_IDENTIFIER.equals(scope.getProjectIdentifier())) {
        // Default project is a special case. That is handled by ng account setup service
        return;
      }
    }
    String principalId = null;
    PrincipalType principalType = PrincipalType.USER;
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && (SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER
            || SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.SERVICE_ACCOUNT)) {
      principalId = SourcePrincipalContextBuilder.getSourcePrincipal().getName();
      principalType = SourcePrincipalContextBuilder.getSourcePrincipal().getType();
    }
    if (isEmpty(principalId)) {
      throw new InvalidRequestException("User not found in security context");
    }
    try {
      assignProjectAdmin(scope, principalId, principalType);
      busyPollUntilProjectSetupCompletes(scope, principalId);
    } catch (Exception e) {
      log.error("Failed to complete post project creation steps for [{}]", ScopeUtils.toString(scope));
    }
  }

  private void busyPollUntilProjectSetupCompletes(Scope scope, String userId) {
    RetryConfig config = RetryConfig.custom()
                             .maxAttempts(50)
                             .waitDuration(Duration.ofMillis(200))
                             .retryOnResult(FALSE::equals)
                             .retryExceptions(Exception.class)
                             .ignoreExceptions(IOException.class)
                             .build();
    Retry retry = Retry.of("check user permissions", config);
    Retry.EventPublisher publisher = retry.getEventPublisher();
    publisher.onRetry(event -> log.info("Retrying for project {} {}", scope.getProjectIdentifier(), event.toString()));
    publisher.onSuccess(
        event -> log.info("Retrying for project {} {}", scope.getProjectIdentifier(), event.toString()));
    Supplier<Boolean> hasAccess = Retry.decorateSupplier(retry,
        ()
            -> accessControlClient.hasAccess(
                ResourceScope.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()),
                Resource.of("USER", userId), INVITE_PERMISSION_IDENTIFIER));
    if (FALSE.equals(hasAccess.get())) {
      log.error("Finishing project setup without confirm role assignment creation [{}]", ScopeUtils.toString(scope));
    }
  }

  private void assignProjectAdmin(Scope scope, String principalId, PrincipalType principalType) {
    switch (principalType) {
      case USER:
        ngUserService.addUserToScope(principalId,
            Scope.builder()
                .accountIdentifier(scope.getAccountIdentifier())
                .orgIdentifier(scope.getOrgIdentifier())
                .projectIdentifier(scope.getProjectIdentifier())
                .build(),
            singletonList(RoleBinding.builder()
                              .roleIdentifier(PROJECT_ADMIN_ROLE)
                              .resourceGroupIdentifier(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                              .build()),
            emptyList(), SYSTEM);
        break;
      case SERVICE_ACCOUNT:
        ngUserService.addServiceAccountToScope(principalId,
            Scope.builder()
                .accountIdentifier(scope.getAccountIdentifier())
                .orgIdentifier(scope.getOrgIdentifier())
                .projectIdentifier(scope.getProjectIdentifier())
                .build(),
            RoleBinding.builder()
                .roleIdentifier(PROJECT_ADMIN_ROLE)
                .resourceGroupIdentifier(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                .build(),
            SYSTEM);
        break;
      case API_KEY:
      case SERVICE: {
        throw new InvalidRequestException(
            "Cannot assign principal" + principalId + "with type" + principalType + "to project");
      }
      default: {
        throw new InvalidRequestException("Invalid  principal type" + principalType);
      }
    }
  }

  @Override
  @DefaultOrganization
  public Optional<Project> get(
      String accountIdentifier, @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    return projectRepository.findByAccountIdentifierAndOrgIdentifierAndIdentifierIgnoreCaseAndDeletedNot(
        accountIdentifier, orgIdentifier, projectIdentifier, true);
  }

  @Override
  @DefaultOrganization
  public Optional<Project> getConsideringCase(
      String accountIdentifier, @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    return projectRepository.findByAccountIdentifierAndOrgIdentifierAndIdentifierAndDeletedNot(
        accountIdentifier, orgIdentifier, projectIdentifier, true);
  }

  @Override
  public PageResponse<ProjectDTO> listProjectsForUser(String userId, String accountId, PageRequest pageRequest) {
    Criteria criteria = Criteria.where(UserMembershipKeys.userId)
                            .is(userId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(accountId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                            .exists(true)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                            .exists(true);

    Criteria projectCriteria = Criteria.where(ProjectKeys.accountIdentifier).is(accountId);
    List<Criteria> criteriaList = new ArrayList<>();
    try (CloseableIterator<UserMembership> iterator = ngUserService.streamUserMemberships(criteria)) {
      while (iterator.hasNext()) {
        UserMembership userMembership = iterator.next();
        Scope scope = userMembership.getScope();
        criteriaList.add(Criteria.where(ProjectKeys.orgIdentifier)
                             .is(scope.getOrgIdentifier())
                             .and(ProjectKeys.identifier)
                             .is(scope.getProjectIdentifier())
                             .and(ProjectKeys.deleted)
                             .is(false));
      }
    }

    if (criteriaList.isEmpty()) {
      return getNGPageResponse(Page.empty(), emptyList());
    }
    projectCriteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    Page<Project> projectsPage = projectRepository.findAll(projectCriteria, PageUtils.getPageRequest(pageRequest));
    return getNGPageResponse(projectsPage.map(ProjectMapper::writeDTO));
  }

  @Override
  public List<ProjectDTO> listProjectsForUser(String userId, String accountId) {
    Criteria criteria = Criteria.where(UserMembershipKeys.userId)
                            .is(userId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(accountId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                            .exists(true)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                            .exists(true);

    Criteria projectCriteria = Criteria.where(ProjectKeys.accountIdentifier).is(accountId);
    List<Criteria> criteriaList = new ArrayList<>();
    try (CloseableIterator<UserMembership> iterator = ngUserService.streamUserMemberships(criteria)) {
      while (iterator.hasNext()) {
        UserMembership userMembership = iterator.next();
        Scope scope = userMembership.getScope();
        criteriaList.add(Criteria.where(ProjectKeys.orgIdentifier)
                             .is(scope.getOrgIdentifier())
                             .and(ProjectKeys.identifier)
                             .is(scope.getProjectIdentifier())
                             .and(ProjectKeys.deleted)
                             .is(false));
      }
    }
    if (criteriaList.isEmpty()) {
      return Collections.emptyList();
    }
    projectCriteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    List<Project> projectsList = projectRepository.findAll(projectCriteria);
    return projectsList.stream().map(ProjectMapper::writeDTO).collect(Collectors.toList());
  }

  @Override
  public ActiveProjectsCountDTO accessibleProjectsCount(
      String userId, String accountId, long startInterval, long endInterval) {
    Criteria criteria = Criteria.where(UserMembershipKeys.userId)
                            .is(userId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(accountId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                            .exists(true)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                            .exists(true);

    Criteria projectCriteria = Criteria.where(ProjectKeys.accountIdentifier).is(accountId);
    List<Criteria> criteriaList = new ArrayList<>();
    try (CloseableIterator<UserMembership> iterator = ngUserService.streamUserMemberships(criteria)) {
      while (iterator.hasNext()) {
        UserMembership userMembership = iterator.next();
        Scope scope = userMembership.getScope();
        criteriaList.add(Criteria.where(ProjectKeys.orgIdentifier)
                             .is(scope.getOrgIdentifier())
                             .and(ProjectKeys.identifier)
                             .is(scope.getProjectIdentifier()));
      }
    }
    if (isEmpty(criteriaList)) {
      return ActiveProjectsCountDTO.builder().count(0).build();
    }
    Criteria accessibleProjectCriteria =
        projectCriteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    Criteria deletedFalseCriteria = Criteria.where(ProjectKeys.createdAt)
                                        .gt(startInterval)
                                        .lt(endInterval)
                                        .andOperator(Criteria.where(ProjectKeys.deleted).is(false));
    Criteria deletedTrueCriteria =
        Criteria.where(ProjectKeys.createdAt)
            .lt(startInterval)
            .andOperator(new Criteria().andOperator(Criteria.where(ProjectKeys.deleted).is(true)),
                Criteria.where(ProjectKeys.lastModifiedAt).gt(startInterval).lt(endInterval));
    return ActiveProjectsCountDTO.builder()
        .count(projectRepository.findAll(new Criteria().andOperator(accessibleProjectCriteria, deletedFalseCriteria))
                   .size()
            - projectRepository.findAll(accessibleProjectCriteria.andOperator(deletedTrueCriteria)).size())
        .build();
  }

  @Override
  @DefaultOrganization
  public Project update(String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String identifier, ProjectDTO projectDTO) {
    validateUpdateProjectRequest(accountIdentifier, orgIdentifier, identifier, projectDTO);
    Optional<Project> optionalProject = get(accountIdentifier, orgIdentifier, identifier);

    if (optionalProject.isPresent()) {
      Project existingProject = optionalProject.get();
      Project project = toProject(projectDTO);
      project.setAccountIdentifier(accountIdentifier);
      project.setOrgIdentifier(orgIdentifier);
      project.setId(existingProject.getId());
      project.setCreatedAt(existingProject.getCreatedAt() == null ? existingProject.getLastModifiedAt()
                                                                  : existingProject.getCreatedAt());
      if (project.getVersion() == null) {
        project.setVersion(existingProject.getVersion());
      }

      List<ModuleType> moduleTypeList = verifyModulesNotRemoved(existingProject.getModules(), project.getModules());
      project.setModules(moduleTypeList);
      validate(project);
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Project updatedProject = projectRepository.save(project);
        log.info(String.format(
            "Project with identifier [%s] and orgIdentifier [%s] was successfully updated", identifier, orgIdentifier));
        outboxService.save(new ProjectUpdateEvent(project.getAccountIdentifier(),
            ProjectMapper.writeDTO(updatedProject), ProjectMapper.writeDTO(existingProject)));
        return updatedProject;
      }));
    }
    throw new InvalidRequestException(
        String.format("Project with identifier [%s] and orgIdentifier [%s] not found", identifier, orgIdentifier),
        USER);
  }

  private List<ModuleType> verifyModulesNotRemoved(List<ModuleType> oldList, List<ModuleType> newList) {
    Set<ModuleType> oldSet = new HashSet<>(oldList);
    Set<ModuleType> newSet = new HashSet<>(newList);

    if (newSet.containsAll(oldSet)) {
      return new ArrayList<>(newSet);
    }
    throw new InvalidRequestException("Modules cannot be removed from a project");
  }

  @Override
  public Page<Project> listPermittedProjects(
      String accountIdentifier, Pageable pageable, ProjectFilterDTO projectFilterDTO, Boolean onlyFavorites) {
    if (BooleanUtils.isTrue(onlyFavorites)) {
      updateFilterPropertiesFromFavorites(accountIdentifier, projectFilterDTO, userHelperService.getUserId());
    }
    Criteria criteria = getCriteriaForPermittedProjects(accountIdentifier, projectFilterDTO);
    if (criteria == null) {
      return Page.empty();
    }
    return projectRepository.findAll(criteria, pageable);
  }

  private void updateFilterPropertiesFromFavorites(
      String accountIdentifier, ProjectFilterDTO projectFilterDTO, String userId) {
    List<String> favoriteIds =
        favoritesService.getFavorites(accountIdentifier, null, null, userId, ResourceType.PROJECT.toString())
            .stream()
            .map(Favorite::getResourceIdentifier)
            .collect(Collectors.toList());
    if (favoriteIds.isEmpty()) {
      favoriteIds.add("NO_MATCH");
    }
    List<String> filterProjectIdentifiers =
        projectFilterDTO.getIdentifiers() != null ? projectFilterDTO.getIdentifiers() : new ArrayList<>();
    filterProjectIdentifiers.addAll(favoriteIds);
    projectFilterDTO.setIdentifiers(filterProjectIdentifiers);
  }

  @Override
  public List<ProjectDTO> listPermittedProjects(String accountIdentifier, ProjectFilterDTO projectFilterDTO) {
    Criteria criteria = getCriteriaForPermittedProjects(accountIdentifier, projectFilterDTO);
    if (criteria == null) {
      return Collections.emptyList();
    }
    List<Project> projectsList = projectRepository.findAll(criteria);
    return projectsList.stream().map(ProjectMapper::writeDTO).collect(Collectors.toList());
  }

  private Criteria getCriteriaForPermittedProjects(String accountIdentifier, ProjectFilterDTO projectFilterDTO) {
    Criteria criteria = createProjectFilterCriteria(
        Criteria.where(ProjectKeys.accountIdentifier).is(accountIdentifier).and(ProjectKeys.deleted).is(FALSE),
        projectFilterDTO);
    List<Project> projects = projectRepository.findAll(criteria);

    Map<Scope, String> scopeToProjectIdMap = projects.stream().collect(Collectors.toMap(project
        -> Scope.of(project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier()),
        Project::getId));
    List<Scope> permittedProjects =
        scopeAccessHelper.getPermittedScopes(Lists.newArrayList(scopeToProjectIdMap.keySet()));
    List<String> permittedIds = permittedProjects.stream().map(scopeToProjectIdMap::get).collect(Collectors.toList());

    if (permittedProjects.isEmpty()) {
      return null;
    }
    criteria = Criteria.where(ProjectKeys.id).in(permittedIds);
    return criteria;
  }

  public ActiveProjectsCountDTO permittedProjectsCount(
      String accountIdentifier, ProjectFilterDTO projectFilterDTO, long startInterval, long endInterval) {
    Criteria criteria = createProjectFilterCriteria(
        Criteria.where(ProjectKeys.accountIdentifier).is(accountIdentifier).and(ProjectKeys.deleted).is(FALSE),
        projectFilterDTO);
    List<Scope> projects = projectRepository.findAllProjects(criteria);
    List<Scope> permittedProjects = scopeAccessHelper.getPermittedScopes(projects);

    if (permittedProjects.isEmpty()) {
      return ActiveProjectsCountDTO.builder().count(0).build();
    }

    criteria = Criteria.where(ProjectKeys.accountIdentifier).is(accountIdentifier);
    Criteria[] subCriteria = permittedProjects.stream()
                                 .map(project
                                     -> Criteria.where(ProjectKeys.orgIdentifier)
                                            .is(project.getOrgIdentifier())
                                            .and(ProjectKeys.identifier)
                                            .is(project.getProjectIdentifier()))
                                 .toArray(Criteria[] ::new);
    Criteria accessibleProjectCriteria = criteria.orOperator(subCriteria);
    Criteria deletedFalseCriteria = Criteria.where(ProjectKeys.createdAt)
                                        .gt(startInterval)
                                        .lt(endInterval)
                                        .andOperator(Criteria.where(ProjectKeys.deleted).is(false));
    Criteria deletedTrueCriteria =
        Criteria.where(ProjectKeys.createdAt)
            .lt(startInterval)
            .andOperator(new Criteria().andOperator(Criteria.where(ProjectKeys.deleted).is(true)),
                Criteria.where(ProjectKeys.lastModifiedAt).gt(startInterval).lt(endInterval));
    return ActiveProjectsCountDTO.builder()
        .count(projectRepository.count(new Criteria().andOperator(accessibleProjectCriteria, deletedFalseCriteria))
            - projectRepository.count(new Criteria().andOperator(accessibleProjectCriteria, deletedTrueCriteria)))
        .build();
  }

  @Override
  public Page<Project> list(Criteria criteria, Pageable pageable) {
    return projectRepository.findAll(criteria, pageable);
  }

  @Override
  public List<Project> list(Criteria criteria) {
    return projectRepository.findAll(criteria);
  }

  private Criteria createProjectFilterCriteria(Criteria criteria, ProjectFilterDTO projectFilterDTO) {
    if (projectFilterDTO == null) {
      return criteria;
    }

    if (projectFilterDTO.getOrgIdentifiers() != null && isNotEmpty(projectFilterDTO.getOrgIdentifiers())) {
      criteria.and(ProjectKeys.orgIdentifier).in(projectFilterDTO.getOrgIdentifiers());
    }

    if (projectFilterDTO.getModuleType() != null) {
      if (Boolean.TRUE.equals(projectFilterDTO.getHasModule())) {
        criteria.and(ProjectKeys.modules).in(projectFilterDTO.getModuleType());
      } else {
        criteria.and(ProjectKeys.modules).nin(projectFilterDTO.getModuleType());
      }
    }
    if (isNotBlank(projectFilterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(ProjectKeys.name).regex(projectFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ProjectKeys.identifier).regex(projectFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ProjectKeys.tags + "." + NGTagKeys.key).regex(projectFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ProjectKeys.tags + "." + NGTagKeys.value).regex(projectFilterDTO.getSearchTerm(), "i"));
    }
    if (isNotEmpty(projectFilterDTO.getIdentifiers())) {
      criteria.and(ProjectKeys.identifier).in(projectFilterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  @DefaultOrganization
  public boolean delete(String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, Long version) {
    try (AutoLogContext ignore1 =
             new NgAutoLogContext(projectIdentifier, orgIdentifier, accountIdentifier, OVERRIDE_ERROR)) {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Project deletedProject =
            projectRepository.hardDelete(accountIdentifier, orgIdentifier, projectIdentifier, version);

        if (isNull(deletedProject)) {
          log.error(String.format("Project with identifier [%s] could not be deleted as it does not exist",
              projectIdentifier, orgIdentifier));
          throw new EntityNotFoundException(
              String.format("Project with identifier [%s] does not exist in the specified scope", projectIdentifier));
        }

        log.info(String.format("Project with identifier [%s] and orgIdentifier [%s] was successfully deleted",
            projectIdentifier, orgIdentifier));
        yamlGitConfigService.deleteAll(accountIdentifier, orgIdentifier, projectIdentifier);
        outboxService.save(
            new ProjectDeleteEvent(deletedProject.getAccountIdentifier(), ProjectMapper.writeDTO(deletedProject)));
        instrumentationHelper.sendProjectDeleteEvent(deletedProject, accountIdentifier);

        return true;
      }));
    }
  }

  @Override
  public boolean restore(String accountIdentifier, String orgIdentifier, String identifier) {
    validateParentOrgExists(accountIdentifier, orgIdentifier);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Project restoredProject = projectRepository.restore(accountIdentifier, orgIdentifier, identifier);
      boolean success = restoredProject != null;
      if (success) {
        outboxService.save(
            new ProjectRestoreEvent(restoredProject.getAccountIdentifier(), ProjectMapper.writeDTO(restoredProject)));
      }
      return success;
    }));
  }

  @Override
  public Map<String, Integer> getProjectsCountPerOrganization(String accountIdentifier, List<String> orgIdentifiers) {
    Criteria criteria =
        Criteria.where(ProjectKeys.accountIdentifier).is(accountIdentifier).and(ProjectKeys.deleted).ne(Boolean.TRUE);
    if (isNotEmpty(orgIdentifiers)) {
      criteria.and(ProjectKeys.orgIdentifier).in(orgIdentifiers);
    }
    MatchOperation matchStage = Aggregation.match(criteria);
    SortOperation sortStage = sort(Sort.by(ProjectKeys.orgIdentifier));
    GroupOperation groupByOrganizationStage =
        group(ProjectKeys.orgIdentifier).count().as(ProjectsPerOrganizationCountKeys.count);
    ProjectionOperation projectionStage =
        project().and(MONGODB_ID).as(ProjectKeys.orgIdentifier).andInclude(ProjectsPerOrganizationCountKeys.count);
    Map<String, Integer> result = new HashMap<>();
    projectRepository
        .aggregate(newAggregation(matchStage, sortStage, groupByOrganizationStage, projectionStage),
            ProjectsPerOrganizationCount.class)
        .getMappedResults()
        .forEach(projectsPerOrganizationCount
            -> result.put(projectsPerOrganizationCount.getOrgIdentifier(), projectsPerOrganizationCount.getCount()));
    return result;
  }

  @Override
  public Long countProjects(String accountIdentifier) {
    return projectRepository.countByAccountIdentifierAndDeletedIsFalse(accountIdentifier);
  }

  @Override
  public boolean isFavorite(Project project, String userId) {
    return favoritesService.isFavorite(
        project.getAccountIdentifier(), null, null, userId, ResourceType.PROJECT.toString(), project.getIdentifier());
  }

  private void validateCreateProjectRequest(String accountIdentifier, String orgIdentifier, ProjectDTO project) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(orgIdentifier, project.getOrgIdentifier())), true);
    validateParentOrgExists(accountIdentifier, orgIdentifier);
  }

  private void validateParentOrgExists(String accountIdentifier, String orgIdentifier) {
    if (!organizationService.get(accountIdentifier, orgIdentifier).isPresent()) {
      throw new InvalidArgumentsException(
          String.format("Organization [%s] in Account [%s] does not exist", orgIdentifier, accountIdentifier),
          USER_SRE);
    }
  }

  private void validateUpdateProjectRequest(
      String accountIdentifier, String orgIdentifier, String identifier, ProjectDTO project) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(orgIdentifier, project.getOrgIdentifier())), true);
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(identifier, project.getIdentifier())), false);
    validateParentOrgExists(accountIdentifier, orgIdentifier);
  }
}
