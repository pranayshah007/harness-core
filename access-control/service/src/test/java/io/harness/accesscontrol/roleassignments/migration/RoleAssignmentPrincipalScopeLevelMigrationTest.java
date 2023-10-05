/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromDTO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccount;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class RoleAssignmentPrincipalScopeLevelMigrationTest extends AccessControlTestBase {
  @Inject private RoleAssignmentRepository roleAssignmentRepository;
  @Mock private HarnessServiceAccountService harnessServiceAccountService;
  @Inject private ScopeService scopeService;
  @Inject private ServiceAccountService serviceAccountService;
  private RoleAssignmentPrincipalScopeLevelMigration roleAssignmentPrincipalScopeLevelMigration;
  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String ORG_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_IDENTIFIER = randomAlphabetic(10);
  private static final String SERVICE_ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String USER_GROUP_IDENTIFIER = randomAlphabetic(10);
  private static final Scope ORG_SCOPE =
      fromDTO(ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG_IDENTIFIER).build());
  private static final Scope PROJECT_SCOPE = fromDTO(ScopeDTO.builder()
                                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .build());

  @Before
  public void setup() {
    harnessServiceAccountService = mock(HarnessServiceAccountService.class);
    roleAssignmentPrincipalScopeLevelMigration = new RoleAssignmentPrincipalScopeLevelMigration(
        roleAssignmentRepository, scopeService, harnessServiceAccountService, serviceAccountService);
    serviceAccountService.createIfNotPresent(
        ServiceAccount.builder().identifier(SERVICE_ACCOUNT_IDENTIFIER).scopeIdentifier(ORG_SCOPE.toString()).build());
    doNothing().when(harnessServiceAccountService).sync(SERVICE_ACCOUNT_IDENTIFIER, PROJECT_SCOPE);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testMigrateServiceAccount() {
    String roleAssignmentIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO existingRoleAssignmentDBO = RoleAssignmentDBO.builder()
                                                      .scopeIdentifier(PROJECT_SCOPE.toString())
                                                      .scopeLevel(PROJECT_SCOPE.getLevel().toString())
                                                      .identifier(roleAssignmentIdentifier)
                                                      .principalIdentifier(SERVICE_ACCOUNT_IDENTIFIER)
                                                      .principalType(PrincipalType.SERVICE_ACCOUNT)
                                                      .roleIdentifier(randomAlphabetic(10))
                                                      .resourceGroupIdentifier(randomAlphabetic(10))
                                                      .build();
    roleAssignmentRepository.save(existingRoleAssignmentDBO);
    roleAssignmentPrincipalScopeLevelMigration.migrate();
    assertPostMigration(existingRoleAssignmentDBO, ORG_SCOPE.getLevel().toString());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testMigrateUserGroup() {
    String roleAssignmentIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO existingRoleAssignmentDBO = RoleAssignmentDBO.builder()
                                                      .scopeIdentifier(PROJECT_SCOPE.toString())
                                                      .scopeLevel(PROJECT_SCOPE.getLevel().toString())
                                                      .identifier(roleAssignmentIdentifier)
                                                      .principalIdentifier(USER_GROUP_IDENTIFIER)
                                                      .principalType(PrincipalType.USER_GROUP)
                                                      .roleIdentifier(randomAlphabetic(10))
                                                      .resourceGroupIdentifier(randomAlphabetic(10))
                                                      .build();
    roleAssignmentRepository.save(existingRoleAssignmentDBO);
    roleAssignmentPrincipalScopeLevelMigration.migrate();
    assertPostMigration(existingRoleAssignmentDBO, PROJECT_SCOPE.getLevel().toString());
  }

  private void assertPostMigration(RoleAssignmentDBO existingRoleAssignmentDBO, String expectedPrincipalScope) {
    Page<RoleAssignmentDBO> postMigration = roleAssignmentRepository.findAll(Pageable.unpaged());
    assertEquals(1, postMigration.getTotalElements());
    RoleAssignmentDBO migratedRoleAssignmentDBO = postMigration.getContent().get(0);
    assertEquals(existingRoleAssignmentDBO.getScopeIdentifier(), migratedRoleAssignmentDBO.getScopeIdentifier());
    assertEquals(existingRoleAssignmentDBO.getScopeLevel(), migratedRoleAssignmentDBO.getScopeLevel());
    assertEquals(existingRoleAssignmentDBO.getRoleIdentifier(), migratedRoleAssignmentDBO.getRoleIdentifier());
    assertEquals(
        existingRoleAssignmentDBO.getResourceGroupIdentifier(), migratedRoleAssignmentDBO.getResourceGroupIdentifier());
    assertEquals(existingRoleAssignmentDBO.getPrincipalType(), migratedRoleAssignmentDBO.getPrincipalType());
    assertEquals(
        existingRoleAssignmentDBO.getPrincipalIdentifier(), migratedRoleAssignmentDBO.getPrincipalIdentifier());
    assertEquals(expectedPrincipalScope, migratedRoleAssignmentDBO.getPrincipalScopeLevel());
  }
}
