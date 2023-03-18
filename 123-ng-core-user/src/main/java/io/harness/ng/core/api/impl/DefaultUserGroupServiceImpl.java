/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.ACCOUNT_BASIC_ROLE;
import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_BASIC_ROLE;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_BASIC_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.exception.DuplicateFieldException;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class DefaultUserGroupServiceImpl implements DefaultUserGroupService {
  private final UserGroupService userGroupService;
  private final AccessControlAdminClient accessControlAdminClient;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final UserMembershipRepository userMembershipRepository;
  private static final String DEBUG_MESSAGE = "DefaultUserGroupServiceImpl: ";

  @Inject
  public DefaultUserGroupServiceImpl(UserGroupService userGroupService,
      AccessControlAdminClient accessControlAdminClient, NGFeatureFlagHelperService ngFeatureFlagHelperService,
      UserMembershipRepository userMembershipRepository) {
    this.userGroupService = userGroupService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.userMembershipRepository = userMembershipRepository;
  }

  @Override
  public UserGroup create(Scope scope, List<String> userIds) {
    String userGroupIdentifier = getUserGroupIdentifier(scope);
    String userGroupName = getUserGroupName(scope);
    String userGroupDescription = getUserGroupDescription(scope);
    UserGroup userGroup = null;
    try {
      UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                                      .accountIdentifier(scope.getAccountIdentifier())
                                      .orgIdentifier(scope.getOrgIdentifier())
                                      .projectIdentifier(scope.getProjectIdentifier())
                                      .name(userGroupName)
                                      .description(userGroupDescription)
                                      .identifier(userGroupIdentifier)
                                      .isSsoLinked(false)
                                      .externallyManaged(false)
                                      .users(userIds == null ? emptyList() : userIds)
                                      .harnessManaged(true)
                                      .build();

      userGroup = userGroupService.createDefaultUserGroup(userGroupDTO);
      if (isNotEmpty(scope.getProjectIdentifier())) {
        createRoleAssignmentForProject(userGroupIdentifier, scope, true);
      } else if (isNotEmpty(scope.getOrgIdentifier())) {
        createRoleAssignmentsForOrganization(userGroupIdentifier, scope, true);
      } else {
        createRoleAssignmentsForAccount(userGroupIdentifier, scope, true);
      }
      log.info(DEBUG_MESSAGE + "Created default user group {} at scope {}", userGroupIdentifier, scope);
      return userGroup;
    } catch (DuplicateFieldException ex) {
      // Safe to assume Default User Group is created.
      log.info(DEBUG_MESSAGE + String.format("Safe to assume Default User Group is created at scope %s", scope));
    }
    return userGroup;
  }

  private String getUserGroupIdentifier(Scope scope) {
    String userGroupIdentifier = DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
    if (isNotEmpty(scope.getProjectIdentifier())) {
      userGroupIdentifier = DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      userGroupIdentifier = DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
    }
    return userGroupIdentifier;
  }

  @Override
  public boolean isDefaultUserGroup(Scope scope, String identifier) {
    return getUserGroupIdentifier(scope).equals(identifier);
  }

  @VisibleForTesting
  protected String getUserGroupName(Scope scope) {
    String userGroupName = "All Account Users";
    if (isNotEmpty(scope.getProjectIdentifier())) {
      userGroupName = "All Project Users";
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      userGroupName = "All Organization Users";
    }
    return userGroupName;
  }

  @VisibleForTesting
  protected String getUserGroupDescription(Scope scope) {
    String userGroupDescription = "Harness managed User Group containing all the users in the account.";
    if (isNotEmpty(scope.getProjectIdentifier())) {
      userGroupDescription = "Harness managed User Group containing all the users in the project.";
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      userGroupDescription = "Harness managed User Group containing all the users in the organization.";
    }
    return userGroupDescription;
  }

  private void createRoleAssignmentsForOrganization(
      String userGroupIdentifier, Scope scope, boolean createOrgViewerRoleBinding) {
    createRoleAssignmentAtOrgOrProject(userGroupIdentifier, scope, createOrgViewerRoleBinding, ORGANIZATION_BASIC_ROLE,
        DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, ORGANIZATION_VIEWER_ROLE);
  }

  private void createRoleAssignmentForProject(
      String userGroupIdentifier, Scope scope, boolean createProjectViewerRoleBinding) {
    createRoleAssignmentAtOrgOrProject(userGroupIdentifier, scope, createProjectViewerRoleBinding, PROJECT_BASIC_ROLE,
        DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER, PROJECT_VIEWER_ROLE);
  }

  private void createRoleAssignmentAtOrgOrProject(String userGroupIdentifier, Scope scope,
      boolean createViewerRoleBinding, String basicRole, String defaultResourceGroupIdentifier,
      String defaultViewerRole) {
    boolean isOrgProjectBasicFeatureFlagEnabled = ngFeatureFlagHelperService.isEnabled(
        scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS);
    if (isOrgProjectBasicFeatureFlagEnabled) {
      createRoleAssignment(userGroupIdentifier, scope, true, true, basicRole, defaultResourceGroupIdentifier);
      if (createViewerRoleBinding) {
        createRoleAssignment(
            userGroupIdentifier, scope, false, false, defaultViewerRole, defaultResourceGroupIdentifier);
      }
    } else {
      createRoleAssignment(userGroupIdentifier, scope, true, false, defaultViewerRole, defaultResourceGroupIdentifier);
    }
  }

  @VisibleForTesting
  protected void createRoleAssignment(String userGroupIdentifier, Scope scope, boolean managed, boolean internal,
      String roleIdentifier, String resourceGroupIdentifier) {
    try {
      List<RoleAssignmentDTO> roleAssignmentDTOList = new ArrayList<>();
      roleAssignmentDTOList.add(
          RoleAssignmentDTO.builder()
              .resourceGroupIdentifier(resourceGroupIdentifier)
              .roleIdentifier(roleIdentifier)
              .disabled(false)
              .managed(managed)
              .internal(internal)
              .principal(PrincipalDTO.builder()
                             .identifier(userGroupIdentifier)
                             .scopeLevel(ScopeLevel
                                             .of(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                                 scope.getProjectIdentifier())
                                             .name()
                                             .toLowerCase())
                             .type(USER_GROUP)
                             .build())
              .build());

      RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO =
          RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignmentDTOList).build();

      NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier(), managed, roleAssignmentCreateRequestDTO));
      log.info("Created role assignment for all users usergroup {} at scope {}", userGroupIdentifier, scope);
    } catch (Exception ex) {
      log.error(
          "Creation of role assignment for all users usergroup {} at scope {} failed", userGroupIdentifier, scope);
    }
  }

  private void createRoleAssignmentsForAccount(
      String principalIdentifier, Scope scope, boolean createAccountViewerRoleBinding) {
    createRoleAssignment(
        principalIdentifier, scope, true, true, ACCOUNT_BASIC_ROLE, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    boolean isAccountBasicFeatureFlagEnabled =
        ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.ACCOUNT_BASIC_ROLE_ONLY);
    if (!isAccountBasicFeatureFlagEnabled && createAccountViewerRoleBinding) {
      createRoleAssignment(principalIdentifier, scope, false, false, ACCOUNT_VIEWER_ROLE,
          DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    }
  }

  @Override
  public void addUserToDefaultUserGroup(Scope scope, String userId) {
    addUserToDefaultUserGroup(
        scope.getAccountIdentifier(), null, null, DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER, userId);
    if (!isEmpty(scope.getOrgIdentifier())) {
      addUserToDefaultUserGroup(scope.getAccountIdentifier(), scope.getOrgIdentifier(), null,
          DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, userId);
    }
    if (!isEmpty(scope.getProjectIdentifier())) {
      addUserToDefaultUserGroup(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, userId);
    }
  }

  private void addUserToDefaultUserGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userGroupId, String userId) {
    Optional<UserGroup> userGroupOptional =
        userGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId);
    if (userGroupOptional.isPresent()
        && !userGroupService.checkMember(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId, userId)) {
      userGroupService.addMemberToDefaultUserGroup(
          accountIdentifier, orgIdentifier, projectIdentifier, userGroupId, userId);
    }
  }

  @Override
  public Optional<UserGroup> get(Scope scope) {
    String identifier = getUserGroupIdentifier(scope);
    return userGroupService.get(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), identifier);
  }

  @Override
  public UserGroup createOrUpdateUserGroupAtScope(Scope scope) {
    List<String> allUsersAtScope = getAllUsersAtScope(scope);

    try {
      Optional<UserGroup> optionalUserGroup = get(scope);
      if (optionalUserGroup.isPresent()) {
        createRoleAssignmentAtScope(scope);
        return addUsersToExistingGroup(scope, optionalUserGroup.get(), allUsersAtScope);
      }
      return create(scope, allUsersAtScope);

    } catch (Exception ex) {
      log.error(String.format("Something went wrong while create/update User Group at scope: %s", scope), ex);
      throw ex;
    }
  }

  private UserGroup addUsersToExistingGroup(Scope scope, UserGroup userGroup, List<String> allUsersAtScope) {
    try {
      List<String> currentUsers = userGroup.getUsers();
      HashSet<String> existingUsers = new HashSet<>(currentUsers);
      HashSet<String> newUsers = new HashSet<>(allUsersAtScope);
      HashSet<String> usersToAdd = new HashSet<>(Sets.difference(newUsers, existingUsers));
      if (isNotEmpty(usersToAdd)) {
        currentUsers = new ArrayList<>(currentUsers);
        currentUsers.addAll(usersToAdd);
        userGroup.setUsers(currentUsers);
        UserGroup updatedUserGroup = userGroupService.updateDefaultUserGroup(userGroup);
        log.info(DEBUG_MESSAGE + String.format("Added %s users to user group at scope %s", usersToAdd.size(), scope));
        return updatedUserGroup;
      } else {
        log.info(DEBUG_MESSAGE + String.format("No users left to be added in user group at scope %s", scope));
        return userGroup;
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to update User Group at scope: " + scope, ex);
      throw ex;
    }
  }

  private List<String> getAllUsersAtScope(Scope scope) {
    try {
      int pageIndex = 0;
      int pageSize = 1000;
      List<String> allUsersAtScope = new ArrayList<>();
      do {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Criteria criteria = Criteria.where(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                                .is(scope.getAccountIdentifier())
                                .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                                .is(scope.getOrgIdentifier())
                                .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                                .is(scope.getProjectIdentifier());
        List<String> userIds = userMembershipRepository.findAllUserIds(criteria, pageable).getContent();
        if (isEmpty(userIds)) {
          break;
        }
        log.info(DEBUG_MESSAGE + String.format("Fetched %s users at scope %s", userIds.size(), scope));
        allUsersAtScope.addAll(userIds);
        pageIndex++;
      } while (true);
      return allUsersAtScope;
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + String.format("Fetching all users failed at scope %s", scope));
      return emptyList();
    }
  }

  private void createRoleAssignmentAtScope(Scope scope) {
    boolean isOrgProjectBasicFeatureFlagEnabled = ngFeatureFlagHelperService.isEnabled(
        scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS);
    Optional<List<RoleAssignmentResponseDTO>> optionalRoleAssignmentResponseDTO = getRoleAssignmentsAtScope(scope);
    if (optionalRoleAssignmentResponseDTO.isPresent() && isEmpty(optionalRoleAssignmentResponseDTO.get())) {
      String userGroupIdentifier = getUserGroupIdentifier(scope);
      if (isNotEmpty(scope.getProjectIdentifier())) {
        if (isOrgProjectBasicFeatureFlagEnabled) {
          createRoleAssignmentForProject(userGroupIdentifier, scope, false);
        }
      } else if (isNotEmpty(scope.getOrgIdentifier())) {
        if (isOrgProjectBasicFeatureFlagEnabled) {
          createRoleAssignmentsForOrganization(userGroupIdentifier, scope, false);
        }
      } else {
        createRoleAssignmentsForAccount(userGroupIdentifier, scope, false);
      }
    }
  }

  @VisibleForTesting
  protected Optional<List<RoleAssignmentResponseDTO>> getRoleAssignmentsAtScope(Scope scope) {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO;
    if (isNotEmpty(scope.getProjectIdentifier())) {
      roleAssignmentFilterDTO = getRoleAssignmentFilterDTOForProjectScope();
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      roleAssignmentFilterDTO = getRoleAssignmentFilterDTOForOrganizationScope();
    } else {
      roleAssignmentFilterDTO = getRoleAssignmentFilterDTOForAccountScope();
    }
    try {
      List<RoleAssignmentResponseDTO> roleAssignments =
          NGRestUtils
              .getResponse(
                  accessControlAdminClient.getFilteredRoleAssignmentsWithInternalRoles(scope.getAccountIdentifier(),
                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), 0, 2, roleAssignmentFilterDTO))
              .getContent();
      return Optional.of(roleAssignments);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + String.format("Role Assignment fetch failed at scope: %s", scope));
    }
    return Optional.empty();
  }

  private RoleAssignmentFilterDTO getRoleAssignmentFilterDTOForAccountScope() {
    return RoleAssignmentFilterDTO.builder()
        .resourceGroupFilter(Collections.singleton(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER))
        .roleFilter(Collections.singleton(ACCOUNT_BASIC_ROLE))
        .principalFilter(Collections.singleton(PrincipalDTO.builder()
                                                   .identifier(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER)
                                                   .scopeLevel("account")
                                                   .type(USER_GROUP)
                                                   .build()))
        .build();
  }

  private RoleAssignmentFilterDTO getRoleAssignmentFilterDTOForOrganizationScope() {
    return RoleAssignmentFilterDTO.builder()
        .resourceGroupFilter(Collections.singleton(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER))
        .roleFilter(ImmutableSet.of(ORGANIZATION_BASIC_ROLE))
        .principalFilter(Collections.singleton(PrincipalDTO.builder()
                                                   .identifier(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER)
                                                   .scopeLevel("organization")
                                                   .type(USER_GROUP)
                                                   .build()))
        .build();
  }

  private RoleAssignmentFilterDTO getRoleAssignmentFilterDTOForProjectScope() {
    return RoleAssignmentFilterDTO.builder()
        .resourceGroupFilter(Collections.singleton(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER))
        .roleFilter(ImmutableSet.of(PROJECT_BASIC_ROLE))
        .principalFilter(Collections.singleton(PrincipalDTO.builder()
                                                   .identifier(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER)
                                                   .scopeLevel("project")
                                                   .type(USER_GROUP)
                                                   .build()))
        .build();
  }
}
