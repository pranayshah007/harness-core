/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_BASIC_ROLE;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_BASIC_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.UserGroupMapper.toEntity;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.NGConstants;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(PL)
public class DefaultUserGroupServiceTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock AccessControlAdminClient accessControlAdminClient;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock UserMembershipRepository userMembershipRepository;
  @Spy @InjectMocks DefaultUserGroupServiceImpl defaultUserGroupService;

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String ORG_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_IDENTIFIER = randomAlphabetic(10);

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testGetDefaultUserGroup() {
    Optional<UserGroup> userGroupOptional = Optional.of(UserGroup.builder().build());
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, null, null);
    when(userGroupService.get(ACCOUNT_IDENTIFIER, null, null, getUserGroupIdentifier(scope)))
        .thenReturn(userGroupOptional);
    Optional<UserGroup> result = defaultUserGroupService.get(scope);
    assertThat(userGroupOptional).isEqualTo(result);
    verify(userGroupService, times(1)).get(ACCOUNT_IDENTIFIER, null, null, getUserGroupIdentifier(scope));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateDefaultUserGroup_AccountScope() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, null, null);
    createAndMockUserGroupDTO(scope, getUserGroupIdentifier(scope));
    final UserGroup userGroup = defaultUserGroupService.create(scope, Collections.emptyList());
    assertThat(userGroup.getIdentifier()).isEqualTo(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true,
            NGConstants.ACCOUNT_BASIC_ROLE, NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false,
            NGConstants.ACCOUNT_VIEWER_ROLE, NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateDefaultUserGroup_OrgScope_withFFOn() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null);
    createAndMockUserGroupDTO(scope, getUserGroupIdentifier(scope));
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(true);
    final UserGroup userGroup = defaultUserGroupService.create(scope, Collections.emptyList());
    assertThat(userGroup.getIdentifier()).isEqualTo(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true,
            ORGANIZATION_BASIC_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false,
            ORGANIZATION_VIEWER_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateDefaultUserGroup_OrgScope_withFFOF() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null);
    createAndMockUserGroupDTO(scope, getUserGroupIdentifier(scope));
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(false);
    final UserGroup userGroup = defaultUserGroupService.create(scope, Collections.emptyList());
    assertThat(userGroup.getIdentifier()).isEqualTo(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true,
            ORGANIZATION_BASIC_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, true, false,
            ORGANIZATION_VIEWER_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateDefaultUserGroup_ProjectScope_withFFOF() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    createAndMockUserGroupDTO(scope, getUserGroupIdentifier(scope));
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(false);
    final UserGroup userGroup = defaultUserGroupService.create(scope, Collections.emptyList());
    assertThat(userGroup.getIdentifier()).isEqualTo(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true, PROJECT_BASIC_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, true, false, PROJECT_VIEWER_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateDefaultUserGroup_ProjectScope_withFFON() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    createAndMockUserGroupDTO(scope, getUserGroupIdentifier(scope));
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(true);
    final UserGroup userGroup = defaultUserGroupService.create(scope, Collections.emptyList());
    assertThat(userGroup.getIdentifier()).isEqualTo(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true, PROJECT_BASIC_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false, PROJECT_VIEWER_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void createOrUpdateUserGroupAtScope_checkRoleAssignmentAtProjectScope_withFFON() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(true);

    Optional<UserGroup> userGroupOptional =
        Optional.of(UserGroup.builder().users(Arrays.asList("user1", "user2", "user3")).build());
    Optional<List<RoleAssignmentResponseDTO>> roleAssignmentResponseDTOS = Optional.of(emptyList());
    when(userGroupService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, getUserGroupIdentifier(scope)))
        .thenReturn(userGroupOptional);
    doReturn(roleAssignmentResponseDTOS).when(defaultUserGroupService).getRoleAssignmentsAtScope(scope);

    defaultUserGroupService.createOrUpdateUserGroupAtScope(scope);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true, PROJECT_BASIC_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false, PROJECT_VIEWER_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void createOrUpdateUserGroupAtScope_checkRoleAssignmentAtProjectScope_withFFOF() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(false);

    Optional<UserGroup> userGroupOptional =
        Optional.of(UserGroup.builder().users(Arrays.asList("user1", "user2", "user3")).build());
    Optional<List<RoleAssignmentResponseDTO>> roleAssignmentResponseDTOS = Optional.of(emptyList());
    when(userGroupService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, getUserGroupIdentifier(scope)))
        .thenReturn(userGroupOptional);
    doReturn(roleAssignmentResponseDTOS).when(defaultUserGroupService).getRoleAssignmentsAtScope(scope);

    defaultUserGroupService.createOrUpdateUserGroupAtScope(scope);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true, PROJECT_BASIC_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false, PROJECT_VIEWER_ROLE,
            NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void createOrUpdateUserGroupAtScope_checkRoleAssignmentAtOrgScope_withFFON() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null);
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(true);

    Optional<UserGroup> userGroupOptional =
        Optional.of(UserGroup.builder().users(Arrays.asList("user1", "user2", "user3")).build());
    Optional<List<RoleAssignmentResponseDTO>> roleAssignmentResponseDTOS = Optional.of(emptyList());
    when(userGroupService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, getUserGroupIdentifier(scope)))
        .thenReturn(userGroupOptional);
    doReturn(roleAssignmentResponseDTOS).when(defaultUserGroupService).getRoleAssignmentsAtScope(scope);

    defaultUserGroupService.createOrUpdateUserGroupAtScope(scope);
    verify(defaultUserGroupService, times(1))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true,
            ORGANIZATION_BASIC_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false,
            ORGANIZATION_VIEWER_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void createOrUpdateUserGroupAtScope_checkRoleAssignmentAtOrgScope_withFFOFF() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null);
    when(ngFeatureFlagHelperService.isEnabled(
             scope.getAccountIdentifier(), FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS))
        .thenReturn(false);

    Optional<UserGroup> userGroupOptional =
        Optional.of(UserGroup.builder().users(Arrays.asList("user1", "user2", "user3")).build());
    Optional<List<RoleAssignmentResponseDTO>> roleAssignmentResponseDTOS = Optional.of(emptyList());
    when(userGroupService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, getUserGroupIdentifier(scope)))
        .thenReturn(userGroupOptional);
    doReturn(roleAssignmentResponseDTOS).when(defaultUserGroupService).getRoleAssignmentsAtScope(scope);

    defaultUserGroupService.createOrUpdateUserGroupAtScope(scope);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, true, true,
            ORGANIZATION_BASIC_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    verify(defaultUserGroupService, times(0))
        .createRoleAssignment(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, scope, false, false,
            ORGANIZATION_VIEWER_ROLE, NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  }

  private void createAndMockUserGroupDTO(Scope scope, String userGroupIdentifier) {
    UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                                    .accountIdentifier(scope.getAccountIdentifier())
                                    .orgIdentifier(scope.getOrgIdentifier())
                                    .projectIdentifier(scope.getProjectIdentifier())
                                    .name(defaultUserGroupService.getUserGroupName(scope))
                                    .description(defaultUserGroupService.getUserGroupDescription(scope))
                                    .identifier(userGroupIdentifier)
                                    .isSsoLinked(false)
                                    .externallyManaged(false)
                                    .users(emptyList())
                                    .harnessManaged(true)
                                    .build();
    UserGroup userGroupEntity = toEntity(userGroupDTO);
    when(userGroupService.createDefaultUserGroup(userGroupDTO)).thenReturn(userGroupEntity);
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
}
