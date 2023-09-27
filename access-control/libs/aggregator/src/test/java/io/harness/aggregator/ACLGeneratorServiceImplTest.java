/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.accesscontrol.resources.resourcegroups.ResourceSelector.builder;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static java.util.Set.of;
import static junit.framework.TestCase.assertEquals;
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
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.ScopeSelector;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
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
  public static final String USERGROUP_RESOURCE_SELECTOR = "/ACCOUNT/account-id$/USERGROUP/*";

  public static final String CORE_RESOURCEGROUP_MANAGE_PERMISSION = "core_resourcegroup_manage";
  public static final String RESOURCEGROUP_RESOURCE_NAME = "resourcegroup";
  public static final String RESOURCEGROUP_RESOURCE_IDENTIFIER = "RESOURCEGROUP";
  public static final String RESOURCEGROUP_RESOURCE_SELECTOR = "/ACCOUNT/account-id$/RESOURCEGROUP/*";

  public static final String USER_RESOURCE_SELECTOR = "/ACCOUNT/account-id$/USER/*";
  public static final String ALL_RESOURCE_SELECTOR = "/*/*";

  private ACLRepository aclRepository;
  private ACLGeneratorService aclGeneratorService;
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

    inMemoryPermissionRepository =
        new InMemoryPermissionRepository(mongoTemplate, Map.of("ccm_perspective_view", Set.of("CCM_FOLDER")));
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, inMemoryPermissionRepository);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_LessThanBufferSize() {
    Set<String> principals = getRandomStrings(250);
    Set<ResourceSelector> resourceSelectors = Set.of(builder().selector(ALL_RESOURCE_SELECTOR).build(),
        builder().selector(USERGROUP_RESOURCE_SELECTOR).build(),
        builder().selector(RESOURCEGROUP_RESOURCE_SELECTOR).build());
    Set<String> permissions = Set.of(CORE_USERGROUP_MANAGE_PERMISSION, CORE_RESOURCEGROUP_MANAGE_PERMISSION);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    List<List<ACL>> listOfParameters = new ArrayList<>();
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      listOfParameters.add(new ArrayList<>((Collection<ACL>) args[0]));
      return (long) listOfParameters.get(0).size();
    });

    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);

    assertThat(aclCount).isEqualTo(1000);
    verify(aclRepository, times(1)).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_MoreThanBufferSize_CallsRepositoryMultipleTimes() {
    Set<String> principals = getRandomStrings(50000);
    Set<ResourceSelector> resourceSelectors = Set.of(builder().selector(ALL_RESOURCE_SELECTOR).build());
    Set<String> permissions = Set.of(CORE_USERGROUP_MANAGE_PERMISSION, CORE_RESOURCEGROUP_MANAGE_PERMISSION);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    List<List<ACL>> listOfParameters = new ArrayList<>();
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      listOfParameters.add(new ArrayList<>((Collection<ACL>) args[0]));
      long count = listOfParameters.get(0).size();
      listOfParameters.clear();
      return count;
    });

    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);
    assertThat(aclCount).isEqualTo(100000);

    verify(aclRepository, times(2)).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_MoreThanAllowed_ReturnsZeroCreated() {
    Set<String> principals = getRandomStrings(1000001);
    Set<ResourceSelector> resourceSelectors = Set.of(builder().selector(ALL_RESOURCE_SELECTOR).build());
    Set<String> permissions = Set.of(CORE_USERGROUP_MANAGE_PERMISSION, CORE_RESOURCEGROUP_MANAGE_PERMISSION);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);

    verify(aclRepository, never()).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createACLs_TillMaxAllowed_ReturnsCreated() {
    Set<String> principals = getRandomStrings(2000000);
    Set<ResourceSelector> resourceSelectors = Set.of(builder().selector(ALL_RESOURCE_SELECTOR).build());
    Set<String> permissions = Set.of(CORE_USERGROUP_MANAGE_PERMISSION);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);

    List<List<ACL>> listOfParameters = new ArrayList<>();
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      listOfParameters.add(new ArrayList<>((Collection<ACL>) args[0]));
      long count = listOfParameters.get(0).size();
      listOfParameters.clear();
      return count;
    });

    long aclCount = aclGeneratorService.createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);

    assertThat(aclCount).isEqualTo(2000000);
    verify(aclRepository, times(40)).insertAllIgnoringDuplicates(any());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void createACLs() {
    Set<String> principals = of(getRandomString());

    Set<ResourceSelector> resourceSelectors = Set.of(
        builder().selector(ALL_RESOURCE_SELECTOR).build(), builder().selector(USERGROUP_RESOURCE_SELECTOR).build());

    Set<String> permissions = Set.of(CORE_USERGROUP_MANAGE_PERMISSION, CORE_RESOURCEGROUP_MANAGE_PERMISSION);

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
    assertThat(acls.size()).isEqualTo(3);

    long enabledAcls = acls.stream().filter(ACL::isEnabled).count();
    assertThat(enabledAcls).isEqualTo(3);
    long disabledAcls = acls.stream().filter(acl -> !acl.isEnabled()).count();
    assertThat(disabledAcls).isEqualTo(0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void createACLsOnlyForExactResourceTypeAndDoNotCreateRedundantOrDisabledACL() {
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, inMemoryPermissionRepository);
    Set<String> principals = of(getRandomString());

    Set<ResourceSelector> resourceSelectors =
        Set.of(builder().selector(ALL_RESOURCE_SELECTOR).build(), builder().selector(USER_RESOURCE_SELECTOR).build());

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
    assertThat(acls.size()).isEqualTo(1);

    long enabledAcls = acls.stream().filter(ACL::isEnabled).count();
    assertThat(enabledAcls).isEqualTo(1);
    ACL enabledAcl = acls.get(0);
    assertThat(enabledAcl.getPermissionIdentifier()).isEqualTo(CORE_USERGROUP_MANAGE_PERMISSION);
    assertThat(enabledAcl.getResourceSelector()).isEqualTo(ALL_RESOURCE_SELECTOR);

    List<ACL> disabledACLs = acls.stream().filter(acl -> !acl.isEnabled()).collect(Collectors.toList());
    long disabledAclCount = disabledACLs.size();
    assertThat(disabledAclCount).isEqualTo(0);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createImplicitACLs_NoScopeSelected_CreatesNoImplicitACLs() {
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, inMemoryPermissionRepository);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);
    Set<String> permissions = new HashSet<>();
    Set<String> usersAdded = new HashSet<>();
    Optional<Role> role = Optional.of(Role.builder().permissions(permissions).build());
    when(roleService.get(
             roleAssignmentDBO.getRoleIdentifier(), roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(role);
    Optional<ResourceGroup> resourceGroup = Optional.empty();
    when(resourceGroupService.get(roleAssignmentDBO.getResourceGroupIdentifier(),
             roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(resourceGroup);
    long aclsCreated = aclGeneratorService.createImplicitACLs(roleAssignmentDBO, usersAdded);
    assertEquals(aclsCreated, 0);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void createImplicitACLs_ForSpecificUsers_CreatesOnlyForThoseUsers() {
    Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope = new HashMap<>();
    implicitPermissionsByScope.put(
        Pair.of(TestScopeLevels.TEST_SCOPE, false), new HashSet<>(Arrays.asList("core_account_view")));
    aclGeneratorService = new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        new HashMap<>(), aclRepository, inMemoryPermissionRepository);
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignment(PrincipalType.USER_GROUP);
    Set<String> permissions = new HashSet<>(Arrays.asList("core_account_view"));
    Optional<Role> role = Optional.of(Role.builder().permissions(permissions).build());
    Set<String> usersAdded = new HashSet<>();
    when(roleService.get(
             roleAssignmentDBO.getRoleIdentifier(), roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(role);
    HashSet<ScopeSelector> scopeSelectors =
        new HashSet<>(Arrays.asList(ScopeSelector.builder().scopeIdentifier("").build()));
    io.harness.accesscontrol.scopes.core.Scope accountScope = io.harness.accesscontrol.scopes.core.Scope.builder()
                                                                  .level(TestScopeLevels.TEST_SCOPE)
                                                                  .parentScope(null)
                                                                  .instanceId("")
                                                                  .build();
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(accountScope);
    Optional<ResourceGroup> resourceGroup = Optional.of(ResourceGroup.builder().scopeSelectors(scopeSelectors).build());
    when(resourceGroupService.get(
             roleAssignmentDBO.getRoleIdentifier(), roleAssignmentDBO.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(resourceGroup);
    when(aclRepository.insertAllIgnoringDuplicates(any())).thenReturn(1L);

    long aclsCreated = aclGeneratorService.createImplicitACLs(roleAssignmentDBO, usersAdded);

    assertEquals(aclsCreated, 1L);
  }

  private Set<String> getRandomStrings(int count) {
    Set<String> randomStrings = new HashSet<>();
    for (int i = 0; i < count; i++) {
      randomStrings.add(getRandomString());
    }
    return randomStrings;
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
