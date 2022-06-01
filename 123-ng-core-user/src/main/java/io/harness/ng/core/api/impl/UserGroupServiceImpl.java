/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.GROUP;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM;
import static io.harness.ng.core.usergroups.filter.UserGroupFilterType.INCLUDE_INHERITED_GROUPS;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;
import static io.harness.ng.core.utils.UserGroupMapper.toEntity;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.RestClientUtils.getResponse;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.accesscontrol.scopes.ScopeNameMapper;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.ng.core.events.UserGroupCreateEvent;
import io.harness.ng.core.events.UserGroupDeleteEvent;
import io.harness.ng.core.events.UserGroupUpdateEvent;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserGroup.UserGroupKeys;
import io.harness.ng.core.user.remote.dto.LastAdminCheckFilter;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.LastAdminCheckService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.notification.NotificationChannelType;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ng.core.spring.UserGroupRepository;
import io.harness.user.remote.UserClient;
import io.harness.utils.ScopeUtils;

import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.security.authentication.SSOConfig;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Singleton
@Slf4j
public class UserGroupServiceImpl implements UserGroupService {
  private final UserGroupRepository userGroupRepository;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private final NgUserService ngUserService;
  private final AuthSettingsManagerClient managerClient;
  private final LastAdminCheckService lastAdminCheckService;
  private final AccessControlAdminClient accessControlAdminClient;
  private final AccessControlClient accessControlClient;
  private final ScopeNameMapper scopeNameMapper;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_TRANSACTION_RETRY_POLICY;

  @Inject
  public UserGroupServiceImpl(UserGroupRepository userGroupRepository, UserClient userClient,
      OutboxService outboxService, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      NgUserService ngUserService, AuthSettingsManagerClient managerClient, LastAdminCheckService lastAdminCheckService,
      AccessControlAdminClient accessControlAdminClient, AccessControlClient accessControlClient,
      ScopeNameMapper scopeNameMapper) {
    this.userGroupRepository = userGroupRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.ngUserService = ngUserService;
    this.managerClient = managerClient;
    this.lastAdminCheckService = lastAdminCheckService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.accessControlClient = accessControlClient;
    this.scopeNameMapper = scopeNameMapper;
  }

  @Override
  public UserGroup create(UserGroupDTO userGroupDTO) {
    try {
      UserGroup userGroup = toEntity(userGroupDTO);
      validate(userGroup);
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        UserGroup savedUserGroup = userGroupRepository.save(userGroup);
        outboxService.save(new UserGroupCreateEvent(userGroupDTO.getAccountIdentifier(), userGroupDTO));
        return savedUserGroup;
      }));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("Try using different user group identifier, [%s] cannot be used", userGroupDTO.getIdentifier()),
          USER_SRE, ex);
    }
  }

  private void addUsersOfGroupToScope(UserGroupDTO userGroupDTO, ScopeDTO scope) {
    if (isNotEmpty(userGroupDTO.getUsers())) {
      for (String userId : userGroupDTO.getUsers()) {
        ngUserService.addUserToScope(userId,
            Scope.builder()
                .accountIdentifier(scope.getAccountIdentifier())
                .orgIdentifier(scope.getOrgIdentifier())
                .projectIdentifier(scope.getProjectIdentifier())
                .build(),
            singletonList(RoleBinding.builder().build()), emptyList(), SYSTEM);
      }
    }
  }

  @Override
  public boolean copy(String accountIdentifier, String userGroupIdentifier, List<ScopeDTO> scopePairs) {
    Optional<UserGroup> userGroupOptional = get(accountIdentifier, null, null, userGroupIdentifier);
    if (!userGroupOptional.isPresent()) {
      throw new InvalidRequestException("The user group doesnt exist at account level for copying");
    }

    UserGroupDTO userGroupDTO = toDTO(userGroupOptional.get());
    for (ScopeDTO scope : scopePairs) {
      if (StringUtils.isEmpty(scope.getAccountIdentifier()) || StringUtils.isEmpty(scope.getOrgIdentifier())) {
        throw new InvalidRequestException("Invalid scope provided for copying user group " + userGroupIdentifier);
      }
      addUsersOfGroupToScope(userGroupDTO, scope);

      log.info("Copying usergroup {} at scope {}", userGroupIdentifier, scope);
      userGroupDTO.setOrgIdentifier(scope.getOrgIdentifier());
      userGroupDTO.setProjectIdentifier(scope.getProjectIdentifier());
      create(userGroupDTO);
      log.info("Successfully copied usergroup {} at scope {}", userGroupIdentifier, scope);
    }
    return true;
  }

  @Override
  public boolean isExternallyManaged(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userGroupIdentifier) {
    Optional<UserGroup> userGroupOptional =
        get(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    if (!userGroupOptional.isPresent()) {
      throw new InvalidRequestException(String.format("Usergroup with Identifier: %s does not exist at Scope: %s/%s/%s",
                                            userGroupIdentifier, accountIdentifier, orgIdentifier, projectIdentifier),
          ErrorCode.USER_GROUP_ERROR, GROUP);
    }
    return userGroupOptional.get().isExternallyManaged();
  }

  @Override
  public Optional<UserGroup> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = createUserGroupFetchCriteria(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return userGroupRepository.find(criteria);
  }

  @Override
  public UserGroup update(UserGroupDTO userGroupDTO) {
    UserGroup savedUserGroup = getOrThrow(userGroupDTO.getAccountIdentifier(), userGroupDTO.getOrgIdentifier(),
        userGroupDTO.getProjectIdentifier(), userGroupDTO.getIdentifier());
    UserGroup userGroup = toEntity(userGroupDTO);
    userGroup.setId(savedUserGroup.getId());
    userGroup.setVersion(savedUserGroup.getVersion());
    return updateInternal(userGroup, toDTO(savedUserGroup));
  }

  @Override
  public Page<UserGroup> list(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, UserGroupFilterType filterType, Pageable pageable) {
    return userGroupRepository.findAll(
        createUserGroupFilterCriteria(accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, filterType),
        pageable);
  }

  @Override
  public List<ScopeNameDTO> getInheritingChildScopeList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userGroupIdentifier) {
    Optional<UserGroup> userGroupOpt = get(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    if (!userGroupOpt.isPresent()) {
      throw new InvalidRequestException(String.format("User Group is not available %s:%s:%s:%s", accountIdentifier,
          orgIdentifier, projectIdentifier, userGroupIdentifier));
    }
    PrincipalDTO principalDTO =
        PrincipalDTO.builder()
            .identifier(userGroupIdentifier)
            .type(USER_GROUP)
            .scopeLevel(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase())
            .build();
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build();
    List<RoleAssignmentResponseDTO> roleAssignmentsResponse =
        NGRestUtils.getResponse(accessControlAdminClient.getFilteredRoleAssignmentsIncludingChildScopes(
            accountIdentifier, orgIdentifier, projectIdentifier, roleAssignmentFilterDTO));
    return roleAssignmentsResponse.stream()
        .map(RoleAssignmentResponseDTO::getScope)
        .distinct()
        .filter(scopeDTO
            -> !scopeDTO.equals(ScopeDTO.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build()))
        .map(scopeNameMapper::toScopeNameDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserGroup> list(Criteria criteria) {
    return userGroupRepository.findAll(criteria);
  }

  @Override
  public List<UserGroup> list(UserGroupFilterDTO userGroupFilterDTO) {
    validateFilter(userGroupFilterDTO);
    Criteria criteria = createUserGroupFilterCriteria(userGroupFilterDTO.getAccountIdentifier(),
        userGroupFilterDTO.getOrgIdentifier(), userGroupFilterDTO.getProjectIdentifier(),
        userGroupFilterDTO.getSearchTerm(), userGroupFilterDTO.getFilterType());
    // consider inherited user groups
    if (isNotEmpty(userGroupFilterDTO.getDatabaseIdFilter())) {
      criteria.and(UserGroupKeys.id).in(userGroupFilterDTO.getDatabaseIdFilter());
    } else if (isNotEmpty(userGroupFilterDTO.getIdentifierFilter())) {
      criteria.and(UserGroupKeys.identifier).in(userGroupFilterDTO.getIdentifierFilter());
    }
    if (isNotEmpty(userGroupFilterDTO.getUserIdentifierFilter())) {
      criteria.and(UserGroupKeys.users).in(userGroupFilterDTO.getUserIdentifierFilter());
    }
    return userGroupRepository.findAll(criteria, Pageable.unpaged()).getContent();
  }

  public PageResponse<UserMetadataDTO> listUsersInUserGroup(
      Scope scope, String userGroupIdentifier, UserFilter userFilter, PageRequest pageRequest) {
    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    if (!userGroupOptional.isPresent()) {
      return PageResponse.getEmptyPageResponse(pageRequest);
    }
    Set<String> userGroupMemberIds = new HashSet<>(userGroupOptional.get().getUsers());
    if (isEmpty(userFilter.getIdentifiers())) {
      userFilter.setIdentifiers(userGroupMemberIds);
    } else {
      userFilter.setIdentifiers(Sets.intersection(userFilter.getIdentifiers(), userGroupMemberIds));
    }
    return ngUserService.listUsers(scope, pageRequest, userFilter);
  }

  @Override
  public List<UserMetadataDTO> getUsersInUserGroup(Scope scope, String userGroupIdentifier) {
    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    if (!userGroupOptional.isPresent()) {
      return new ArrayList<>();
    }
    Set<String> userGroupMemberIds = new HashSet<>(userGroupOptional.get().getUsers());

    return ngUserService.getUserMetadata(new ArrayList<>(userGroupMemberIds));
  }

  @Override
  public UserGroup delete(Scope scope, String identifier) {
    validateAtleastOneAdminExistAfterRemoval(scope, identifier, null);

    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), identifier);
    if (userGroupOptional.isPresent() && userGroupOptional.get().isHarnessManaged()) {
      throw new InvalidRequestException("Cannot deleted a managed user group");
    }
    Criteria criteria = createUserGroupFetchCriteria(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), identifier);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      UserGroup userGroup = userGroupRepository.delete(criteria);
      outboxService.save(new UserGroupDeleteEvent(userGroup.getAccountIdentifier(), toDTO(userGroup)));
      return userGroup;
    }));
  }

  @Override
  public boolean deleteByScope(Scope scope) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      Criteria criteria =
          createScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
      List<UserGroup> deleteUserGroups = userGroupRepository.deleteAll(criteria);
      if (isNotEmpty(deleteUserGroups)) {
        deleteUserGroups.forEach(userGroup
            -> outboxService.save(new UserGroupDeleteEvent(userGroup.getAccountIdentifier(), toDTO(userGroup))));
      }
      return true;
    }));
  }

  @Override
  public boolean checkMember(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userGroupIdentifier, String userIdentifier) {
    UserGroup existingUserGroup = getOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    return existingUserGroup.getUsers().contains(userIdentifier);
  }

  @Override
  public UserGroup addMember(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userGroupIdentifier, String userIdentifier) {
    UserGroup existingUserGroup = getOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    UserGroupDTO oldUserGroup = (UserGroupDTO) HObjectMapper.clone(toDTO(existingUserGroup));

    if (existingUserGroup.getUsers().stream().noneMatch(userIdentifier::equals)) {
      log.info("[NGSamlUserGroupSync] Adding member {} to Existing Usergroup: {}", userIdentifier, existingUserGroup);
      existingUserGroup.getUsers().add(userIdentifier);
    } else {
      throw new InvalidRequestException(
          String.format("User %s is already part of User Group %s", userIdentifier, userGroupIdentifier));
    }
    return updateInternal(existingUserGroup, oldUserGroup);
  }

  @Override
  public void addUserToUserGroups(String accountIdentifier, String userId, List<UserGroup> userGroups) {
    if (isEmpty(userGroups)) {
      return;
    }

    for (UserGroup userGroup : userGroups) {
      if (!checkMember(accountIdentifier, userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier(),
              userGroup.getIdentifier(), userId)) {
        log.info("[NGSamlUserGroupSync] Trying to add user {} to UserGroup:{}", userId, userGroup);
        addMember(accountIdentifier, userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier(),
            userGroup.getIdentifier(), userId);
      } else {
        log.info("[NGSamlUserGroupSync] Not adding user {} to UserGroup:{} CheckMember failed ", userId, userGroup);
      }
    }
  }

  @Override
  public void addUserToUserGroups(Scope scope, String userId, List<String> userGroups) {
    if (isEmpty(userGroups)) {
      return;
    }
    userGroups.forEach(userGroup -> {
      if (!checkMember(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroup,
              userId)) {
        addMember(
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroup, userId);
      }
    });
  }

  @Override
  public UserGroup removeMember(Scope scope, String userGroupIdentifier, String userIdentifier) {
    UserGroup existingUserGroup = getOrThrow(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    UserGroupDTO oldUserGroup = (UserGroupDTO) HObjectMapper.clone(toDTO(existingUserGroup));
    validateAtleastOneAdminExistAfterRemoval(scope, userGroupIdentifier, userIdentifier);
    existingUserGroup.getUsers().remove(userIdentifier);
    return updateInternal(existingUserGroup, oldUserGroup);
  }

  private void validateAtleastOneAdminExistAfterRemoval(
      Scope scope, String userGroupIdentifier, String userIdentifier) {
    if (!ScopeUtils.isAccountScope(scope)) {
      return;
    }
    if (!lastAdminCheckService.doesAdminExistAfterRemoval(
            scope.getAccountIdentifier(), new LastAdminCheckFilter(userIdentifier, userGroupIdentifier))) {
      throw new InvalidRequestException(String.format("%s is the last account admin for %s. Can't remove it",
          userIdentifier == null ? userGroupIdentifier : userIdentifier, scope.getAccountIdentifier()));
    }
  }

  @Override
  public void removeMemberAll(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String userIdentifier) {
    Criteria criteria =
        createCriteriaByScopeAndUsersIn(accountIdentifier, orgIdentifier, projectIdentifier, userIdentifier);
    List<UserGroup> userGroups = userGroupRepository.findAll(criteria);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    List<String> userGroupIdentifiers = userGroups.stream().map(UserGroup::getIdentifier).collect(Collectors.toList());
    userGroupIdentifiers.forEach(userGroupIdentifier -> removeMember(scope, userGroupIdentifier, userIdentifier));
  }

  private Criteria createCriteriaByScopeAndUsersIn(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String userIdentifier) {
    Criteria criteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    criteria.and(UserGroupKeys.users).in(userIdentifier);
    return criteria;
  }

  private UserGroup getOrThrow(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Optional<UserGroup> userGroupOptional = get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (!userGroupOptional.isPresent()) {
      throw new InvalidArgumentsException("User Group in the given scope does not exist:" + identifier);
    }
    return userGroupOptional.get();
  }

  private UserGroup updateInternal(UserGroup newUserGroup, UserGroupDTO oldUserGroup) {
    log.info("[NGSamlUserGroupSync] Old User Group {}", oldUserGroup);
    validate(newUserGroup);
    try {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        log.info("[NGSamlUserGroupSync] Saving new User group {}", newUserGroup);
        UserGroup updatedUserGroup = userGroupRepository.save(newUserGroup);
        log.info("[NGSamlUserGroupSync] Saved New User Group Successfully");
        outboxService.save(
            new UserGroupUpdateEvent(updatedUserGroup.getAccountIdentifier(), toDTO(updatedUserGroup), oldUserGroup));
        return updatedUserGroup;
      }));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("Try using different user group identifier, [%s] cannot be used", newUserGroup.getIdentifier()),
          USER_SRE, ex);
    }
  }

  private void validate(UserGroup userGroup) {
    if (userGroup.getNotificationConfigs() != null) {
      validateNotificationSettings(userGroup.getNotificationConfigs());
    }
    if (userGroup.getUsers() != null) {
      validateUsers(userGroup.getUsers());
      validateScopeMembership(userGroup);
    }
  }

  private void validateUsers(List<String> usersIds) {
    Set<String> duplicates = getDuplicates(usersIds);
    if (isNotEmpty(duplicates)) {
      throw new InvalidArgumentsException(
          String.format("Duplicate users %s provided in the user group", duplicates.toString()));
    }
  }

  private static <T> Set<T> getDuplicates(Iterable<T> elements) {
    Set<T> set = new HashSet<>();
    Set<T> duplicates = new HashSet<>();
    for (T element : elements) {
      if (!set.add(element)) {
        duplicates.add(element);
      }
    }
    return duplicates;
  }

  private void validateScopeMembership(UserGroup userGroup) {
    Scope scope = Scope.builder()
                      .accountIdentifier(userGroup.getAccountIdentifier())
                      .orgIdentifier(userGroup.getOrgIdentifier())
                      .projectIdentifier(userGroup.getProjectIdentifier())
                      .build();
    List<String> userIds = ngUserService.listUserIds(scope);
    Sets.SetView<String> invalidUserIds = Sets.difference(new HashSet<>(userGroup.getUsers()), new HashSet<>(userIds));
    if (isNotEmpty(invalidUserIds)) {
      throw new InvalidArgumentsException(getInvalidUserMessage(invalidUserIds));
    }
  }

  private void validateFilter(UserGroupFilterDTO filter) {
    if (isNotEmpty(filter.getIdentifierFilter()) && isNotEmpty(filter.getDatabaseIdFilter())) {
      throw new InvalidArgumentsException("Both the database id filter and identifier filter cannot be provided");
    }
  }

  private String getInvalidUserMessage(Set<String> invalidUserIds) {
    return String.format("The following user%s not valid: [%s]", invalidUserIds.size() > 1 ? "s are" : " is",
        String.join(", ", invalidUserIds));
  }

  private void validateNotificationSettings(List<NotificationSettingConfig> notificationSettingConfigs) {
    Set<NotificationChannelType> typeSet = new HashSet<>();
    for (NotificationSettingConfig config : notificationSettingConfigs) {
      if (typeSet.contains(config.getType())) {
        throw new InvalidArgumentsException(
            "Not allowed to create multiple notification setting of type: " + config.getType());
      }
      typeSet.add(config.getType());
    }
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(UserGroupKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(UserGroupKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }

  private Criteria createUserGroupFilterCriteria(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, UserGroupFilterType filterType) {
    Criteria criteria;
    if (filterType == INCLUDE_INHERITED_GROUPS) {
      criteria = createScopeCriteriaIncludingInheritedUserGroups(accountIdentifier, orgIdentifier, projectIdentifier);
    } else {
      criteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    }
    if (isNotBlank(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
          Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i"));
      criteria.andOperator(searchCriteria);
    }
    return criteria;
  }

  private Criteria createScopeCriteriaIncludingInheritedUserGroups(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    Criteria scopeCriteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    Set<String> principalScopeLevelFilters = new HashSet<>();

    if ((isNotEmpty(projectIdentifier) || isNotEmpty(orgIdentifier))
        && accessControlClient.hasAccess(
            ResourceScope.of(accountIdentifier, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION)) {
      principalScopeLevelFilters.add(ScopeLevel.of(accountIdentifier, null, null).toString().toLowerCase());
    }
    if (isNotEmpty(projectIdentifier)
        && accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, null),
            Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION)) {
      principalScopeLevelFilters.add(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    }

    if (isNotEmpty(principalScopeLevelFilters)) {
      // call access control and get inherited user group ids
      RoleAssignmentFilterDTO roleAssignmentFilterDTO = RoleAssignmentFilterDTO.builder()
                                                            .principalTypeFilter(Collections.singleton(USER_GROUP))
                                                            .principalScopeLevelFilter(principalScopeLevelFilters)
                                                            .build();
      RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO =
          NGRestUtils.getResponse(accessControlAdminClient.getAggregatedFilteredRoleAssignments(
              accountIdentifier, orgIdentifier, projectIdentifier, roleAssignmentFilterDTO));
      if (isEmpty(roleAssignmentAggregateResponseDTO.getRoleAssignments())) {
        return scopeCriteria;
      }
      criteria.orOperator(
          roleAssignmentAggregateResponseDTO.getRoleAssignments()
              .stream()
              .map(roleAssignmentDTO
                  -> Criteria.where(UserGroupKeys.identifier)
                         .is(roleAssignmentDTO.getPrincipal().getIdentifier())
                         .andOperator(createScopeCriteriaFromScopeLevel(accountIdentifier, orgIdentifier,
                             projectIdentifier, roleAssignmentDTO.getPrincipal().getScopeLevel())))
              .toArray(Criteria[] ::new)

      );
      return new Criteria().orOperator(criteria, scopeCriteria);
    }
    return scopeCriteria;
  }

  private Criteria createScopeCriteriaFromScopeLevel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String scopeLevel) {
    Criteria criteria = new Criteria();
    if (scopeLevel.equalsIgnoreCase(ScopeLevel.ACCOUNT.toString())) {
      criteria.and(UserGroupKeys.accountIdentifier)
          .is(accountIdentifier)
          .and(UserGroupKeys.orgIdentifier)
          .exists(false)
          .and(UserGroupKeys.projectIdentifier)
          .exists(false);
    } else if (scopeLevel.equalsIgnoreCase(ScopeLevel.ORGANIZATION.toString())) {
      criteria.and(UserGroupKeys.accountIdentifier)
          .is(accountIdentifier)
          .and(UserGroupKeys.orgIdentifier)
          .is(orgIdentifier)
          .and(UserGroupKeys.projectIdentifier)
          .exists(false);
    } else if (scopeLevel.equalsIgnoreCase(ScopeLevel.PROJECT.toString())) {
      criteria.and(UserGroupKeys.accountIdentifier)
          .is(accountIdentifier)
          .and(UserGroupKeys.orgIdentifier)
          .is(orgIdentifier)
          .and(UserGroupKeys.projectIdentifier)
          .is(projectIdentifier);
    }
    return criteria;
  }

  private Criteria createUserGroupFetchCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    criteria.and(UserGroupKeys.identifier).is(identifier);
    return criteria;
  }

  private Criteria getUserGroupbySsoIdCriteria(String accountIdentifier, String ssoId) {
    Criteria criteria = createScopeCriteria(accountIdentifier, null, null);
    criteria.and(UserGroupKeys.isSsoLinked).is(true);
    criteria.and(UserGroupKeys.linkedSsoId).is(ssoId);
    return criteria;
  }

  @Override
  public List<UserGroup> getUserGroupsBySsoId(String accountIdentifier, String ssoId) {
    Criteria criteria = getUserGroupbySsoIdCriteria(accountIdentifier, ssoId);
    return userGroupRepository.findAll(criteria);
  }

  @Override
  public List<UserGroup> getUserGroupsBySsoId(String ssoId) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.isSsoLinked).is(true);
    criteria.and(UserGroupKeys.linkedSsoId).is(ssoId);
    return userGroupRepository.findAll(criteria);
  }

  @Override
  public List<UserGroup> getExternallyManagedGroups(String accountIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(UserGroupKeys.externallyManaged).is(true);
    return userGroupRepository.findAll(criteria);
  }

  private Criteria getUserGroupbySsoIdCriteria(String ssoId) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.isSsoLinked).is(true);
    criteria.and(UserGroupKeys.linkedSsoId).is(ssoId);
    return criteria;
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public UserGroup linkToSsoGroup(@NotBlank @AccountIdentifier String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotBlank String userGroupIdentifier, @NotNull SSOType ssoType, @NotBlank String ssoId,
      @NotBlank String ssoGroupId, @NotBlank String ssoGroupName) {
    UserGroup existingUserGroup = getOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    UserGroupDTO oldUserGroup = (UserGroupDTO) HObjectMapper.clone(toDTO(existingUserGroup));

    if (TRUE.equals(existingUserGroup.getIsSsoLinked())) {
      throw new InvalidRequestException("SSO Provider already linked to the group. Try unlinking first.");
    }

    SSOConfig ssoConfig = getResponse(managerClient.getAccountAccessManagementSettings(accountIdentifier));

    List<SSOSettings> ssoSettingsList = ssoConfig.getSsoSettings();
    SSOSettings ssoSettings = null;
    for (SSOSettings ssoSetting : ssoSettingsList) {
      if (ssoSetting.getUuid().equals(ssoId)) {
        ssoSettings = ssoSetting;
        break;
      }
    }

    if (null == ssoSettings) {
      throw new InvalidRequestException("Invalid ssoId");
    }
    existingUserGroup.setIsSsoLinked(TRUE);
    existingUserGroup.setLinkedSsoType(ssoType);
    existingUserGroup.setLinkedSsoId(ssoId);
    existingUserGroup.setLinkedSsoDisplayName(ssoSettings.getDisplayName());
    existingUserGroup.setSsoGroupId(ssoGroupId);
    existingUserGroup.setSsoGroupName(ssoGroupName);

    return updateInternal(existingUserGroup, oldUserGroup);
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public UserGroup unlinkSsoGroup(@NotBlank @AccountIdentifier String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotBlank String userGroupIdentifier, boolean retainMembers) {
    UserGroup existingUserGroup = getOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    UserGroupDTO oldUserGroup = (UserGroupDTO) HObjectMapper.clone(toDTO(existingUserGroup));

    if (FALSE.equals(existingUserGroup.getIsSsoLinked()) || existingUserGroup.getIsSsoLinked() == null) {
      throw new InvalidRequestException("Group is not linked to any SSO group.");
    }

    if (!retainMembers) {
      existingUserGroup.setUsers(emptyList());
      existingUserGroup = updateInternal(existingUserGroup, oldUserGroup);
    }

    existingUserGroup.setSsoGroupId(null);
    existingUserGroup.setIsSsoLinked(FALSE);
    existingUserGroup.setSsoGroupName(null);
    existingUserGroup.setLinkedSsoId(null);
    existingUserGroup.setLinkedSsoType(null);
    existingUserGroup.setLinkedSsoDisplayName(null);

    return updateInternal(existingUserGroup, oldUserGroup);
  }

  @Override
  public void sanitize(Scope scope, String identifier) {
    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), identifier);
    if (userGroupOptional.isPresent()) {
      UserGroup userGroup = userGroupOptional.get();
      List<String> currentUserIds = userGroup.getUsers();
      Set<String> uniqueUserIds = new HashSet<>(currentUserIds);

      List<String> userIds = ngUserService.listUserIds(scope);
      Set<String> invalidUserIds = new HashSet<>(Sets.difference(uniqueUserIds, new HashSet<>(userIds)));
      uniqueUserIds.removeAll(invalidUserIds);
      userGroup.setUsers(new ArrayList<>(uniqueUserIds));

      userGroupRepository.save(userGroup);
    }
  }
}
