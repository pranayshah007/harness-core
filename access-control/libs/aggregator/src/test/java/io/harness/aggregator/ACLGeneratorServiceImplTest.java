/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.aggregator.consumers.ACLGeneratorServiceImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PL)
public class ACLGeneratorServiceImplTest extends AggregatorTestBase {
  private RoleService roleService;
  private UserGroupService userGroupService;
  private ResourceGroupService resourceGroupService;
  private ScopeService scopeService;
  private ACLRepository aclRepository;
  ACLGeneratorService aclGeneratorService;

  @Before
  public void setup() {
    roleService = mock(RoleService.class);
    userGroupService = mock(UserGroupService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    scopeService = mock(ScopeService.class);
    aclRepository = mock(ACLRepository.class);
    aclGeneratorService = new ACLGeneratorServiceImpl(
        roleService, userGroupService, resourceGroupService, scopeService, new HashMap<>(), aclRepository);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_LessThanBufferSize() {
    int count = 10;
    Set<String> principals = getRandomStrings(count);
    Set<String> permissions = getRandomStrings(count);
    Set<ResourceSelector> resourceSelectors = getResourceSelector(count);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenReturn(1000L);
    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);
    assertTrue(aclCount <= 50000);
    verify(aclRepository, times(1)).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_MoreThanBufferSize_CallsRepositoryMultipleTimes() {
    int count = 50;
    Set<String> principals = getRandomStrings(count);
    Set<String> permissions = getRandomStrings(count);
    Set<ResourceSelector> resourceSelectors = getResourceSelector(count);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenReturn(125000L);
    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);
    assertTrue(aclCount > 50000);
    verify(aclRepository, times(3)).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_MoreThanAllowed_ReturnsZeroCreated() {
    int count = 500;
    Set<String> principals = getRandomStrings(count);
    Set<ResourceSelector> resourceSelectors = getResourceSelector(count);
    count = 10;
    Set<String> permissions = getRandomStrings(count);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);
    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);
    assertEquals(0, aclCount);
    verify(aclRepository, never()).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_TillMaxAllowed_ReturnsCreated() {
    int count = 500;
    Set<String> principals = getRandomStrings(count);
    Set<ResourceSelector> resourceSelectors = getResourceSelector(count);
    count = 8;
    Set<String> permissions = getRandomStrings(count);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenReturn(50000L);
    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);
    assertEquals(2000000, aclCount);
    verify(aclRepository, times(40)).insertAllIgnoringDuplicates(any());
  }

  private Set<String> getRandomStrings(int count) {
    Set<String> randomStrings = new HashSet<>();
    for (int i = 0; i < count; i++) {
      randomStrings.add(randomAlphabetic(10));
    }
    return randomStrings;
  }

  private Set<ResourceSelector> getResourceSelector(int count) {
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    for (int i = 0; i < count; i++) {
      resourceSelectors.add(ResourceSelector.builder().selector(randomAlphabetic(10)).conditional(false).build());
    }
    return resourceSelectors;
  }

  private RoleAssignmentDBO getRoleAssignment(PrincipalType principalType) {
    return RoleAssignmentDBO.builder()
        .id(randomAlphabetic(10))
        .resourceGroupIdentifier(randomAlphabetic(10))
        .principalType(principalType)
        .principalIdentifier(randomAlphabetic(10))
        .roleIdentifier(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .build();
  }
}
