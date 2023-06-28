/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.persistence;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.ACLDAOImpl;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ACLDAOImplTest extends AccessControlCoreTestBase {
  private ACLRepository aclRepository;
  private Map<String, ScopeLevel> scopeLevels;
  private ACLDAOImpl aclDaoImpl;

  @Before
  public void setup() {
    aclRepository = mock(ACLRepository.class);
    scopeLevels = new HashMap<>();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOnlyEnabledACLMatch() {
    aclDaoImpl = new ACLDAOImpl(aclRepository, scopeLevels, true);
    Principal principal = Principal.of(PrincipalType.USER, randomAlphabetic(10));
    List<PermissionCheck> permissionChecks = new ArrayList<>();
    permissionChecks.add(PermissionCheck.builder().resourceType("user").permission("core_user_view").build());
    aclDaoImpl.getMatchingACLs(principal, permissionChecks);

    verify(aclRepository, times(1)).getByAclQueryStringInAndEnabled(any(), anyBoolean());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAllACLMatch() {
    aclDaoImpl = new ACLDAOImpl(aclRepository, scopeLevels, false);
    Principal principal = Principal.of(PrincipalType.USER, randomAlphabetic(10));
    List<PermissionCheck> permissionChecks = new ArrayList<>();
    permissionChecks.add(PermissionCheck.builder().resourceType("user").permission("core_user_view").build());
    aclDaoImpl.getMatchingACLs(principal, permissionChecks);

    verify(aclRepository, times(1)).getByAclQueryStringIn(any());
  }
}
