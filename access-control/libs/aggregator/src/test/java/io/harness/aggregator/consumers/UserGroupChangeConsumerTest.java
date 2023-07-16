/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.common.AccessControlTestUtils.getRandomString;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.principals.usergroups.UserGroupTestUtils.buildUserGroupDBO;
import static io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBOMapper.fromDBO;
import static io.harness.accesscontrol.resources.resourcegroups.ResourceGroupTestUtils.buildResourceGroup;
import static io.harness.accesscontrol.roles.RoleTestUtils.buildRole;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static java.util.concurrent.ThreadLocalRandom.current;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBOMapper;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignmentTestUtils;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.models.UserGroupUpdateEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class UserGroupChangeConsumerTest extends AggregatorTestBase {
  private RoleAssignmentRepository roleAssignmentRepository;
  private ScopeService scopeService;
  private UserGroupService userGroupService;
  private RoleAssignmentChangeConsumerImpl roleAssignmentChangeConsumer;
  private String testScopeIdentifier;
  private String scopeIdentifier;
  private Role role;
  private ResourceGroup resourceGroup;

  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  private UserGroupChangeConsumer userGroupChangeConsumer;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    userGroupService = mock(UserGroupService.class);
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    RoleService roleService = mock(RoleService.class);
    ResourceGroupService resourceGroupService = mock(ResourceGroupService.class);
    RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler = mock(RoleAssignmentCRUDEventHandler.class);
    scopeService = mock(ScopeService.class);
    ACLGeneratorService aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService,
        resourceGroupService, scopeService, new HashMap<>(), aclRepository, false, inMemoryPermissionRepository);
    roleAssignmentChangeConsumer = new RoleAssignmentChangeConsumerImpl(
        aclRepository, roleAssignmentRepository, aclGeneratorService, roleAssignmentCRUDEventHandler);
    userGroupChangeConsumer =
        new UserGroupChangeConsumer(aclRepository, roleAssignmentRepository, aclGeneratorService, scopeService);
    aclRepository.cleanCollection();
    testScopeIdentifier = getRandomString(20);
    scopeIdentifier = "/ACCOUNT/" + testScopeIdentifier;
    role = buildRole(scopeIdentifier);
    resourceGroup = buildResourceGroup(scopeIdentifier);
    when(roleService.get(role.getIdentifier(), role.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(role));
    when(resourceGroupService.get(
             resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(resourceGroup));
  }

  private List<RoleAssignmentDBO> createACLsForRoleAssignments(int count, UserGroupDBO userGroupForRoleAssignment) {
    int remaining = count;
    List<RoleAssignmentDBO> roleAssignmentDBOS = new ArrayList<>();
    while (remaining > 0) {
      RoleAssignmentDBO roleAssignmentDBO = RoleAssignmentTestUtils.buildRoleAssignmentDBO(scopeIdentifier,
          role.getIdentifier(), resourceGroup.getIdentifier(),
          Principal.builder()
              .principalType(USER_GROUP)
              .principalIdentifier(userGroupForRoleAssignment.getIdentifier())
              .principalScopeLevel(TestScopeLevels.TEST_SCOPE.toString())
              .build());
      when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
               roleAssignmentDBO.getIdentifier(), roleAssignmentDBO.getScopeIdentifier()))
          .thenReturn(Optional.of(roleAssignmentDBO));
      roleAssignmentChangeConsumer.consumeCreateEvent(roleAssignmentDBO.getId(), roleAssignmentDBO);
      roleAssignmentDBOS.add(roleAssignmentDBO);
      remaining--;
    }
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.principalType).is(USER_GROUP);
    criteria.and(RoleAssignmentDBOKeys.principalIdentifier).is(userGroupForRoleAssignment.getIdentifier());
    criteria.and(RoleAssignmentDBOKeys.principalScopeLevel).is(TestScopeLevels.TEST_SCOPE.toString());
    criteria.and(RoleAssignmentDBOKeys.scopeIdentifier)
        .regex(Pattern.compile("^".concat(userGroupForRoleAssignment.getScopeIdentifier())));
    when(roleAssignmentRepository.findAll(criteria, Pageable.ofSize(10000)))
        .thenReturn(PageTestUtils.getPage(roleAssignmentDBOS, roleAssignmentDBOS.size()));
    return roleAssignmentDBOS;
  }

  private void mockUserGroupServices(UserGroupDBO userGroupForMocking) {
    when(userGroupService.get(userGroupForMocking.getIdentifier(), userGroupForMocking.getScopeIdentifier()))
        .thenReturn(Optional.of(UserGroupDBOMapper.fromDBO(userGroupForMocking)));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void userGroupUpdateFromNonEmptyToNonEmpty() {
    UserGroupDBO newUserGroup = buildUserGroupDBO(scopeIdentifier, current().nextInt(1, 4));
    mockUserGroupServices(newUserGroup);

    int numRoleAssignments = current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newUserGroup);
    verifyACLs(roleAssignments, role.getPermissions().size(), newUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(newUserGroup);
    UserGroup userGroup = fromDBO(updatedUserGroup);
    String newUserId = getRandomString(10);
    Set<String> usersAdded = new HashSet<>(List.of(newUserId));
    updatedUserGroup.getUsers().add(newUserId);
    UserGroupUpdateEventData userGroupUpdateEventData =
        UserGroupUpdateEventData.builder().updatedUserGroup(userGroup).usersAdded(usersAdded).build();

    mockUserGroupServices(updatedUserGroup);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
        .thenReturn(Scope.builder()
                        .level(TestScopeLevels.TEST_SCOPE)
                        .parentScope(null)
                        .instanceId(testScopeIdentifier)
                        .build());

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), userGroupUpdateEventData);
    verifyACLs(roleAssignments, role.getPermissions().size(), updatedUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void userGroupUpdateFromEmptyToNonEmpty() {
    UserGroupDBO newUserGroup = buildUserGroupDBO(scopeIdentifier, 0);
    mockUserGroupServices(newUserGroup);

    int numRoleAssignments = current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newUserGroup);
    verifyACLs(roleAssignments, 0, 0, 0);

    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(newUserGroup);
    Set<String> usersAdded = new HashSet<>(List.of(getRandomString(10), getRandomString(10)));

    updatedUserGroup.getUsers().addAll(usersAdded);
    UserGroup userGroup = fromDBO(updatedUserGroup);

    UserGroupUpdateEventData userGroupUpdateEventData =
        UserGroupUpdateEventData.builder().updatedUserGroup(userGroup).usersAdded(usersAdded).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
        .thenReturn(Scope.builder()
                        .level(TestScopeLevels.TEST_SCOPE)
                        .parentScope(null)
                        .instanceId(testScopeIdentifier)
                        .build());
    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), userGroupUpdateEventData);
    verifyACLs(roleAssignments, role.getPermissions().size(), updatedUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void userGroupUpdateFromNonEmptyToEmpty() {
    UserGroupDBO newUserGroup = buildUserGroupDBO(scopeIdentifier, current().nextInt(1, 4));
    mockUserGroupServices(newUserGroup);

    int numRoleAssignments = current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newUserGroup);
    verifyACLs(roleAssignments, role.getPermissions().size(), newUserGroup.getUsers().size(),
        resourceGroup.getResourceSelectors().size());
    Set<String> usersRemoved = newUserGroup.getUsers();
    UserGroupDBO updatedUserGroup = (UserGroupDBO) HObjectMapper.clone(newUserGroup);
    updatedUserGroup.getUsers().removeAll(usersRemoved);
    mockUserGroupServices(updatedUserGroup);

    UserGroup userGroup = fromDBO(updatedUserGroup);

    UserGroupUpdateEventData userGroupUpdateEventData =
        UserGroupUpdateEventData.builder().updatedUserGroup(userGroup).usersRemoved(usersRemoved).build();

    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
        .thenReturn(Scope.builder()
                        .level(TestScopeLevels.TEST_SCOPE)
                        .parentScope(null)
                        .instanceId(testScopeIdentifier)
                        .build());

    userGroupChangeConsumer.consumeUpdateEvent(updatedUserGroup.getId(), userGroupUpdateEventData);
    verifyACLs(roleAssignments, 0, 0, 0);
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
}
