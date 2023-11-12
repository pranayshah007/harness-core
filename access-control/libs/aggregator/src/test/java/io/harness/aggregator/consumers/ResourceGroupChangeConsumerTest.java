/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.common.AccessControlTestUtils.getRandomString;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.roles.RoleTestUtils.buildRole;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
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
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.RoleAssignmentTestUtils;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.models.ResourceGroupChangeEventData;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class ResourceGroupChangeConsumerTest extends AggregatorTestBase {
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  private RoleAssignmentRepository roleAssignmentRepository;
  private ResourceGroupRepository resourceGroupRepository;
  private RoleService roleService;
  private UserGroupService userGroupService;
  private ResourceGroupService resourceGroupService;
  private ResourceGroupChangeConsumer resourceGroupChangeConsumer;
  private InMemoryPermissionRepository inMemoryPermissionRepository;
  @Inject @Named("batchSizeForACLCreation") private int batchSizeForACLCreation;
  ACLGeneratorService aclGeneratorService;
  private Role role;
  String scopeIdentifier = randomAlphabetic(10);

  @Before
  public void setup() {
    ScopeService scopeService = mock(ScopeService.class);
    roleService = mock(RoleService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    userGroupService = mock(UserGroupService.class);
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    resourceGroupRepository = mock(ResourceGroupRepository.class);
    inMemoryPermissionRepository = mock(InMemoryPermissionRepository.class);
    when(inMemoryPermissionRepository.isPermissionCompatibleWithResourceSelector(any(), any())).thenReturn(true);
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, inMemoryPermissionRepository, batchSizeForACLCreation);
    resourceGroupChangeConsumer =
        new ResourceGroupChangeConsumer(aclRepository, roleAssignmentRepository, aclGeneratorService);
    aclRepository.cleanCollection();
    role = buildRole(scopeIdentifier);
  }

  private void setUpACLGenerationForRoleAssignments(
      ResourceGroup resourceGroup, List<RoleAssignmentDBO> roleAssignmentDBOS) {
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.resourceGroupIdentifier).is(resourceGroup.getIdentifier());
    criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(resourceGroup.getScopeIdentifier());
    when(roleAssignmentRepository.findAll(criteria, Pageable.ofSize(100000)))
        .thenReturn(PageTestUtils.getPage(roleAssignmentDBOS, roleAssignmentDBOS.size()));
    roleAssignmentDBOS.forEach(
        roleAssignmentDBO -> setUpACLGenerationForRoleAssignment(resourceGroup, roleAssignmentDBO));
  }

  private void setUpACLGenerationForRoleAssignment(ResourceGroup resourceGroup, RoleAssignmentDBO roleAssignmentDBO) {
    when(roleService.get(
             roleAssignmentDBO.getRoleIdentifier(), roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(role));
    when(resourceGroupService.get(roleAssignmentDBO.getResourceGroupIdentifier(),
             roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(resourceGroup));
  }

  private List<RoleAssignmentDBO> createACLsForRoleAssignments(int count, ResourceGroup resourceGroup) {
    int remaining = count;
    List<RoleAssignmentDBO> roleAssignmentDBOS = new ArrayList<>();
    while (remaining > 0) {
      RoleAssignmentDBO roleAssignmentDBO = RoleAssignmentTestUtils.buildRoleAssignmentDBO(scopeIdentifier,
          resourceGroup.getIdentifier(), resourceGroup.getIdentifier(),
          Principal.builder().principalType(USER).principalIdentifier(getRandomString(20)).build());
      setUpACLGenerationForRoleAssignment(resourceGroup, roleAssignmentDBO);
      aclGeneratorService.createACLsForRoleAssignment(roleAssignmentDBO);
      roleAssignmentDBOS.add(roleAssignmentDBO);
      remaining--;
    }

    return roleAssignmentDBOS;
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void resourceGroupUpdate() {
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = getResourceGroup(identifier, scopeIdentifier);
    List<RoleAssignmentDBO> roleAssignmentDBOs = createACLsForRoleAssignments(5, resourceGroup);
    verifyACLs(resourceGroup, roleAssignmentDBOs, resourceGroup.getResourceSelectorsV2().size(), 1,
        role.getPermissions().size());
    ResourceGroup updatedResourceGroup = getResourceGroup(identifier, scopeIdentifier);
    setUpACLGenerationForRoleAssignments(updatedResourceGroup, roleAssignmentDBOs);
    Set<ResourceSelector> resourceSelectorsAdded =
        ResourceGroup.getDiffOfResourceSelectors(updatedResourceGroup, resourceGroup);
    Set<ResourceSelector> resourceSelectorsDeleted =
        ResourceGroup.getDiffOfResourceSelectors(resourceGroup, updatedResourceGroup);

    ResourceGroupChangeEventData resourceGroupChangeEventData = ResourceGroupChangeEventData.builder()
                                                                    .updatedResourceGroup(updatedResourceGroup)
                                                                    .addedResourceSelectors(resourceSelectorsAdded)
                                                                    .removedResourceSelectors(resourceSelectorsDeleted)
                                                                    .build();
    resourceGroupChangeConsumer.consumeUpdateEvent(null, resourceGroupChangeEventData);
    verifyACLs(updatedResourceGroup, roleAssignmentDBOs, role.getPermissions().size(), 1, role.getPermissions().size());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void updateRemovedAllResourceSelectors_ReturnsZeroACLs() {
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = getResourceGroup(identifier, scopeIdentifier);
    List<RoleAssignmentDBO> roleAssignmentDBOs = createACLsForRoleAssignments(5, resourceGroup);
    verifyACLs(resourceGroup, roleAssignmentDBOs, resourceGroup.getResourceSelectorsV2().size(), 1,
        role.getPermissions().size());
    ResourceGroup updatedResourceGroup = getResourceGroupWithNoResourcesSelected(identifier, scopeIdentifier);
    setUpACLGenerationForRoleAssignments(updatedResourceGroup, roleAssignmentDBOs);
    Set<ResourceSelector> resourceSelectorsAdded =
        ResourceGroup.getDiffOfResourceSelectors(updatedResourceGroup, resourceGroup);
    Set<ResourceSelector> resourceSelectorsDeleted =
        ResourceGroup.getDiffOfResourceSelectors(resourceGroup, updatedResourceGroup);

    ResourceGroupChangeEventData resourceGroupChangeEventData = ResourceGroupChangeEventData.builder()
                                                                    .updatedResourceGroup(updatedResourceGroup)
                                                                    .addedResourceSelectors(resourceSelectorsAdded)
                                                                    .removedResourceSelectors(resourceSelectorsDeleted)
                                                                    .build();
    resourceGroupChangeConsumer.consumeUpdateEvent(null, resourceGroupChangeEventData);
    verifyACLs(updatedResourceGroup, roleAssignmentDBOs, 0, 0, 0);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void updateResourceGroupFromEmptyToNonEmpt() {
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = getResourceGroupWithNoResourcesSelected(identifier, scopeIdentifier);
    List<RoleAssignmentDBO> roleAssignmentDBOs = createACLsForRoleAssignments(5, resourceGroup);
    verifyACLs(resourceGroup, roleAssignmentDBOs, 0, 0, 0);
    ResourceGroup updatedResourceGroup = getResourceGroup(identifier, scopeIdentifier);
    setUpACLGenerationForRoleAssignments(updatedResourceGroup, roleAssignmentDBOs);
    Set<ResourceSelector> resourceSelectorsAdded =
        ResourceGroup.getDiffOfResourceSelectors(updatedResourceGroup, resourceGroup);
    Set<ResourceSelector> resourceSelectorsDeleted =
        ResourceGroup.getDiffOfResourceSelectors(resourceGroup, updatedResourceGroup);

    ResourceGroupChangeEventData resourceGroupChangeEventData = ResourceGroupChangeEventData.builder()
                                                                    .updatedResourceGroup(updatedResourceGroup)
                                                                    .addedResourceSelectors(resourceSelectorsAdded)
                                                                    .removedResourceSelectors(resourceSelectorsDeleted)
                                                                    .build();
    resourceGroupChangeConsumer.consumeUpdateEvent(null, resourceGroupChangeEventData);
    verifyACLs(updatedResourceGroup, roleAssignmentDBOs, role.getPermissions().size(), 1, role.getPermissions().size());
  }

  private void verifyACLs(ResourceGroup resourceGroup, RoleAssignmentDBO roleAssignmentDBO, int distinctPermissions,
      int distinctPrincipals, int distinctResourceSelectors) {
    assertEquals(distinctPermissions,
        aclRepository.getDistinctPermissionsInACLsForRoleAssignment(roleAssignmentDBO.getId()).size());
    assertEquals(distinctPrincipals,
        aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId()).size());
    assertEquals(
        distinctResourceSelectors, aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId()).size());
    Set<ResourceSelector> resourceSelectors =
        aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId());
    assertEquals(resourceGroup.getResourceSelectorsV2(), resourceSelectors);
  }

  private void verifyACLs(ResourceGroup resourceGroup, List<RoleAssignmentDBO> roleAssignments, int distinctPermissions,
      int distinctPrincipals, int distinctResourceSelectors) {
    for (RoleAssignmentDBO dbo : roleAssignments) {
      verifyACLs(resourceGroup, dbo, distinctPermissions, distinctPrincipals, distinctResourceSelectors);
    }
  }

  private ResourceGroup getResourceGroup(String identifier, String scopeIdentifier) {
    Set<ResourceSelector> resourceSelectors =
        new HashSet<>(Arrays.asList(getResourceSelector(), getResourceSelector(), getResourceSelector()));
    return ResourceGroup.builder()
        .resourceSelectorsV2(resourceSelectors)
        .identifier(identifier)
        .scopeIdentifier(scopeIdentifier)
        .build();
  }

  private ResourceGroup getResourceGroupWithNoResourcesSelected(String identifier, String scopeIdentifier) {
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    return ResourceGroup.builder()
        .resourceSelectorsV2(resourceSelectors)
        .identifier(identifier)
        .scopeIdentifier(scopeIdentifier)
        .build();
  }

  private ResourceSelector getResourceSelector() {
    return ResourceSelector.builder().selector(randomAlphabetic(10)).conditional(false).condition(null).build();
  }
}
