/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scope;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.encryption.Scope;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ScopeHelper {
  public static Scope getScope(String accountId, String orgId, String projectId) {
    if (isNotEmpty(projectId)) {
      return Scope.PROJECT;
    } else if (isNotEmpty(orgId)) {
      return Scope.ORG;
    } else if (isNotEmpty(accountId)) {
      return Scope.ACCOUNT;
    }
    return Scope.UNKNOWN;
  }

  public static String getScopeMessageForLogs(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    StringBuilder scopeMessage = new StringBuilder(32);
    scopeMessage.append("account ").append(accountIdentifier);
    if (orgIdentifier != null) {
      scopeMessage.append(", org ").append(orgIdentifier);
    }
    if (projectIdentifier != null) {
      scopeMessage.append(", project ").append(projectIdentifier);
    }
    return scopeMessage.toString();
  }
}
