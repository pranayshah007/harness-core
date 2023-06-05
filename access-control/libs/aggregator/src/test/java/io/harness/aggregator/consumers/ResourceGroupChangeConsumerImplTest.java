/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.scopes.core.Scope.PATH_DELIMITER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBOMapper;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AggregatorTestBase;
import io.harness.aggregator.controllers.AggregatorBaseSyncController.AggregatorJobType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
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

@OwnedBy(PL)
public class ResourceGroupChangeConsumerImplTest extends AggregatorTestBase {
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  private RoleAssignmentRepository roleAssignmentRepository;
  private ResourceGroupRepository resourceGroupRepository;
  private RoleService roleService;
  private UserGroupService userGroupService;
  private ResourceGroupService resourceGroupService;
  private ResourceGroupChangeConsumerImpl resourceGroupChangeConsumer;
  private int randomCount;
  private String id = randomAlphabetic(10);
  private ResourceGroupDBO resourceGroupDBO = getResourceGroupDBO(id);
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    ScopeService scopeService = mock(ScopeService.class);
    roleService = mock(RoleService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    userGroupService = mock(UserGroupService.class);
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    resourceGroupRepository = mock(ResourceGroupRepository.class);
    ACLGeneratorService changeConsumerService = new ACLGeneratorServiceImpl(roleService, userGroupService,
        resourceGroupService, scopeService, new HashMap<>(), aclRepository, false, inMemoryPermissionRepository);
    resourceGroupChangeConsumer = new ResourceGroupChangeConsumerImpl(aclRepository, roleAssignmentRepository,
        resourceGroupRepository, AggregatorJobType.PRIMARY.name(), changeConsumerService);
    aclRepository.cleanCollection();
    randomCount = ThreadLocalRandom.current().nextInt(1, 10);
    id = randomAlphabetic(10);
    resourceGroupDBO = getResourceGroupDBO(id);
  }

  private List<RoleAssignmentDBO> getRoleAssignments(ResourceGroupDBO resourceGroupDBO, PrincipalType principalType) {
    List<RoleAssignmentDBO> roleAssignmentDBOs = new ArrayList<>();
    for (int i = 0; i < randomCount; i++) {
      roleAssignmentDBOs.add(RoleAssignmentDBO.builder()
                                 .id(randomAlphabetic(10))
                                 .resourceGroupIdentifier(resourceGroupDBO.getIdentifier())
                                 .principalType(principalType)
                                 .principalIdentifier(randomAlphabetic(10))
                                 .roleIdentifier(randomAlphabetic(10))
                                 .scopeIdentifier(resourceGroupDBO.getScopeIdentifier())
                                 .identifier(randomAlphabetic(10))
                                 .build());
    }
    return roleAssignmentDBOs;
  }

  private Set<String> getRandomStrings() {
    Set<String> randomStrings = new HashSet<>();
    for (int i = 0; i < randomCount; i++) {
      randomStrings.add(randomAlphabetic(10));
    }
    return randomStrings;
  }

  private ResourceGroupDBO getResourceGroupDBO(String id) {
    return ResourceGroupDBO.builder()
        .id(id)
        .scopeIdentifier(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .resourceSelectors(Collections.singleton(PATH_DELIMITER.concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER)
                                                     .concat(PATH_DELIMITER)
                                                     .concat(ResourceGroup.ALL_RESOURCES_IDENTIFIER)))
        .build();
  }

  private ResourceGroupDBO getResourceGroupDBOWithNoResourceSelectors(String id) {
    return ResourceGroupDBO.builder()
        .id(id)
        .scopeIdentifier(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .build();
  }

  private ResourceGroup toResourceGroup(ResourceGroupDBO resourceGroupDBO) {
    return ResourceGroupDBOMapper.fromDBO(resourceGroupDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceGroupUpdate() {
    List<RoleAssignmentDBO> roleAssignmentDBOs = getRoleAssignments(resourceGroupDBO, PrincipalType.USER);

    preResourceGroupUpdate(resourceGroupDBO, roleAssignmentDBOs);

    resourceGroupChangeConsumer.consumeUpdateEvent(id, resourceGroupDBO);

    assertConsumeUpdateEventBaseInvocations(id, 0);
    assertRoleAssignmentDBOs(resourceGroupDBO, roleAssignmentDBOs, 0, randomCount, 1, 1);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceGroupUpdateUserGroup() {
    List<RoleAssignmentDBO> roleAssignmentDBOs = getRoleAssignments(resourceGroupDBO, PrincipalType.USER_GROUP);

    preResourceGroupUpdate(resourceGroupDBO, roleAssignmentDBOs);
    roleAssignmentDBOs.forEach(roleAssignmentDBO
        -> when(
            userGroupService.get(roleAssignmentDBO.getPrincipalIdentifier(), roleAssignmentDBO.getScopeIdentifier()))
               .thenReturn(Optional.of(UserGroup.builder()
                                           .identifier(roleAssignmentDBO.getPrincipalIdentifier())
                                           .users(getRandomStrings())
                                           .build())));

    resourceGroupChangeConsumer.consumeUpdateEvent(id, resourceGroupDBO);

    assertConsumeUpdateEventBaseInvocations(id, 0);
    assertRoleAssignmentDBOs(resourceGroupDBO, roleAssignmentDBOs, 0, randomCount, randomCount, 1);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceGroupUpdateNotFound() {
    when(resourceGroupRepository.findById(resourceGroupDBO.getId())).thenReturn(Optional.empty());

    resourceGroupChangeConsumer.consumeUpdateEvent(id, resourceGroupDBO);

    verify(resourceGroupRepository, times(1)).findById(id);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceGroupUpdateNoResourceSelectors() {
    resourceGroupDBO = getResourceGroupDBOWithNoResourceSelectors(id);
    List<RoleAssignmentDBO> roleAssignmentDBOs = getRoleAssignments(resourceGroupDBO, PrincipalType.USER);

    preResourceGroupUpdate(resourceGroupDBO, roleAssignmentDBOs);

    resourceGroupChangeConsumer.consumeUpdateEvent(id, resourceGroupDBO);

    assertConsumeUpdateEventBaseInvocations(id, -1);
    assertRoleAssignmentDBOs(resourceGroupDBO, roleAssignmentDBOs, -1, 0, 0, 0);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceGroupUpdateNoRoleAssignments() {
    List<RoleAssignmentDBO> roleAssignmentDBOs = new ArrayList<>();

    preResourceGroupUpdate(resourceGroupDBO, roleAssignmentDBOs);

    resourceGroupChangeConsumer.consumeUpdateEvent(id, resourceGroupDBO);

    assertConsumeUpdateEventBaseInvocations(id, 0);
    assertRoleAssignmentDBOs(resourceGroupDBO, roleAssignmentDBOs, 0, 0, 0, 0);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceGroupUpdateFullScopeSelected() {
    ResourceGroupDBO oldResourceGroupDBO = ResourceGroupDBO.builder()
                                               .id(id)
                                               .scopeIdentifier(resourceGroupDBO.getScopeIdentifier())
                                               .identifier(resourceGroupDBO.getIdentifier())
                                               .name(resourceGroupDBO.getName())
                                               .build();

    List<RoleAssignmentDBO> roleAssignmentDBOs = getRoleAssignments(oldResourceGroupDBO, PrincipalType.USER);
    preResourceGroupUpdate(oldResourceGroupDBO, roleAssignmentDBOs);
    resourceGroupChangeConsumer.consumeUpdateEvent(id, oldResourceGroupDBO);

    assertConsumeUpdateEventBaseInvocations(id, -1);
    assertRoleAssignmentDBOs(oldResourceGroupDBO, roleAssignmentDBOs, -1, 0, 0, 0);

    preResourceGroupUpdate(resourceGroupDBO, roleAssignmentDBOs);
    resourceGroupChangeConsumer.consumeUpdateEvent(id, resourceGroupDBO);
    assertConsumeUpdateEventBaseInvocations(id, 0);
    assertRoleAssignmentDBOs(resourceGroupDBO, roleAssignmentDBOs, 0, randomCount, 1, 1);
  }

  private void preResourceGroupUpdate(
      ResourceGroupDBO updatedResourceGroupDBO, List<RoleAssignmentDBO> roleAssignmentDBOs) {
    when(resourceGroupRepository.findById(updatedResourceGroupDBO.getId()))
        .thenReturn(Optional.of(updatedResourceGroupDBO));
    when(roleAssignmentRepository.findAll(any(), any()))
        .thenReturn(PageTestUtils.getPage(roleAssignmentDBOs, roleAssignmentDBOs.size()));

    roleAssignmentDBOs.forEach(roleAssignmentDBO -> {
      when(roleService.get(
               roleAssignmentDBO.getRoleIdentifier(), roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
          .thenReturn(Optional.of(Role.builder()
                                      .identifier(roleAssignmentDBO.getRoleIdentifier())
                                      .permissions(getRandomStrings())
                                      .build()));
      when(resourceGroupService.get(roleAssignmentDBO.getResourceGroupIdentifier(),
               roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
          .thenReturn(Optional.of(toResourceGroup(updatedResourceGroupDBO)));
    });
  }

  private void assertConsumeUpdateEventBaseInvocations(String id, int previousInvocationsCount) {
    verify(resourceGroupRepository, times(previousInvocationsCount + 1)).findById(id);
    verify(roleAssignmentRepository, times(previousInvocationsCount + 1)).findAll(any(), any());
  }

  private void assertRoleAssignmentDBOs(ResourceGroupDBO updatedResourceGroupDBO,
      List<RoleAssignmentDBO> roleAssignmentDBOs, int previousInvocationsCount, int distinctPermissions,
      int distinctPrincipals, int distinctResourceSelectors) {
    roleAssignmentDBOs.forEach(roleAssignmentDBO -> {
      verify(roleService, times(previousInvocationsCount + 1))
          .get(roleAssignmentDBO.getRoleIdentifier(), roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER);
      verify(resourceGroupService, times((previousInvocationsCount + 1) * randomCount))
          .get(updatedResourceGroupDBO.getIdentifier(), updatedResourceGroupDBO.getScopeIdentifier(),
              ManagedFilter.NO_FILTER);
      if (PrincipalType.USER_GROUP.equals(roleAssignmentDBO.getPrincipalType())) {
        verify(userGroupService, times(previousInvocationsCount + 1))
            .get(roleAssignmentDBO.getPrincipalIdentifier(), roleAssignmentDBO.getScopeIdentifier());
      }
      assertACLs(roleAssignmentDBO, distinctPermissions, distinctPrincipals, distinctResourceSelectors);
    });
  }

  private void assertACLs(RoleAssignmentDBO roleAssignmentDBO, int distinctPermissions, int distinctPrincipals,
      int distinctResourceSelectors) {
    assertEquals(distinctPermissions,
        aclRepository.getDistinctPermissionsInACLsForRoleAssignment(roleAssignmentDBO.getId()).size());
    assertEquals(distinctPrincipals,
        aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId()).size());
    assertEquals(
        distinctResourceSelectors, aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId()).size());
  }
}
