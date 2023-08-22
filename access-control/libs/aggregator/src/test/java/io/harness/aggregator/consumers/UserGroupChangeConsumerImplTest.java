/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.common.AccessControlTestUtils.getRandomString;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.principals.usergroups.UserGroupTestUtils.buildUserGroupDBO;
import static io.harness.accesscontrol.resources.resourcegroups.ResourceGroupTestUtils.buildResourceGroup;
import static io.harness.accesscontrol.roles.RoleTestUtils.buildRole;
import static io.harness.accesscontrol.scopes.TestScopeLevels.EXTRA_SCOPE;
import static io.harness.accesscontrol.scopes.TestScopeLevels.TEST_SCOPE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.UTKARSH;

import static java.util.concurrent.ThreadLocalRandom.current;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignmentTestUtils;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.core.ScopeServiceImpl;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.controllers.AggregatorBaseSyncController.AggregatorJobType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
public class UserGroupChangeConsumerImplTest extends AggregatorTestBase {
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  @Inject @Named("mongoTemplate") private MongoTemplate mongoTemplate;
  @Inject private RoleAssignmentRepository roleAssignmentRepository;
  @Inject private UserGroupRepository userGroupRepository;
  @Inject private UserGroupService userGroupService;
  private ScopeService scopeService;
  private UserGroupChangeConsumerImpl userGroupChangeConsumer;
  private RoleAssignmentChangeConsumerImpl roleAssignmentChangeConsumer;
  private String scopeIdentifier;
  private Role role;
  private ResourceGroup resourceGroup;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    ResourceGroupService resourceGroupService = mock(ResourceGroupService.class);
    RoleService roleService = mock(RoleService.class);
    RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler = mock(RoleAssignmentCRUDEventHandler.class);
    UserGroupCRUDEventHandler userGroupCRUDEventHandler = mock(UserGroupCRUDEventHandler.class);
    scopeService = new ScopeServiceImpl(null, Map.of(TEST_SCOPE.name(), TEST_SCOPE, EXTRA_SCOPE.name(), EXTRA_SCOPE));

    scopeIdentifier = "/" + TEST_SCOPE.name() + "/" + getRandomString(20);
    role = buildRole(scopeIdentifier);
    resourceGroup = buildResourceGroup(scopeIdentifier);
    when(roleService.get(any(), any(), any())).thenReturn(Optional.of(role));
    when(resourceGroupService.get(any(), any(), any())).thenReturn(Optional.of(resourceGroup));

    ACLGeneratorService changeConsumerService = new ACLGeneratorServiceImpl(roleService, userGroupService,
        resourceGroupService, scopeService, new HashMap<>(), aclRepository, false, inMemoryPermissionRepository);

    userGroupChangeConsumer =
        new UserGroupChangeConsumerImpl(aclRepository, roleAssignmentRepository, userGroupRepository,
            AggregatorJobType.PRIMARY.name(), changeConsumerService, scopeService, userGroupCRUDEventHandler);
    roleAssignmentChangeConsumer = new RoleAssignmentChangeConsumerImpl(
        aclRepository, roleAssignmentRepository, changeConsumerService, roleAssignmentCRUDEventHandler);

    aclRepository.cleanCollection();
  }

  @After
  public void clean() {
    mongoTemplate.remove(new Query(), UserGroupDBO.class);
    mongoTemplate.remove(new Query(), RoleAssignmentDBO.class);
    mongoTemplate.remove(new Query(), ACL.class);
  }

  private List<RoleAssignmentDBO> createACLsForRoleAssignments(
      int count, UserGroupDBO userGroupForRoleAssignment, String scopeIdentifier) {
    int remaining = count;
    List<RoleAssignmentDBO> roleAssignmentDBOS = new ArrayList<>();
    while (remaining > 0) {
      RoleAssignmentDBO roleAssignmentDBO = RoleAssignmentTestUtils.buildRoleAssignmentDBO(scopeIdentifier,
          role.getIdentifier(), resourceGroup.getIdentifier(),
          Principal.builder()
              .principalType(USER_GROUP)
              .principalIdentifier(userGroupForRoleAssignment.getIdentifier())
              .principalScopeLevel(TEST_SCOPE.toString())
              .build());
      mongoTemplate.save(roleAssignmentDBO);
      roleAssignmentChangeConsumer.consumeCreateEvent(roleAssignmentDBO.getId(), roleAssignmentDBO);
      roleAssignmentDBOS.add(roleAssignmentDBO);
      remaining--;
    }
    return roleAssignmentDBOS;
  }

  private void verifyACLs(List<RoleAssignmentDBO> roleAssignments, int distinctPermissions, int distinctPrincipals,
      int distinctResourceSelectors) {
    for (RoleAssignmentDBO dbo : roleAssignments) {
      assertEquals(
          distinctPermissions, aclRepository.getDistinctPermissionsInACLsForRoleAssignment(dbo.getId()).size());
      assertEquals(distinctPrincipals, aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(dbo.getId()).size());
      assertEquals(distinctResourceSelectors, aclRepository.getDistinctResourceSelectorsInACLs(dbo.getId()).size());
    }
  }

  private int getRandomNumber() {
    return current().nextInt(1, 4);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUserGroupUpdateFromNonEmptyToNonEmptyUsers() {
    UserGroupDBO newUserGroup = buildUserGroupDBO(scopeIdentifier, getRandomNumber());
    mongoTemplate.save(newUserGroup);

    List<RoleAssignmentDBO> roleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), newUserGroup, scopeIdentifier);
    verifyACLs(roleAssignments, role.getPermissions().size(), newUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(newUserGroup);
    updatedUserGroup.getUsers().add(getRandomString(10));
    mongoTemplate.save(updatedUserGroup);

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), updatedUserGroup);
    verifyACLs(roleAssignments, role.getPermissions().size(), updatedUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUserGroupUpdateFromEmptyToNonEmptyUsers() {
    UserGroupDBO newUserGroup = buildUserGroupDBO(scopeIdentifier, 0);
    mongoTemplate.save(newUserGroup);

    List<RoleAssignmentDBO> roleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), newUserGroup, scopeIdentifier);
    verifyACLs(roleAssignments, 0, 0, 0);

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(newUserGroup);
    updatedUserGroup.getUsers().add(getRandomString(10));
    updatedUserGroup.getUsers().add(getRandomString(10));
    mongoTemplate.save(updatedUserGroup);

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), updatedUserGroup);
    verifyACLs(roleAssignments, role.getPermissions().size(), updatedUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUserGroupUpdateFromNonEmptyToEmpty() {
    UserGroupDBO newUserGroup = buildUserGroupDBO(scopeIdentifier, getRandomNumber());
    mongoTemplate.save(newUserGroup);

    List<RoleAssignmentDBO> roleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), newUserGroup, scopeIdentifier);
    verifyACLs(roleAssignments, role.getPermissions().size(), newUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(newUserGroup);
    updatedUserGroup.getUsers().removeAll(updatedUserGroup.getUsers());
    mongoTemplate.save(updatedUserGroup);

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), updatedUserGroup);
    verifyACLs(roleAssignments, 0, 0, 0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUserGroupUpdateShouldRemoveRoleAssignmentsFromSameScopeIdentifier() {
    UserGroupDBO userGroup = buildUserGroupDBO(scopeIdentifier, getRandomNumber());
    mongoTemplate.save(userGroup);
    UserGroupDBO anotherUserGroup = UserGroupDBO.builder()
                                        .id(getRandomString(20))
                                        .name(userGroup.getName())
                                        .identifier(userGroup.getIdentifier())
                                        .scopeIdentifier(scopeIdentifier + "_suffix")
                                        .users(userGroup.getUsers())
                                        .build();
    mongoTemplate.save(anotherUserGroup);

    List<RoleAssignmentDBO> roleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), userGroup, userGroup.getScopeIdentifier());
    verifyACLs(roleAssignments, role.getPermissions().size(), userGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
    List<RoleAssignmentDBO> anotherRoleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), anotherUserGroup, anotherUserGroup.getScopeIdentifier());
    verifyACLs(anotherRoleAssignments, role.getPermissions().size(), anotherUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(userGroup);
    updatedUserGroup.getUsers().removeAll(updatedUserGroup.getUsers());
    mongoTemplate.save(updatedUserGroup);

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), updatedUserGroup);
    verifyACLs(roleAssignments, 0, 0, 0);
    verifyACLs(anotherRoleAssignments, role.getPermissions().size(), anotherUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUserGroupUpdateShouldRemoveRoleAssignmentsFromSameScopeAndInheritedInChildScope() {
    UserGroupDBO userGroup = buildUserGroupDBO(scopeIdentifier, getRandomNumber());
    mongoTemplate.save(userGroup);

    List<RoleAssignmentDBO> roleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), userGroup, userGroup.getScopeIdentifier());
    verifyACLs(roleAssignments, role.getPermissions().size(), userGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    // Role assignments in child scope
    scopeIdentifier = scopeIdentifier + "/" + EXTRA_SCOPE.name() + "/" + getRandomString(20);
    List<RoleAssignmentDBO> anotherChildScopeRoleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), userGroup, scopeIdentifier);
    verifyACLs(anotherChildScopeRoleAssignments, role.getPermissions().size(), userGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(userGroup);
    updatedUserGroup.getUsers().removeAll(updatedUserGroup.getUsers());
    mongoTemplate.save(updatedUserGroup);

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), updatedUserGroup);
    verifyACLs(roleAssignments, 0, 0, 0);
    verifyACLs(anotherChildScopeRoleAssignments, 0, 0, 0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUserGroupUpdateShouldRemoveRoleAssignmentsFromSameScopeAndOnlyItsInheritedInChildScope() {
    UserGroupDBO userGroup = buildUserGroupDBO(scopeIdentifier, getRandomNumber());
    mongoTemplate.save(userGroup);

    UserGroupDBO anotherUserGroup = UserGroupDBO.builder()
                                        .id(getRandomString(20))
                                        .name(userGroup.getName())
                                        .identifier(userGroup.getIdentifier())
                                        .scopeIdentifier(scopeIdentifier + "extra")
                                        .users(userGroup.getUsers())
                                        .build();
    mongoTemplate.save(anotherUserGroup);

    List<RoleAssignmentDBO> roleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), userGroup, userGroup.getScopeIdentifier());
    verifyACLs(roleAssignments, role.getPermissions().size(), userGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    // Inherited role assignments created under same scope
    String childScopeIdentifier = scopeIdentifier + "/" + EXTRA_SCOPE.name() + "/" + getRandomString(20);
    List<RoleAssignmentDBO> childScopeRoleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), userGroup, childScopeIdentifier);
    verifyACLs(childScopeRoleAssignments, role.getPermissions().size(), userGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    // Inherited role assignments created in scope
    childScopeIdentifier = scopeIdentifier + "extra/" + EXTRA_SCOPE.name() + "/" + getRandomString(20);
    List<RoleAssignmentDBO> anotherChildScopeRoleAssignments =
        createACLsForRoleAssignments(getRandomNumber(), anotherUserGroup, childScopeIdentifier);
    verifyACLs(anotherChildScopeRoleAssignments, role.getPermissions().size(), anotherUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(userGroup);
    updatedUserGroup.getUsers().removeAll(updatedUserGroup.getUsers());
    mongoTemplate.save(updatedUserGroup);

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), updatedUserGroup);
    verifyACLs(roleAssignments, 0, 0, 0);
    verifyACLs(childScopeRoleAssignments, 0, 0, 0);
    verifyACLs(anotherChildScopeRoleAssignments, role.getPermissions().size(), anotherUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
  }
}
