/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
public enum ARMScopeType {
  RESOURCE_GROUP("RESOURCE_GROUP"),
  SUBSCRIPTION("SUBSCRIPTION"),
  MANAGEMENT_GROUP("MANAGEMENT_GROUP"),
  TENANT("TENANT");

  ARMScopeType(String value) {
    this.value = value;
  }

  private final String value;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ARMScopeType fromString(final String value) {
    for (ARMScopeType type : ARMScopeType.values()) {
      if (type.toString().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format("Unrecognized ARM scope type, value: %s,", value));
  }

  @JsonValue
  @Override
  public String toString() {
    return this.value;
  }
}
