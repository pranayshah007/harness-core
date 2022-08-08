/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.PrincipalValidator;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class UserValidatorTest extends AccessControlTestBase {
  private UserService userService;
  private PrincipalValidator principalValidator;

  @Before
  public void setup() {
    userService = mock(UserService.class);
    principalValidator = spy(new UserValidator(userService));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    assertEquals(PrincipalType.USER, principalValidator.getPrincipalType());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidatePrincipalValid() {
    String scopeIdentifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(11);
    Principal principal =
        Principal.builder().principalType(PrincipalType.USER).principalIdentifier(principalIdentifier).build();
    when(userService.get(principalIdentifier, scopeIdentifier)).thenReturn(Optional.of(User.builder().build()));
    assertTrue(principalValidator.validatePrincipal(principal, scopeIdentifier).isValid());
    verify(userService, times(1)).get(principalIdentifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidatePrincipalInValid() {
    String scopeIdentifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(11);
    Principal principal =
        Principal.builder().principalType(PrincipalType.USER).principalIdentifier(principalIdentifier).build();
    when(userService.get(principalIdentifier, scopeIdentifier)).thenReturn(Optional.empty());
    assertFalse(principalValidator.validatePrincipal(principal, scopeIdentifier).isValid());
    verify(userService, times(1)).get(principalIdentifier, scopeIdentifier);
  }
}
