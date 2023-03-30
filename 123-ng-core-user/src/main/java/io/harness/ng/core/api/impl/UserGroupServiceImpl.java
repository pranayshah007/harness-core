/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.GROUP;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.ng.core.usergroups.filter.UserGroupFilterType.INCLUDE_CHILD_SCOPE_GROUPS;
import static io.harness.ng.core.usergroups.filter.UserGroupFilterType.INCLUDE_INHERITED_GROUPS;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;
import static io.harness.ng.core.utils.UserGroupMapper.toEntity;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.CGRestUtils.getResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.ScopeFilterType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.data.structure.CollectionUtils;
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
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.ng.core.dto.ScopeSelector;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UserInfo;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.ng.core.events.UserGroupCreateEvent;
import io.harness.ng.core.events.UserGroupDeleteEvent;
import io.harness.ng.core.events.UserGroupUpdateEvent;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserGroup.UserGroupKeys;
import io.harness.ng.core.user.entities.UserMetadata;
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
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.ScopeUtils;

import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.security.authentication.SSOConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;
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
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private static final List<String> defaultUserGroups = ImmutableList.of(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER,
      DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER);

  @Inject
  public UserGroupServiceImpl(UserGroupRepository userGroupRepository, UserClient userClient,
      OutboxService outboxService, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      NgUserService ngUserService, AuthSettingsManagerClient managerClient, LastAdminCheckService lastAdminCheckService,
      AccessControlAdminClient accessControlAdminClient, AccessControlClient accessControlClient,
      ScopeNameMapper scopeNameMapper, NGFeatureFlagHelperService ngFeatureFlagHelperService) {
    this.userGroupRepository = userGroupRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.ngUserService = ngUserService;
    this.managerClient = managerClient;
    this.lastAdminCheckService = lastAdminCheckService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.accessControlClient = accessControlClient;
    this.scopeNameMapper = scopeNameMapper;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
  }

  @Override
  public UserGroup create(UserGroupDTO userGroupDTO) {
    if (userGroupDTO.isHarnessManaged() || defaultUserGroups.contains(userGroupDTO.getIdentifier())) {
      throw new InvalidRequestException("Cannot create a harness managed user group");
    }
    return createInternal(userGroupDTO);
  }

  public UserGroup createDefaultUserGroup(UserGroupDTO userGroupDTO) {
    return createInternal(userGroupDTO);
  }

  private UserGroup createInternal(UserGroupDTO userGroupDTO) {
    try {
      UserGroup userGroup = toEntity(userGroupDTO);
      validate(userGroup);
      sanitizeInternal(userGroup);
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
    checkUpdateForHarnessManagedGroup(userGroupDTO, savedUserGroup);
    UserGroup userGroup = toEntity(userGroupDTO);
    userGroup.setId(savedUserGroup.getId());
    userGroup.setVersion(savedUserGroup.getVersion());
    return updateInternal(userGroup, toDTO(savedUserGroup));
  }

  @Override
  public UserGroup updateDefaultUserGroup(UserGroup userGroup) {
    UserGroup savedUserGroup = getOrThrow(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
        userGroup.getProjectIdentifier(), userGroup.getIdentifier());
    return updateInternal(userGroup, toDTO(savedUserGroup));
  }

  @Override
  public UserGroup updateWithCheckThatSCIMFieldsAreNotModified(UserGroupDTO userGroupDTO) {
    UserGroup savedUserGroup = getOrThrow(userGroupDTO.getAccountIdentifier(), userGroupDTO.getOrgIdentifier(),
        userGroupDTO.getProjectIdentifier(), userGroupDTO.getIdentifier());
    checkIfSCIMFieldsAreNotUpdatedInExternallyManagedGroup(userGroupDTO, savedUserGroup);
    return update(userGroupDTO);
  }

  private void checkIfSCIMFieldsAreNotUpdatedInExternallyManagedGroup(
      UserGroupDTO toBeSavedUserGroup, UserGroup savedUserGroup) {
    if (!isExternallyManaged(toBeSavedUserGroup.getAccountIdentifier(), toBeSavedUserGroup.getOrgIdentifier(),
            toBeSavedUserGroup.getProjectIdentifier(), toBeSavedUserGroup.getIdentifier())) {
      return;
    }

    cannotUpdateUsers(toBeSavedUserGroup, savedUserGroup, "Update is not supported for externally managed group ");
    cannotChangeUserGroupName(
        toBeSavedUserGroup, savedUserGroup, "The name cannot be updated for externally managed group");
  }

  private void checkUpdateForHarnessManagedGroup(UserGroupDTO toBeSavedUserGroup, UserGroup savedUserGroup) {
    if (defaultUserGroups.contains(savedUserGroup.getIdentifier()) || savedUserGroup.isHarnessManaged()) {
      cannotUpdateUsers(
          toBeSavedUserGroup, savedUserGroup, "Updating users is not supported for harness managed group ");
      cannotChangeUserGroupName(
          toBeSavedUserGroup, savedUserGroup, "The name cannot be updated for harness managed group.");
    }
  }

  private void cannotUpdateUsers(UserGroupDTO toBeSavedUserGroup, UserGroup savedUserGroup, String errorMessage) {
    List<String> newUsersToBeAdded = toBeSavedUserGroup.getUsers();
    List<String> savedUsers = savedUserGroup.getUsers();
    if (!CollectionUtils.isEqualCollection(newUsersToBeAdded, savedUsers)) {
      throw new InvalidRequestException(errorMessage + toBeSavedUserGroup.getIdentifier());
    }
  }

  private void cannotChangeUserGroupName(
      UserGroupDTO toBeSavedUserGroup, UserGroup savedUserGroup, String errorMessage) {
    if (!savedUserGroup.getName().equals(toBeSavedUserGroup.getName())) {
      throw new InvalidRequestException(errorMessage);
    }
  }

  @Override
  public List<UserInfo> getUserMetaData(List<String> uuids) {
    return ngUserService.getUserMetadata(uuids)
        .stream()
        .map(user -> UserInfo.builder().id(user.getUuid()).email(user.getEmail()).build())
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getUserIds(List<String> emails) {
    return ngUserService.getUserIdsByEmails(emails);
  }

  @Override
  public Page<UserGroup> list(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, UserGroupFilterType filterType, Pageable pageable) {
    Criteria criteria =
        createUserGroupFilterCriteria(accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, filterType);
    if (!accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION)) {
      List<UserGroup> userGroups = userGroupRepository.findAll(criteria, Pageable.unpaged()).getContent();
      userGroups = getPermittedUserGroups(userGroups);
      if (isEmpty(userGroups)) {
        return Page.empty();
      }
      criteria.and(UserGroupKeys.identifier)
          .in(userGroups.stream().map(UserGroup::getIdentifier).collect(Collectors.toList()));
    }
    return userGroupRepository.findAll(criteria, pageable);
  }

  @Override
  public Long countUserGroups(String accountIdentifier) {
    return userGroupRepository.countByAccountIdentifierAndDeletedIsFalse(accountIdentifier);
  }

  @Override
  public List<UserGroup> getUserGroupsForUser(String accountIdentifier, String userId) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(UserGroupKeys.externallyManaged).is(true);
    criteria.and(UserGroupKeys.users).in(userId);
    return userGroupRepository.findAll(criteria);
  }

  @Override
  public List<UserGroup> getPermittedUserGroups(List<UserGroup> userGroups) {
    if (isEmpty(userGroups)) {
      return Collections.emptyList();
    }

    Map<EntityScopeInfo, List<UserGroup>> allUserGroupScopesMap =
        userGroups.stream().collect(Collectors.groupingBy(UserGroupServiceImpl::getEntityScopeInfoFromUserGroup));

    List<PermissionCheckDTO> permissionChecks =
        userGroups.stream()
            .map(usergroup
                -> PermissionCheckDTO.builder()
                       .permission(VIEW_USERGROUP_PERMISSION)
                       .resourceIdentifier(usergroup.getIdentifier())
                       .resourceScope(ResourceScope.of(usergroup.getAccountIdentifier(), usergroup.getOrgIdentifier(),
                           usergroup.getProjectIdentifier()))
                       .resourceType(USERGROUP)
                       .build())
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<UserGroup> permittedUserGroups = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedUserGroups.add(
            allUserGroupScopesMap.get(getEntityScopeInfoFromAccessControlDTO(accessControlDTO)).get(0));
      }
    }
    return permittedUserGroups;
  }

  @Override
  public Page<UserGroup> list(
      List<ScopeSelector> scopeFilter, String userIdentifier, String searchTerm, Pageable pageable) {
    Criteria criteria = createUserGroupCriteriaByUser(scopeFilter, userIdentifier, searchTerm);
    return userGroupRepository.findAll(criteria, pageable);
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
  public List<UserGroup> list(Criteria criteria, Integer skip, Integer limit) {
    return userGroupRepository.findAll(criteria, skip, limit);
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
  public CloseableIterator<UserMetadata> getUsersInUserGroup(Scope scope, String userGroupIdentifier) {
    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    if (!userGroupOptional.isPresent()) {
      return null;
    }
    Set<String> userGroupMemberIds = new HashSet<>(userGroupOptional.get().getUsers());

    return ngUserService.streamUserMetadata(new ArrayList<>(userGroupMemberIds));
  }

  @Override
  public UserGroup delete(Scope scope, String identifier) {
    validateAtleastOneAdminExistAfterRemoval(scope, identifier, null);

    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), identifier);
    cannotDeleteHarnessManagedGroup(userGroupOptional);
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

      List<UserGroup> deleteUserGroups = userGroupRepository.findAllAndDelete(criteria);
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
    checkToHarnessManagedUserGroup(existingUserGroup);
    return addMemberInternal(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier, userIdentifier);
  }

  @Override
  public UserGroup addMemberToDefaultUserGroup(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userGroupIdentifier, String userIdentifier) {
    return addMemberInternal(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier, userIdentifier);
  }

  private UserGroup addMemberInternal(String accountIdentifier, String orgIdentifier, String projectIdentifier,
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
    checkToHarnessManagedUserGroup(existingUserGroup);
    return removeMemberInternal(scope, userGroupIdentifier, userIdentifier);
  }

  private void checkToHarnessManagedUserGroup(UserGroup existingUserGroup) {
    if (existingUserGroup.isHarnessManaged()) {
      throw new InvalidRequestException("Cannot add or remove user from an harness managed user group.");
    }
  }

  private UserGroup removeMemberInternal(Scope scope, String userGroupIdentifier, String userIdentifier) {
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
    List<UserGroup> userGroups = userGroupRepository.findAll(criteria, null, null);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    List<String> userGroupIdentifiers = userGroups.stream().map(UserGroup::getIdentifier).collect(Collectors.toList());
    userGroupIdentifiers.forEach(
        userGroupIdentifier -> removeMemberInternal(scope, userGroupIdentifier, userIdentifier));
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
    sanitizeInternal(newUserGroup);
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
  }

  private void validateFilter(UserGroupFilterDTO filter) {
    if (isNotEmpty(filter.getIdentifierFilter()) && isNotEmpty(filter.getDatabaseIdFilter())) {
      throw new InvalidArgumentsException("Both the database id filter and identifier filter cannot be provided");
    }
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

  private Criteria createChildScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.accountIdentifier).is(accountIdentifier);
    if (isNotEmpty(orgIdentifier)) {
      criteria.and(UserGroupKeys.orgIdentifier).is(orgIdentifier);
    }
    if (isNotEmpty(projectIdentifier)) {
      criteria.and(UserGroupKeys.projectIdentifier).is(projectIdentifier);
    }
    return criteria;
  }

  @VisibleForTesting
  protected Criteria createUserGroupFilterCriteria(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, UserGroupFilterType filterType) {
    Criteria criteria;
    if (filterType == INCLUDE_INHERITED_GROUPS) {
      criteria = createScopeCriteriaIncludingInheritedUserGroups(accountIdentifier, orgIdentifier, projectIdentifier);
    } else if (filterType == INCLUDE_CHILD_SCOPE_GROUPS) {
      criteria = createChildScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
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

  private Criteria createUserGroupCriteriaByUser(
      List<ScopeSelector> scopeFilter, String userIdentifier, String searchTerm) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.users).in(userIdentifier);
    Criteria[] scopeCriteriaList =
        scopeFilter.stream()
            .map(scope -> {
              if (ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(scope.getFilter())) {
                Criteria scopeCriteria =
                    Criteria.where(UserGroupKeys.accountIdentifier).is(scope.getAccountIdentifier());
                if (isNotEmpty(scope.getOrgIdentifier())) {
                  scopeCriteria.and(UserGroupKeys.orgIdentifier).is(scope.getOrgIdentifier());
                }
                if (isNotEmpty(scope.getProjectIdentifier())) {
                  scopeCriteria.and(UserGroupKeys.projectIdentifier).is(scope.getProjectIdentifier());
                }
                return scopeCriteria;
              } else {
                return Criteria.where(UserGroupKeys.accountIdentifier)
                    .is(scope.getAccountIdentifier())
                    .and(UserGroupKeys.orgIdentifier)
                    .is(scope.getOrgIdentifier())
                    .and(UserGroupKeys.projectIdentifier)
                    .is(scope.getProjectIdentifier());
              }
            })
            .toArray(Criteria[] ::new);
    if (isNotEmpty(scopeCriteriaList)) {
      criteria.orOperator(scopeCriteriaList);
    }
    if (isNotBlank(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(Criteria.where(UserGroupKeys.name).regex(searchTerm, "i"),
          Criteria.where(UserGroupKeys.tags).regex(searchTerm, "i"));
      criteria.andOperator(searchCriteria);
    }
    return criteria;
  }

  private Criteria getUserGroupbySsoIdCriteria(String accountIdentifier, String ssoId) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(UserGroupKeys.isSsoLinked).is(true);
    criteria.and(UserGroupKeys.linkedSsoId).is(ssoId);
    return criteria;
  }

  @Override
  public List<UserGroup> getUserGroupsBySsoId(String accountIdentifier, String ssoId) {
    Criteria criteria = getUserGroupbySsoIdCriteria(accountIdentifier, ssoId);
    return userGroupRepository.findAll(criteria, null, null);
  }

  @Override
  public List<UserGroup> getUserGroupsBySsoId(String ssoId) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.isSsoLinked).is(true);
    criteria.and(UserGroupKeys.linkedSsoId).is(ssoId);
    return userGroupRepository.findAll(criteria, null, null);
  }

  @Override
  public List<UserGroup> getExternallyManagedGroups(String accountIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroupKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(UserGroupKeys.externallyManaged).is(true);
    return userGroupRepository.findAll(criteria, null, null);
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public UserGroup linkToSsoGroup(@NotBlank @AccountIdentifier String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotBlank String userGroupIdentifier, @NotNull SSOType ssoType, @NotBlank String ssoId,
      @NotBlank String ssoGroupId, @NotBlank String ssoGroupName) {
    UserGroup existingUserGroup = getOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    UserGroupDTO oldUserGroup = (UserGroupDTO) HObjectMapper.clone(toDTO(existingUserGroup));

    if (TRUE.equals(existingUserGroup.isHarnessManaged())
        || defaultUserGroups.contains(existingUserGroup.getIdentifier())) {
      throw new InvalidRequestException("Cannot link SSO Provider to the Harness Managed group.");
    }
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
  public void sanitize(Scope scope, String userGroupIdentifier) {
    Optional<UserGroup> userGroupOptional =
        get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), userGroupIdentifier);
    if (userGroupOptional.isPresent()) {
      UserGroup userGroup = userGroupOptional.get();
      sanitizeInternal(userGroup);
      userGroupRepository.save(userGroup);
    }
  }

  private void sanitizeInternal(UserGroup userGroup) {
    if (userGroup.getUsers() != null) {
      Set<String> uniqueUsers = new HashSet<>(userGroup.getUsers());
      Set<String> invalidUsers = getAllInvalidUsers(
          Scope.of(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier()),
          uniqueUsers);
      uniqueUsers.removeAll(invalidUsers);
      userGroup.setUsers(new ArrayList<>(uniqueUsers));
    }
  }

  private Set<String> getAllInvalidUsers(Scope scope, Set<String> currentUserIds) {
    List<String> userIds = ngUserService.listUserIds(scope);
    userIds = userIds == null ? new ArrayList<>() : userIds;
    return new HashSet<>(Sets.difference(currentUserIds, new HashSet<>(userIds)));
  }

  private void cannotDeleteHarnessManagedGroup(Optional<UserGroup> userGroupOptional) {
    if (userGroupOptional.isPresent()
        && (userGroupOptional.get().isHarnessManaged()
            || defaultUserGroups.contains(userGroupOptional.get().getIdentifier()))) {
      throw new InvalidRequestException("Cannot delete a harness managed user group.");
    }
  }

  private static EntityScopeInfo getEntityScopeInfoFromUserGroup(UserGroup userGroup) {
    return EntityScopeInfo.builder()
        .accountIdentifier(userGroup.getAccountIdentifier())
        .orgIdentifier(isBlank(userGroup.getOrgIdentifier()) ? null : userGroup.getOrgIdentifier())
        .projectIdentifier(isBlank(userGroup.getProjectIdentifier()) ? null : userGroup.getProjectIdentifier())
        .identifier(userGroup.getIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }
}