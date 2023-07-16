/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.common.AccessControlTestUtils.getRandomString;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.usergroups.UserGroupTestUtils;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupTestUtils;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.roleassignments.RoleAssignmentTestUtils;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.RoleTestUtils;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class RoleAssignmentChangeConsumerImplTest extends AggregatorTestBase {
  private RoleService roleService;
  private ResourceGroupService resourceGroupService;
  private UserGroupService userGroupService;
  private ScopeService scopeService;
  private RoleAssignmentRepository roleAssignmentRepository;
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  private RoleAssignmentChangeConsumerImpl roleAssignmentChangeConsumer;
  private RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler;

  private String scopeIdentifier;
  private Role role;
  private ResourceGroup resourceGroup;
  private UserGroup userGroup;
  private String user;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    roleAssignmentCRUDEventHandler = mock(RoleAssignmentCRUDEventHandler.class);
    roleService = mock(RoleService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    userGroupService = mock(UserGroupService.class);
    scopeService = mock(ScopeService.class);
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    ACLGeneratorService changeConsumerService = new ACLGeneratorServiceImpl(roleService, userGroupService,
        resourceGroupService, scopeService, new HashMap<>(), aclRepository, false, inMemoryPermissionRepository);
    roleAssignmentChangeConsumer = new RoleAssignmentChangeConsumerImpl(
        aclRepository, roleAssignmentRepository, changeConsumerService, roleAssignmentCRUDEventHandler);
    scopeIdentifier =
        Scope.builder().level(TestScopeLevels.TEST_SCOPE).instanceId(getRandomString(10)).build().toString();
    role = RoleTestUtils.buildRole(scopeIdentifier);
    resourceGroup = ResourceGroupTestUtils.buildResourceGroup(scopeIdentifier);
    userGroup = UserGroupTestUtils.buildUserGroup(scopeIdentifier);
    user = getRandomString(10);
    when(roleService.get(role.getIdentifier(), role.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(role));
    when(resourceGroupService.get(
             resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(resourceGroup));
    when(userGroupService.get(userGroup.getIdentifier(), userGroup.getScopeIdentifier()))
        .thenReturn(Optional.of(userGroup));
  }

  private RoleAssignmentDBO createACLsForRoleAssignment(Principal principal) {
    RoleAssignmentDBO roleAssignmentDBO = RoleAssignmentTestUtils.buildRoleAssignmentDBO(
        scopeIdentifier, role.getIdentifier(), resourceGroup.getIdentifier(), principal);
    when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
             roleAssignmentDBO.getIdentifier(), roleAssignmentDBO.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignmentDBO));
    roleAssignmentChangeConsumer.consumeCreateEvent(roleAssignmentDBO.getId(), roleAssignmentDBO);
    return roleAssignmentDBO;
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleAssignmentCreation_withUserGroup() {
    RoleAssignmentDBO roleAssignmentDBO = createACLsForRoleAssignment(
        Principal.builder().principalIdentifier(userGroup.getIdentifier()).principalType(USER_GROUP).build());
    verifyACLs(roleAssignmentDBO);
    verifyInvocations(roleAssignmentDBO);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleAssignmentCreation_withUser() {
    RoleAssignmentDBO roleAssignmentDBO =
        createACLsForRoleAssignment(Principal.builder().principalIdentifier(user).principalType(USER).build());
    verifyACLs(roleAssignmentDBO);
    verifyInvocations(roleAssignmentDBO);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleAssignmentCreation_RoleNotFound() {
    createACLsForRoleAssignment(
        Principal.builder().principalIdentifier(userGroup.getIdentifier()).principalType(USER_GROUP).build());
    when(roleService.get(role.getIdentifier(), role.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.empty());
    RoleAssignmentDBO roleAssignmentDBO =
        createACLsForRoleAssignment(Principal.builder().principalIdentifier(user).principalType(USER).build());
    verifyNoACLs(roleAssignmentDBO);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleAssignmentCreation_ResourceGroupNotFound() {
    createACLsForRoleAssignment(
        Principal.builder().principalIdentifier(userGroup.getIdentifier()).principalType(USER_GROUP).build());
    when(resourceGroupService.get(
             resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.empty());
    RoleAssignmentDBO roleAssignmentDBO =
        createACLsForRoleAssignment(Principal.builder().principalIdentifier(user).principalType(USER).build());
    verifyNoACLs(roleAssignmentDBO);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleAssignmentCreation_UserGroupNotFound() {
    createACLsForRoleAssignment(Principal.builder().principalIdentifier(user).principalType(USER).build());
    when(userGroupService.get(userGroup.getIdentifier(), userGroup.getScopeIdentifier())).thenReturn(Optional.empty());
    RoleAssignmentDBO roleAssignmentDBO = createACLsForRoleAssignment(
        Principal.builder().principalIdentifier(userGroup.getIdentifier()).principalType(USER_GROUP).build());
    verifyNoACLs(roleAssignmentDBO);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleAssignmentDeletion() {
    RoleAssignmentDBO roleAssignmentDBO =
        createACLsForRoleAssignment(Principal.builder().principalIdentifier(user).principalType(USER).build());
    verifyACLs(roleAssignmentDBO);
    roleAssignmentChangeConsumer.consumeDeleteEvent(roleAssignmentDBO.getId());
    verifyNoACLs(roleAssignmentDBO);
    verify(roleAssignmentCRUDEventHandler, times(1)).handleRoleAssignmentDelete(roleAssignmentDBO.getId());
  }

  private void verifyACLs(RoleAssignmentDBO assignment) {
    assertThat(new HashSet<>(aclRepository.getDistinctPermissionsInACLsForRoleAssignment(assignment.getId())))
        .isEqualTo(role.getPermissions());
    if (assignment.getPrincipalType().equals(USER)) {
      assertThat(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(assignment.getId()))
          .isEqualTo(Collections.singletonList(user));
    } else {
      assertThat(new HashSet<>(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(assignment.getId())))
          .isEqualTo(userGroup.getUsers());
    }
    assertThat(new HashSet<>(aclRepository.getDistinctResourceSelectorsInACLs(assignment.getId())))
        .isEqualTo(resourceGroup.getResourceSelectors()
                       .stream()
                       .map(selector -> ResourceSelector.builder().selector(selector).build())
                       .collect(Collectors.toSet()));
  }

  private void verifyNoACLs(RoleAssignmentDBO assignment) {
    assertThat(aclRepository.getDistinctPermissionsInACLsForRoleAssignment(assignment.getId())).isNullOrEmpty();
    assertThat(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(assignment.getId())).isNullOrEmpty();
    assertThat(aclRepository.getDistinctResourceSelectorsInACLs(assignment.getId())).isNullOrEmpty();
  }

  private void verifyInvocations(RoleAssignmentDBO roleAssignmentDBO) {
    verify(roleAssignmentRepository, times(1))
        .findByIdentifierAndScopeIdentifier(roleAssignmentDBO.getIdentifier(), roleAssignmentDBO.getScopeIdentifier());
    verify(roleAssignmentCRUDEventHandler, times(1)).handleRoleAssignmentCreate(roleAssignmentDBO);
    verify(roleService, times(2)).get(role.getIdentifier(), role.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    verify(resourceGroupService, times(2))
        .get(resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    if (roleAssignmentDBO.getPrincipalType().equals(USER_GROUP)) {
      verify(userGroupService, times(2)).get(userGroup.getIdentifier(), userGroup.getScopeIdentifier());
    } else {
      verify(userGroupService, times(0)).get(userGroup.getIdentifier(), userGroup.getScopeIdentifier());
    }
  }
}
