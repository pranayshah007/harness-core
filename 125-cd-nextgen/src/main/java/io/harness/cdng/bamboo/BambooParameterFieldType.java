/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CDC)
public enum BambooParameterFieldType {
  @JsonProperty("String") STRING("String"),
  @JsonProperty("Number") NUMBER("Number");

  private final String displayName;
  BambooParameterFieldType(String displayName) {
    this.displayName = displayName;
  }

  @JsonIgnore
  public String getDisplayName() {
    return this.displayName;
  }
}
