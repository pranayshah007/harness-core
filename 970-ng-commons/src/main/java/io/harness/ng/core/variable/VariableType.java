/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.EntitySubtype;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(PL)
public enum VariableType implements EntitySubtype {
  @JsonProperty("String") STRING("String"),
  @JsonProperty("Secret") SECRET("Secret");

  private final String displayName;

  VariableType(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static VariableType getVariableType(@JsonProperty("type") String displayName) {
    for (VariableType variableType : VariableType.values()) {
      if (variableType.displayName.equalsIgnoreCase(displayName)) {
        return variableType;
      }
    }
    throw new IllegalArgumentException(String.format("Invalid variable type: %s", displayName));
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
