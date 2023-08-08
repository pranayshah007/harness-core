/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.constants;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ViolationType {
  DENYLIST_VIOLATION("Deny List Violation"),
  ALLOWLIST_VIOLATION("Allow List Violation"),
  UNKNOWN_VIOLATION("Unknown Violation");

  String violation;

  public String getViolation() {
    return this.violation;
  }
}
