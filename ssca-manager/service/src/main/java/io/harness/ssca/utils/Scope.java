/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Scope {
  ACCOUNT("account"),
  ORG("org"),
  PROJECT("project");

  String name;

  public static Scope getScope(String scopeString) {
    for (Scope s : Scope.values()) {
      if (s.getName().equals(scopeString.toLowerCase())) {
        return s;
      }
    }
    return null;
  }
}
