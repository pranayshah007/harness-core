/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.ACCOUNT_BASIC_ROLE_ONLY;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.commons.helpers.FeatureFlagHelperService;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOBuilder;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class UserRoleAssignmentRemovalMigrationTest extends AccessControlTestBase {
  @Inject private RoleAssignmentRepository roleAssignmentRepository;
  @Mock private FeatureFlagHelperService featureFlagHelperService;
  @Mock private AccountUtils accountUtils;
  @Inject private ScopeService scopeService;
  private UserRoleAssignmentRemovalMigration userRoleAssignmentRemovalMigration;

  @Before
  public void setup() {
    featureFlagHelperService = mock(FeatureFlagHelperService.class);
    accountUtils = mock(AccountUtils.class);
    userRoleAssignmentRemovalMigration = new UserRoleAssignmentRemovalMigration(
        roleAssignmentRepository, featureFlagHelperService, accountUtils, scopeService);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void
  testMigrateAccounts_WhenAccountScopeUserGroupRoleAssignmentDoesNotExist_ThenSkipsDeletingAccountScopeUserRoleAssignment() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    when(featureFlagHelperService.isEnabled(ACCOUNT_BASIC_ROLE_ONLY, accountIdentifier)).thenReturn(false);

    when(accountUtils.getAllNGAccountIds()).thenReturn(Arrays.asList(accountIdentifier));
    String principalIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO accountScopeUserRoleAssignment =
        createAccountScopeRoleAssignment(accountIdentifier, PrincipalType.USER, principalIdentifier);
    roleAssignmentRepository.save(accountScopeUserRoleAssignment);
    RoleAssignmentDBO orgScopeUserRoleAssignment =
        createOrganizationScopeUserRoleAssignment(accountIdentifier, orgIdentifier);
    roleAssignmentRepository.save(orgScopeUserRoleAssignment);
    RoleAssignmentDBO projectScopeUserRoleAssignment =
        createProjectScopeUserRoleAssignment(accountIdentifier, orgIdentifier, projectIdentifier);
    roleAssignmentRepository.save(projectScopeUserRoleAssignment);

    userRoleAssignmentRemovalMigration.migrate();

    Page<RoleAssignmentDBO> postMigrationRoleAssignments = roleAssignmentRepository.findAll(Pageable.unpaged());
    assertEquals(1, postMigrationRoleAssignments.getTotalElements());
    assertPostMigration(accountScopeUserRoleAssignment, postMigrationRoleAssignments.getContent().get(0));
  }

  private RoleAssignmentDBO createAccountScopeRoleAssignment(
      String accountIdentifier, PrincipalType principalType, String principalIdentifier) {
    Scope scope = ScopeMapper.fromDTO(
        ScopeDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(null).projectIdentifier(null).build());

    String roleAssignmentIdentifier = randomAlphabetic(10);
    RoleAssignmentDBOBuilder existingRoleAssignmentDBOBuilder =
        RoleAssignmentDBO.builder()
            .scopeIdentifier(scope.toString())
            .scopeLevel(scope.getLevel().toString())
            .identifier(roleAssignmentIdentifier)
            .principalIdentifier(principalIdentifier)
            .principalType(principalType)
            .roleIdentifier(ACCOUNT_VIEWER_ROLE)
            .resourceGroupIdentifier(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);

    if (principalType == PrincipalType.USER_GROUP) {
      existingRoleAssignmentDBOBuilder.principalScopeLevel(HarnessScopeLevel.ACCOUNT.getName());
    }
    RoleAssignmentDBO existingRoleAssignmentDBO = existingRoleAssignmentDBOBuilder.build();
    return existingRoleAssignmentDBO;
  }

  private RoleAssignmentDBO createOrganizationScopeUserRoleAssignment(String accountIdentifier, String orgIdentifier) {
    Scope scope = ScopeMapper.fromDTO(ScopeDTO.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(null)
                                          .build());
    String principalIdentifier = randomAlphabetic(10);
    String roleAssignmentIdentifier = randomAlphabetic(10);
    return RoleAssignmentDBO.builder()
        .scopeIdentifier(scope.toString())
        .scopeLevel(scope.getLevel().toString())
        .identifier(roleAssignmentIdentifier)
        .principalIdentifier(principalIdentifier)
        .managed(true)
        .principalType(PrincipalType.USER)
        .roleIdentifier(ORGANIZATION_VIEWER_ROLE)
        .resourceGroupIdentifier(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER)
        .build();
  }

  private RoleAssignmentDBO createProjectScopeUserRoleAssignment(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Scope scope = ScopeMapper.fromDTO(ScopeDTO.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .build());
    String principalIdentifier = randomAlphabetic(10);
    String roleAssignmentIdentifier = randomAlphabetic(10);
    return RoleAssignmentDBO.builder()
        .scopeIdentifier(scope.toString())
        .scopeLevel(scope.getLevel().toString())
        .identifier(roleAssignmentIdentifier)
        .principalIdentifier(principalIdentifier)
        .managed(true)
        .principalType(PrincipalType.USER)
        .roleIdentifier(PROJECT_VIEWER_ROLE)
        .resourceGroupIdentifier(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
        .build();
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void
  testMigrateAccounts_WhenAccountScopeUserGroupRoleAssignmentExists_ThenDeletesAccountScopeUserRoleAssignment() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    when(featureFlagHelperService.isEnabled(ACCOUNT_BASIC_ROLE_ONLY, accountIdentifier)).thenReturn(false);

    when(accountUtils.getAllNGAccountIds()).thenReturn(Arrays.asList(accountIdentifier));
    String principalIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO accountScopeUserRoleAssignment =
        createAccountScopeRoleAssignment(accountIdentifier, PrincipalType.USER, principalIdentifier);
    roleAssignmentRepository.save(accountScopeUserRoleAssignment);
    RoleAssignmentDBO orgScopeUserRoleAssignment =
        createOrganizationScopeUserRoleAssignment(accountIdentifier, orgIdentifier);
    roleAssignmentRepository.save(orgScopeUserRoleAssignment);
    RoleAssignmentDBO projectScopeUserRoleAssignment =
        createProjectScopeUserRoleAssignment(accountIdentifier, orgIdentifier, projectIdentifier);
    roleAssignmentRepository.save(projectScopeUserRoleAssignment);

    RoleAssignmentDBO accountScopeUserGroupRoleAssignment = createAccountScopeRoleAssignment(
        accountIdentifier, PrincipalType.USER_GROUP, DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER);
    roleAssignmentRepository.save(accountScopeUserGroupRoleAssignment);

    userRoleAssignmentRemovalMigration.migrate();

    Page<RoleAssignmentDBO> postMigrationRoleAssignments = roleAssignmentRepository.findAll(Pageable.unpaged());
    assertEquals(1, postMigrationRoleAssignments.getTotalElements());
    assertPostMigration(accountScopeUserGroupRoleAssignment, postMigrationRoleAssignments.getContent().get(0));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void
  testMigrateAccounts_WhenAccountScopeUserGroupRoleAssignmentExistsButAccountBasicRoleOnlyFFIsOn_ThenSkipsDeletingAccountScopeUserRoleAssignment() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    when(featureFlagHelperService.isEnabled(ACCOUNT_BASIC_ROLE_ONLY, accountIdentifier)).thenReturn(true);

    when(accountUtils.getAllNGAccountIds()).thenReturn(Arrays.asList(accountIdentifier));
    String principalIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO accountScopeUserRoleAssignment =
        createAccountScopeRoleAssignment(accountIdentifier, PrincipalType.USER, principalIdentifier);
    roleAssignmentRepository.save(accountScopeUserRoleAssignment);
    RoleAssignmentDBO orgScopeUserRoleAssignment =
        createOrganizationScopeUserRoleAssignment(accountIdentifier, orgIdentifier);
    roleAssignmentRepository.save(orgScopeUserRoleAssignment);
    RoleAssignmentDBO projectScopeUserRoleAssignment =
        createProjectScopeUserRoleAssignment(accountIdentifier, orgIdentifier, projectIdentifier);
    roleAssignmentRepository.save(projectScopeUserRoleAssignment);

    RoleAssignmentDBO accountScopeUserGroupRoleAssignment = createAccountScopeRoleAssignment(
        accountIdentifier, PrincipalType.USER_GROUP, DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER);
    roleAssignmentRepository.save(accountScopeUserGroupRoleAssignment);

    userRoleAssignmentRemovalMigration.migrate();

    Page<RoleAssignmentDBO> postMigrationRoleAssignments = roleAssignmentRepository.findAll(Pageable.unpaged());
    assertEquals(2, postMigrationRoleAssignments.getTotalElements());
    assertPostMigration(accountScopeUserRoleAssignment, postMigrationRoleAssignments.getContent().get(0));
    assertPostMigration(accountScopeUserGroupRoleAssignment, postMigrationRoleAssignments.getContent().get(1));
  }

  private void assertPostMigration(
      RoleAssignmentDBO existingRoleAssignmentDBO, RoleAssignmentDBO expectedRoleAssignmentDBO) {
    assertEquals(existingRoleAssignmentDBO.getScopeIdentifier(), expectedRoleAssignmentDBO.getScopeIdentifier());
    assertEquals(existingRoleAssignmentDBO.getScopeLevel(), expectedRoleAssignmentDBO.getScopeLevel());
    assertEquals(existingRoleAssignmentDBO.getRoleIdentifier(), expectedRoleAssignmentDBO.getRoleIdentifier());
    assertEquals(
        existingRoleAssignmentDBO.getResourceGroupIdentifier(), expectedRoleAssignmentDBO.getResourceGroupIdentifier());
    assertEquals(existingRoleAssignmentDBO.getPrincipalType(), expectedRoleAssignmentDBO.getPrincipalType());
    assertEquals(
        existingRoleAssignmentDBO.getPrincipalIdentifier(), expectedRoleAssignmentDBO.getPrincipalIdentifier());
  }
}
