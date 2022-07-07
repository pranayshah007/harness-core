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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@FieldDefaults(level = AccessLevel.PRIVATE)
public enum Operator {
  @JsonProperty("equals") EQ("equals"),
  @JsonProperty("not equals") NOT_EQ("not equals"),
  @JsonProperty("in") IN("in"),
  @JsonProperty("not in") NOT_IN("not in");

  @Getter final String displayName;

  Operator(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
