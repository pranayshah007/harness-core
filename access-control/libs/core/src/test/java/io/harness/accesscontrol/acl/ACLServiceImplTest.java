/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl;

import static io.harness.accesscontrol.permissions.PermissionStatus.EXPERIMENTAL;
import static io.harness.accesscontrol.permissions.PermissionStatus.INACTIVE;
import static io.harness.accesscontrol.permissions.PermissionStatus.STAGING;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Set.of;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.conditions.ACLExpressionEvaluatorProvider;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACLDAO;
import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class ACLServiceImplTest extends AccessControlCoreTestBase {
  private ACLDAO aclDAO;
  private PermissionService permissionService;
  private ACLServiceImpl aclService;
  private MongoTemplate mongoTemplate;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    aclDAO = mock(ACLDAO.class);
    permissionService = mock(PermissionService.class);
    mongoTemplate = mock(MongoTemplate.class);

    ACLExpressionEvaluatorProvider aclExpressionEvaluatorProvider = mock(ACLExpressionEvaluatorProvider.class);
    inMemoryPermissionRepository = mock(InMemoryPermissionRepository.class);
    aclService = new ACLServiceImpl(
        aclDAO, permissionService, aclExpressionEvaluatorProvider, mongoTemplate, inMemoryPermissionRepository);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCheckAccessDisabledPermissions() {
    List<Permission> disabledPermissions = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      disabledPermissions.add(Permission.builder().identifier(randomAlphabetic(10)).status(INACTIVE).build());
    }
    when(permissionService.list(
             PermissionFilter.builder().statusFilter(Sets.newHashSet(INACTIVE, EXPERIMENTAL, STAGING)).build()))
        .thenReturn(disabledPermissions);
    when(inMemoryPermissionRepository.getResourceTypesApplicableToPermission(any())).thenReturn(of());

    Principal principal = Principal.of(PrincipalType.USER, randomAlphabetic(10));
    List<PermissionCheck> permissionChecks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      permissionChecks.add(PermissionCheck.builder()
                               .resourceType("resource")
                               .permission(disabledPermissions.get(0).getIdentifier())
                               .build());
    }
    List<List<ACL>> aclResults = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      aclResults.add(new ArrayList<>());
    }
    when(aclDAO.getMatchingACLs(principal, permissionChecks)).thenReturn(aclResults);
    List<PermissionCheckResult> response = aclService.checkAccess(principal, permissionChecks, null);

    assertEquals(10, response.size());
    response.forEach(check -> assertTrue(check.isPermitted()));
  }
}
