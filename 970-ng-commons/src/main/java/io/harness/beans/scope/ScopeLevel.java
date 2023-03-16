/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum ScopeLevel {
  ACCOUNT("ACCOUNT"),
  ORGANIZATION("ORGANIZATION"),
  PROJECT("PROJECT");

  String name;

  ScopeLevel(String name) {
    this.name = name;
  }

  public static ScopeLevel of(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (!isBlank(projectIdentifier)) {
      return ScopeLevel.PROJECT;
    }
    if (!isBlank(orgIdentifier)) {
      return ScopeLevel.ORGANIZATION;
    }
    return ScopeLevel.ACCOUNT;
  }

  public static ScopeLevel of(Scope scope) {
    if (!isBlank(scope.getProjectIdentifier())) {
      return ScopeLevel.PROJECT;
    }
    if (!isBlank(scope.getOrgIdentifier())) {
      return ScopeLevel.ORGANIZATION;
    }
    return ScopeLevel.ACCOUNT;
  }
}
