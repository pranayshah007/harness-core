/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.PrincipalValidator;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class ServiceAccountValidator implements PrincipalValidator {
  private final ServiceAccountService serviceAccountService;
  private final ScopeService scopeService;

  @Inject
  public ServiceAccountValidator(ServiceAccountService serviceAccountService, ScopeService scopeService) {
    this.serviceAccountService = serviceAccountService;
    this.scopeService = scopeService;
  }

  @Override
  public PrincipalType getPrincipalType() {
    return PrincipalType.SERVICE_ACCOUNT;
  }

  @Override
  public ValidationResult validatePrincipal(Principal principal, String scopeIdentifier) {
    Scope scope = scopeService.buildScopeFromScopeIdentifier(scopeIdentifier);
    String identifier = principal.getPrincipalIdentifier();
    Scope currentScope = scope;
    while (currentScope != null) {
      if (serviceAccountService.get(identifier, currentScope.toString()).isPresent()) {
        return ValidationResult.builder().valid(true).build();
      }
      currentScope = currentScope.getParentScope();
    }
    return ValidationResult.builder()
        .valid(false)
        .errorMessage(String.format(
            "Service Account not found with the given identifier %s in account %s", identifier, scopeIdentifier))
        .build();
  }
}
