/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;

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
import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ServiceAccountValidatorTest extends AccessControlTestBase {
  private ServiceAccountService serviceAccountService;
  private ScopeService scopeService;
  private PrincipalValidator principalValidator;

  private final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private final String ORG_IDENTIFIER = randomAlphabetic(10);
  private final String PROJECT_IDENTIFIER = randomAlphabetic(10);

  @Before
  public void setup() {
    serviceAccountService = mock(ServiceAccountService.class);
    scopeService = mock(ScopeService.class);
    principalValidator = spy(new ServiceAccountValidator(serviceAccountService, scopeService));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    assertEquals(PrincipalType.SERVICE_ACCOUNT, principalValidator.getPrincipalType());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidatePrincipalValid_inSameScope() {
    String scopeIdentifier =
        "/ACCOUNT/" + ACCOUNT_IDENTIFIER + "/ORGANIZATION/" + ORG_IDENTIFIER + "/PROJECT/" + PROJECT_IDENTIFIER;
    String principalIdentifier = randomAlphabetic(11);
    Principal principal = Principal.builder()
                              .principalType(PrincipalType.SERVICE_ACCOUNT)
                              .principalIdentifier(principalIdentifier)
                              .build();
    Scope scope = getScope();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier)).thenReturn(scope);

    when(serviceAccountService.get(principalIdentifier, scopeIdentifier))
        .thenReturn(Optional.of(ServiceAccount.builder().build()));
    assertTrue(principalValidator.validatePrincipal(principal, scopeIdentifier).isValid());
    verify(serviceAccountService, times(1)).get(principalIdentifier, scopeIdentifier);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testValidatePrincipalValid_inParentScope() {
    String scopeIdentifier =
        "/ACCOUNT/" + ACCOUNT_IDENTIFIER + "/ORGANIZATION/" + ORG_IDENTIFIER + "/PROJECT/" + PROJECT_IDENTIFIER;
    String principalIdentifier = randomAlphabetic(11);
    Principal principal = Principal.builder()
                              .principalType(PrincipalType.SERVICE_ACCOUNT)
                              .principalIdentifier(principalIdentifier)
                              .build();
    Scope scope = getScope();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier)).thenReturn(scope);

    String orgScopeIdentifier = scope.getParentScope().toString();
    String accountScopeIdentifier = scope.getParentScope().getParentScope().toString();

    when(serviceAccountService.get(principalIdentifier, scopeIdentifier)).thenReturn(Optional.empty());
    when(serviceAccountService.get(principalIdentifier, orgScopeIdentifier)).thenReturn(Optional.empty());

    when(serviceAccountService.get(principalIdentifier, accountScopeIdentifier))
        .thenReturn(Optional.of(ServiceAccount.builder().build()));
    assertTrue(principalValidator.validatePrincipal(principal, scopeIdentifier).isValid());
    verify(serviceAccountService, times(1)).get(principalIdentifier, scopeIdentifier);
    verify(serviceAccountService, times(1)).get(principalIdentifier, orgScopeIdentifier);
    verify(serviceAccountService, times(1)).get(principalIdentifier, accountScopeIdentifier);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testValidatePrincipalInValid() {
    String scopeIdentifier =
        "/ACCOUNT/" + ACCOUNT_IDENTIFIER + "/ORGANIZATION/" + ORG_IDENTIFIER + "/PROJECT/" + PROJECT_IDENTIFIER;
    String principalIdentifier = randomAlphabetic(11);
    Principal principal = Principal.builder()
                              .principalType(PrincipalType.SERVICE_ACCOUNT)
                              .principalIdentifier(principalIdentifier)
                              .build();
    Scope scope = getScope();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier)).thenReturn(scope);

    String orgScopeIdentifier = scope.getParentScope().toString();
    String accountScopeIdentifier = scope.getParentScope().getParentScope().toString();

    when(serviceAccountService.get(principalIdentifier, scopeIdentifier)).thenReturn(Optional.empty());
    when(serviceAccountService.get(principalIdentifier, orgScopeIdentifier)).thenReturn(Optional.empty());

    when(serviceAccountService.get(principalIdentifier, accountScopeIdentifier)).thenReturn(Optional.empty());
    assertFalse(principalValidator.validatePrincipal(principal, scopeIdentifier).isValid());
    verify(serviceAccountService, times(1)).get(principalIdentifier, scopeIdentifier);
    verify(serviceAccountService, times(1)).get(principalIdentifier, orgScopeIdentifier);
    verify(serviceAccountService, times(1)).get(principalIdentifier, accountScopeIdentifier);
  }

  private Scope getScope() {
    return Scope.builder()
        .instanceId(PROJECT_IDENTIFIER)
        .level(HarnessScopeLevel.PROJECT)
        .parentScope(
            Scope.builder()
                .instanceId(ORG_IDENTIFIER)
                .level(HarnessScopeLevel.ORGANIZATION)
                .parentScope(Scope.builder().instanceId(ACCOUNT_IDENTIFIER).level(HarnessScopeLevel.ACCOUNT).build())
                .build())
        .build();
  }
}
