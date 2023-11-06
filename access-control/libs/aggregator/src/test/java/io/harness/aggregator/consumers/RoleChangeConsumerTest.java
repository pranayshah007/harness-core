/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.common.AccessControlTestUtils.getRandomString;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.resources.resourcegroups.ResourceGroupTestUtils.buildResourceGroup;
import static io.harness.accesscontrol.roles.RoleTestUtils.buildRoleRBO;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignmentTestUtils;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.persistence.RoleDBO;
import io.harness.accesscontrol.roles.persistence.RoleDBOMapper;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.models.RoleChangeEventData;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class RoleChangeConsumerTest extends AggregatorTestBase {
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  private RoleAssignmentRepository roleAssignmentRepository;
  private ACLGeneratorService aclGeneratorService;
  private RoleService roleService;
  private ScopeService scopeService;
  private String scopeIdentifier;
  private RoleDBO role;
  private ResourceGroup resourceGroup;
  private InMemoryPermissionRepository inMemoryPermissionRepository;
  private RoleChangeConsumer roleChangeConsumer;
  private ResourceGroupService resourceGroupService;
  private UserGroupService userGroupService;
  @Inject @Named("batchSizeForACLCreation") private int batchSizeForACLCreation;

  @Before
  public void setup() {
    roleService = mock(RoleService.class);
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    resourceGroupService = mock(ResourceGroupService.class);
    userGroupService = mock(UserGroupService.class);
    scopeService = mock(ScopeService.class);
    inMemoryPermissionRepository = mock(InMemoryPermissionRepository.class);
    when(inMemoryPermissionRepository.isPermissionCompatibleWithResourceSelector(any(), any())).thenReturn(true);
    aclRepository.cleanCollection();
    scopeIdentifier = getRandomString(20);
    role = buildRoleRBO(scopeIdentifier, ThreadLocalRandom.current().nextInt(1, 4));
    when(roleService.get(role.getIdentifier(), role.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(RoleDBOMapper.fromDBO(role)));
    resourceGroup = buildResourceGroup(scopeIdentifier);
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, inMemoryPermissionRepository, batchSizeForACLCreation);
    roleChangeConsumer = new RoleChangeConsumer(aclRepository, roleAssignmentRepository, aclGeneratorService);
    when(resourceGroupService.get(
             resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(resourceGroup));
  }

  private List<RoleAssignmentDBO> createACLsForRoleAssignments(int count, RoleDBO roleDBO) {
    int remaining = count;
    List<RoleAssignmentDBO> roleAssignmentDBOS = new ArrayList<>();
    while (remaining > 0) {
      RoleAssignmentDBO roleAssignmentDBO = RoleAssignmentTestUtils.buildRoleAssignmentDBO(scopeIdentifier,
          roleDBO.getIdentifier(), resourceGroup.getIdentifier(),
          Principal.builder().principalType(USER).principalIdentifier(getRandomString(20)).build());
      when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
               roleAssignmentDBO.getIdentifier(), roleAssignmentDBO.getScopeIdentifier()))
          .thenReturn(Optional.of(roleAssignmentDBO));
      when(roleService.get(roleDBO.getIdentifier(), roleDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
          .thenReturn(Optional.of(RoleDBOMapper.fromDBO(roleDBO)));
      aclGeneratorService.createACLsForRoleAssignment(roleAssignmentDBO);
      roleAssignmentDBOS.add(roleAssignmentDBO);
      remaining--;
    }
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier).is(roleDBO.getIdentifier());
    criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(roleDBO.getScopeIdentifier());
    when(roleAssignmentRepository.findAll(criteria, Pageable.ofSize(100000)))
        .thenReturn(PageTestUtils.getPage(roleAssignmentDBOS, roleAssignmentDBOS.size()));
    return roleAssignmentDBOS;
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testRoleUpdateFromNonEmptyToNonEmpty() {
    int numRoleAssignments = ThreadLocalRandom.current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, role);
    verifyACLs(roleAssignments, role.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());

    RoleDBO updatedRole = (RoleDBO) HObjectMapper.clone(role);

    String permissionAdded = getRandomString(10);
    String permissionRemoved = updatedRole.getPermissions().stream().findFirst().get();
    updatedRole.getPermissions().remove(permissionRemoved);
    Set<String> permissionsAdded = new HashSet<>(List.of(permissionAdded));
    Set<String> permissionsRemoved = new HashSet<>(List.of(permissionRemoved));
    updatedRole.getPermissions().add(permissionAdded);
    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(RoleDBOMapper.fromDBO(updatedRole))
                                                  .permissionsAdded(permissionsAdded)
                                                  .permissionsRemoved(permissionsRemoved)
                                                  .build();
    roleChangeConsumer.consumeUpdateEvent(null, roleChangeEventData);
    verifyACLs(roleAssignments, updatedRole.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testRoleUpdateFromEmptyToNonEmpty() {
    RoleDBO newRole = buildRoleRBO(scopeIdentifier, 0);

    int numRoleAssignments = ThreadLocalRandom.current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newRole);
    verifyACLs(roleAssignments, newRole.getPermissions().size(), 0, 0);

    RoleDBO updatedRole = (RoleDBO) HObjectMapper.clone(newRole);
    List<String> permissionsAdded = List.of(getRandomString(10), getRandomString(10));
    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(RoleDBOMapper.fromDBO(updatedRole))
                                                  .permissionsAdded(new HashSet<>(permissionsAdded))
                                                  .build();

    updatedRole.getPermissions().addAll(permissionsAdded);

    roleChangeConsumer.consumeUpdateEvent(null, roleChangeEventData);
    verifyACLs(roleAssignments, updatedRole.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testRoleUpdateFromNonEmptyToEmpty() {
    RoleDBO newRole = buildRoleRBO(scopeIdentifier, 4);
    int numRoleAssignments = ThreadLocalRandom.current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newRole);
    verifyACLs(roleAssignments, newRole.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());

    RoleDBO updatedRole = (RoleDBO) HObjectMapper.clone(newRole);
    Set<String> permissionsRemoved = new HashSet<>(updatedRole.getPermissions());
    updatedRole.getPermissions().removeAll(updatedRole.getPermissions());

    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(RoleDBOMapper.fromDBO(updatedRole))
                                                  .permissionsRemoved(permissionsRemoved)
                                                  .permissionsAdded(Collections.emptySet())
                                                  .build();

    roleChangeConsumer.consumeUpdateEvent(null, roleChangeEventData);
    verifyACLs(roleAssignments, updatedRole.getPermissions().size(), 0, 0);
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
