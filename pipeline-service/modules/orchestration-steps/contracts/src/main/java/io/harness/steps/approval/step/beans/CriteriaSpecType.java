/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CDC)
public enum CriteriaSpecType {
  @JsonProperty(CriteriaSpecTypeConstants.JEXL) JEXL(CriteriaSpecTypeConstants.JEXL),
  @JsonProperty(CriteriaSpecTypeConstants.KEY_VALUES) KEY_VALUES(CriteriaSpecTypeConstants.KEY_VALUES);

  private final String displayName;

  CriteriaSpecType(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
