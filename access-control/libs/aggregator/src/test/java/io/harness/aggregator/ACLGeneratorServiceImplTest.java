/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static java.util.Set.of;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.aggregator.consumers.ACLGeneratorServiceImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PL)
public class ACLGeneratorServiceImplTest extends AggregatorTestBase {
  public static final String CORE_USERGROUP_MANAGE_PERMISSION = "core_usergroup_manage";
  public static final String USERGROUP_RESOURCE_NAME = "usergroup";
  public static final String USERGROUP_RESOURCE_IDENTIFIER = "USERGROUP";

  public static final String CORE_RESOURCEGROUP_MANAGE_PERMISSION = "core_resourcegroup_manage";
  public static final String RESOURCEGROUP_RESOURCE_NAME = "resourcegroup";
  public static final String RESOURCEGROUP_RESOURCE_IDENTIFIER = "RESOURCEGROUP";

  private ACLRepository aclRepository;
  ACLGeneratorService aclGeneratorService;
  private ScopeService scopeService;
  private RoleService roleService;
  private UserGroupService userGroupService;
  private ResourceGroupService resourceGroupService;
  private InMemoryPermissionRepository inMemoryPermissionRepository;
  @Inject @Named("mongoTemplate") private MongoTemplate mongoTemplate;

  @Before
  public void setup() {
    roleService = mock(RoleService.class);
    userGroupService = mock(UserGroupService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    scopeService = mock(ScopeService.class);
    aclRepository = mock(ACLRepository.class);
    aclRepository = mock(ACLRepository.class);
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_USERGROUP_MANAGE_PERMISSION).build());
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION).build());
    mongoTemplate.save(ResourceTypeDBO.builder()
                           .identifier(USERGROUP_RESOURCE_IDENTIFIER)
                           .permissionKey(USERGROUP_RESOURCE_NAME)
                           .build());
    mongoTemplate.save(ResourceTypeDBO.builder()
                           .identifier(RESOURCEGROUP_RESOURCE_IDENTIFIER)
                           .permissionKey(RESOURCEGROUP_RESOURCE_NAME)
                           .build());
    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, false, inMemoryPermissionRepository);
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

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void createACLsAndDoNotMarkRedundantACLDisabled() {
    Set<String> principals = of(getRandomString());

    String allResourceSelector = "/*/*";
    String userGroupSelector = "/ACCOUNT/account-id$/USERGROUP/*";
    Set<ResourceSelector> resourceSelectors = of(ResourceSelector.builder().selector(allResourceSelector).build(),
        ResourceSelector.builder().selector(userGroupSelector).build());

    Set<String> permissions = of(CORE_USERGROUP_MANAGE_PERMISSION, CORE_RESOURCEGROUP_MANAGE_PERMISSION);

    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    List<List<ACL>> listOfParameters = new ArrayList<>();
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      listOfParameters.add(new ArrayList<>((Collection<ACL>) args[0]));
      return (long) listOfParameters.get(0).size();
    });
    aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);

    assertThat(listOfParameters.size()).isEqualTo(1);
    List<ACL> acls = listOfParameters.get(0);
    assertThat(acls.size()).isEqualTo(4);

    long enabledAcls = acls.stream().filter(ACL::isEnabled).count();
    assertThat(enabledAcls).isEqualTo(4);
    long disabledAcls = acls.stream().filter(acl -> !acl.isEnabled()).count();
    assertThat(disabledAcls).isEqualTo(0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void createACLsAndMarkRedundantACLDisabled() {
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, true, inMemoryPermissionRepository);

    Set<String> principals = of(getRandomString());

    String allResourceSelector = "/*/*";
    String userGroupSelector = "/ACCOUNT/account-id$/USERGROUP/*";
    Set<ResourceSelector> resourceSelectors = of(ResourceSelector.builder().selector(allResourceSelector).build(),
        ResourceSelector.builder().selector(userGroupSelector).build());

    Set<String> permissions = of(CORE_USERGROUP_MANAGE_PERMISSION, CORE_RESOURCEGROUP_MANAGE_PERMISSION);

    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    List<List<ACL>> listOfParameters = new ArrayList<>();
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      listOfParameters.add(new ArrayList<>((Collection<ACL>) args[0]));
      return (long) listOfParameters.get(0).size();
    });
    aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);

    assertThat(listOfParameters.size()).isEqualTo(1);
    List<ACL> acls = listOfParameters.get(0);
    assertThat(acls.size()).isEqualTo(4);

    long enabledAclCount = acls.stream().filter(ACL::isEnabled).count();
    assertThat(enabledAclCount).isEqualTo(3);

    List<ACL> disabledACLs = acls.stream().filter(acl -> !acl.isEnabled()).collect(Collectors.toList());
    long disabledAclCount = disabledACLs.size();
    assertThat(disabledAclCount).isEqualTo(1);

    ACL disabledACL = disabledACLs.get(0);
    assertThat(disabledACL.getPermissionIdentifier()).isEqualTo(CORE_RESOURCEGROUP_MANAGE_PERMISSION);
    assertThat(disabledACL.getResourceSelector()).isEqualTo("/ACCOUNT/account-id$/USERGROUP/*");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void createACLsOnlyForExactResourceTypeAndMarkRedundantACLDisabled() {
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, true, inMemoryPermissionRepository);
    Set<String> principals = of(getRandomString());

    String allResourceSelector = "/*/*";
    String userGroupSelector = "/ACCOUNT/account-id$/USER/*";
    Set<ResourceSelector> resourceSelectors = of(ResourceSelector.builder().selector(allResourceSelector).build(),
        ResourceSelector.builder().selector(userGroupSelector).build());

    Set<String> permissions = of(CORE_USERGROUP_MANAGE_PERMISSION);

    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    List<List<ACL>> listOfParameters = new ArrayList<>();
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      listOfParameters.add(new ArrayList<>((Collection<ACL>) args[0]));
      return (long) listOfParameters.get(0).size();
    });
    aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);

    assertThat(listOfParameters.size()).isEqualTo(1);
    List<ACL> acls = listOfParameters.get(0);
    assertThat(acls.size()).isEqualTo(2);

    long enabledAcls = acls.stream().filter(ACL::isEnabled).count();
    assertThat(enabledAcls).isEqualTo(1);

    List<ACL> disabledACLs = acls.stream().filter(acl -> !acl.isEnabled()).collect(Collectors.toList());
    long disabledAclCount = disabledACLs.size();
    assertThat(disabledAclCount).isEqualTo(1);

    ACL disabledACL = disabledACLs.get(0);
    assertThat(disabledACL.getPermissionIdentifier()).isEqualTo(CORE_USERGROUP_MANAGE_PERMISSION);
    assertThat(disabledACL.getResourceSelector()).isEqualTo("/ACCOUNT/account-id$/USER/*");
  }

  private Set<String> getRandomStrings(int count) {
    Set<String> randomStrings = new HashSet<>();
    for (int i = 0; i < count; i++) {
      randomStrings.add(getRandomString());
    }
    return randomStrings;
  }

  private Set<ResourceSelector> getResourceSelector(int count) {
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    for (int i = 0; i < count; i++) {
      resourceSelectors.add(ResourceSelector.builder().selector(getRandomString()).conditional(false).build());
    }
    return resourceSelectors;
  }

  private String getRandomString() {
    int length = 10;
    return randomAlphabetic(length);
  }

  private RoleAssignmentDBO getRoleAssignment(PrincipalType principalType) {
    return RoleAssignmentDBO.builder()
        .id(getRandomString())
        .resourceGroupIdentifier(getRandomString())
        .principalType(principalType)
        .principalIdentifier(getRandomString())
        .roleIdentifier(getRandomString())
        .scopeIdentifier(getRandomString())
        .identifier(getRandomString())
        .build();
  }
}
