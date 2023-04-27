/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.core;

import static io.harness.accesscontrol.scopes.core.Scope.PATH_DELIMITER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
@ValidateOnExecution
public class ScopeHelper {
  public static Scope toParentScope(@NotNull Scope scope, String parentScopeLevel) {
    if (parentScopeLevel == null) {
      return null;
    }
    Scope currentScope = scope;
    while (currentScope != null) {
      ScopeLevel scopeLevel = currentScope.getLevel();
      if (scopeLevel.toString().equals(parentScopeLevel)) {
        return currentScope;
      }
      currentScope = currentScope.getParentScope();
    }
    return null;
  }

  /*
  It expects scope identifier parameter in either of below format
  "/ACCOUNT/<account-id>"
  "/ACCOUNT/<account-id>/ORGANIZATION/<org-id>"
  "/ACCOUNT/<account-id>/ORGANIZATION/<org-id>/PROJECT/<project-id>"
  and splits it based on '/' and returns <account-id> from it.

  If input parameter is null OR do not have correct format it returns null.
   */
  public static String getAccountFromScopeIdentifier(String scopeIdentifier) {
    if (scopeIdentifier == null) {
      return null;
    }
    List<String> scopeIdentifierElements = Arrays.asList(scopeIdentifier.split(PATH_DELIMITER));
    if (scopeIdentifierElements.size() < 3) {
      return null;
    }
    return scopeIdentifierElements.get(2);
  }
}
