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
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UTKARSH;

import static junit.framework.TestCase.assertEquals;
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
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.controllers.AggregatorBaseSyncController.AggregatorJobType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class RoleChangeConsumerImplTest extends AggregatorTestBase {
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  private RoleAssignmentRepository roleAssignmentRepository;
  private RoleRepository roleRepository;
  private RoleService roleService;
  private ScopeService scopeService;
  private RoleChangeConsumerImpl roleChangeConsumer;
  private RoleAssignmentChangeConsumerImpl roleAssignmentChangeConsumer;
  private String scopeIdentifier;
  private RoleDBO role;
  private ResourceGroup resourceGroup;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    roleService = mock(RoleService.class);
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    roleRepository = mock(RoleRepository.class);
    ResourceGroupService resourceGroupService = mock(ResourceGroupService.class);
    RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler = mock(RoleAssignmentCRUDEventHandler.class);
    UserGroupService userGroupService = mock(UserGroupService.class);
    scopeService = mock(ScopeService.class);
    ACLGeneratorService changeConsumerService = new ACLGeneratorServiceImpl(roleService, userGroupService,
        resourceGroupService, scopeService, new HashMap<>(), aclRepository, false, inMemoryPermissionRepository);
    roleChangeConsumer = new RoleChangeConsumerImpl(aclRepository, roleAssignmentRepository, roleRepository,
        AggregatorJobType.PRIMARY.name(), changeConsumerService);
    aclRepository.cleanCollection();
    scopeIdentifier = getRandomString(20);
    role = buildRoleRBO(scopeIdentifier, ThreadLocalRandom.current().nextInt(1, 4));
    resourceGroup = buildResourceGroup(scopeIdentifier);
    roleAssignmentChangeConsumer = new RoleAssignmentChangeConsumerImpl(
        aclRepository, roleAssignmentRepository, changeConsumerService, roleAssignmentCRUDEventHandler);

    mockRoleServices(role);
    when(resourceGroupService.get(
             resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(resourceGroup));
  }

  private void mockRoleServices(RoleDBO roleForMocking) {
    when(roleRepository.findById(roleForMocking.getId())).thenReturn(Optional.of(roleForMocking));
    when(roleService.get(roleForMocking.getIdentifier(), roleForMocking.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(RoleDBOMapper.fromDBO(roleForMocking)));
  }

  private List<RoleAssignmentDBO> createACLsForRoleAssignments(int count, RoleDBO roleForAssignment) {
    int remaining = count;
    List<RoleAssignmentDBO> roleAssignmentDBOS = new ArrayList<>();
    while (remaining > 0) {
      RoleAssignmentDBO roleAssignmentDBO = RoleAssignmentTestUtils.buildRoleAssignmentDBO(scopeIdentifier,
          roleForAssignment.getIdentifier(), resourceGroup.getIdentifier(),
          Principal.builder().principalType(USER).principalIdentifier(getRandomString(20)).build());
      when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
               roleAssignmentDBO.getIdentifier(), roleAssignmentDBO.getScopeIdentifier()))
          .thenReturn(Optional.of(roleAssignmentDBO));
      roleAssignmentChangeConsumer.consumeCreateEvent(roleAssignmentDBO.getId(), roleAssignmentDBO);
      roleAssignmentDBOS.add(roleAssignmentDBO);
      remaining--;
    }
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier).is(roleForAssignment.getIdentifier());
    criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(roleForAssignment.getScopeIdentifier());
    when(roleAssignmentRepository.findAll(criteria, Pageable.unpaged()))
        .thenReturn(PageTestUtils.getPage(roleAssignmentDBOS, roleAssignmentDBOS.size()));
    return roleAssignmentDBOS;
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleUpdateFromNonEmptyToNonEmpty() {
    int numRoleAssignments = ThreadLocalRandom.current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, role);
    verifyACLs(roleAssignments, role.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());

    RoleDBO updatedRole = (RoleDBO) HObjectMapper.clone(role);
    updatedRole.getPermissions().add(getRandomString(10));
    mockRoleServices(updatedRole);
    roleChangeConsumer.consumeUpdateEvent(updatedRole.getId(), updatedRole);
    verifyACLs(roleAssignments, updatedRole.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleUpdateFromEmptyToNonEmpty() {
    RoleDBO newRole = buildRoleRBO(scopeIdentifier, 0);
    mockRoleServices(newRole);

    int numRoleAssignments = ThreadLocalRandom.current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newRole);
    verifyACLs(roleAssignments, newRole.getPermissions().size(), 0, 0);

    RoleDBO updatedRole = (RoleDBO) HObjectMapper.clone(newRole);
    updatedRole.getPermissions().add(getRandomString(10));
    updatedRole.getPermissions().add(getRandomString(10));
    mockRoleServices(updatedRole);

    roleChangeConsumer.consumeUpdateEvent(updatedRole.getId(), updatedRole);
    verifyACLs(roleAssignments, updatedRole.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRoleUpdateFromNonEmptyToEmpty() {
    RoleDBO newRole = buildRoleRBO(scopeIdentifier, 4);
    mockRoleServices(newRole);

    int numRoleAssignments = ThreadLocalRandom.current().nextInt(1, 10);
    List<RoleAssignmentDBO> roleAssignments = createACLsForRoleAssignments(numRoleAssignments, newRole);
    verifyACLs(roleAssignments, newRole.getPermissions().size(), 1, resourceGroup.getResourceSelectors().size());

    RoleDBO updatedRole = (RoleDBO) HObjectMapper.clone(newRole);
    updatedRole.getPermissions().removeAll(updatedRole.getPermissions());
    mockRoleServices(updatedRole);

    roleChangeConsumer.consumeUpdateEvent(updatedRole.getId(), updatedRole);
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
