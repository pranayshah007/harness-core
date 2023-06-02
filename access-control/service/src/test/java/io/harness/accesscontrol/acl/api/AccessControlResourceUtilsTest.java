/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.api;

import static io.harness.accesscontrol.acl.api.AccessControlResourceUtils.checkPreconditions;
import static io.harness.accesscontrol.acl.api.AccessControlResourceUtils.serviceContextAndOnlyServicePrincipalInBody;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class AccessControlResourceUtilsTest extends AccessControlTestBase {
  @Before
  public void setup() {}

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void
  checkPreconditions_ServiceContextAndDifferentServicePrincipalInBody_When_DifferentPrincipalInContextAndBody_IsAllowed_ReturnsTrue() {
    io.harness.security.dto.Principal principalInContext = new ServicePrincipal("service1");
    Principal principalToCheckPermissions =
        Principal.builder().principalType(PrincipalType.SERVICE).principalIdentifier("service2").build();
    assertTrue(checkPreconditions(principalInContext, principalToCheckPermissions));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void
  serviceContextAndOnlyServicePrincipalInBody_ServiceContextAndDifferentServicePrincipalInBody_When_DifferentPrincipalInContextAndBody_IsAllowed_ReturnsFalse() {
    io.harness.security.dto.Principal principalInContext = new ServicePrincipal("service1");
    Principal principalToCheckPermissions =
        Principal.builder().principalType(PrincipalType.SERVICE).principalIdentifier("service2").build();
    assertTrue(serviceContextAndOnlyServicePrincipalInBody(principalInContext, principalToCheckPermissions));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkPreconditions_ServiceContextAndNoPrincipalInBody_ReturnsTrue() {
    io.harness.security.dto.Principal principalInContext = new ServicePrincipal("testService");
    // Principal principalToCheckPermissions = Principal.builder().principalType(PrincipalType.SERVICE).build();
    assertTrue(checkPreconditions(principalInContext, null));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkPreconditions_ServiceContextAndSamePrincipalInBody_ReturnsTrue() {
    io.harness.security.dto.Principal principalInContext = new ServicePrincipal("testService");
    Principal principalToCheckPermissions =
        Principal.builder().principalType(PrincipalType.SERVICE).principalIdentifier("testService").build();
    assertTrue(checkPreconditions(principalInContext, principalToCheckPermissions));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkPreconditions_ServiceContextAndNonServicePrincipalInBody_ReturnsTrue() {
    io.harness.security.dto.Principal principalInContext = new ServicePrincipal("testService");
    Principal principalToCheckPermissions =
        Principal.builder().principalType(PrincipalType.USER).principalIdentifier("testUser").build();
    assertTrue(checkPreconditions(principalInContext, principalToCheckPermissions));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkPreconditions_DifferentUserPrincipal_InContext_And_InBody_ReturnsFalse() {
    io.harness.security.dto.Principal principalInContext = new UserPrincipal("user1", "userEmail", "user1", "123");
    Principal principalToCheckPermissions =
        Principal.builder().principalType(PrincipalType.USER).principalIdentifier("user2").build();
    assertFalse(checkPreconditions(principalInContext, principalToCheckPermissions));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkPreconditions_SameUserPrincipal_InContext_In_Body_ReturnsTrue() {
    io.harness.security.dto.Principal principalInContext = new UserPrincipal("user1", "userEmail", "user1", "123");
    Principal principalToCheckPermissions =
        Principal.builder().principalType(PrincipalType.USER).principalIdentifier("user1").build();
    assertTrue(checkPreconditions(principalInContext, principalToCheckPermissions));
  }
}
