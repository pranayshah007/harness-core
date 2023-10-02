/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.constants;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum PolicyType {
  DENY_LIST("denyList", ViolationType.DENYLIST_VIOLATION),
  ALLOW_LIST("allowList", ViolationType.ALLOWLIST_VIOLATION);

  String value;

  ViolationType violationType;

  public String getValue() {
    return this.value;
  }

  public ViolationType getViolationType() {
    return this.violationType;
  }
}
