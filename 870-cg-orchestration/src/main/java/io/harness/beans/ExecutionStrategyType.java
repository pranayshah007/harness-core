/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutionStrategyType {
  @JsonProperty("Basic") BASIC("Basic"),
  @JsonProperty("Canary") CANARY("Canary"),
  @JsonProperty("BlueGreen") BLUE_GREEN("BlueGreen"),
  @JsonProperty("Rolling") ROLLING("Rolling"),
  @JsonProperty("Default") DEFAULT("Default"),
  @JsonProperty("GitOps") GITOPS("GitOps");

  private String displayName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ExecutionStrategyType getExecutionStrategy(@JsonProperty("type") String displayName) {
    for (ExecutionStrategyType executionStrategyType : ExecutionStrategyType.values()) {
      if (executionStrategyType.displayName.equalsIgnoreCase(displayName)) {
        return executionStrategyType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  ExecutionStrategyType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  public static ExecutionStrategyType fromString(final String s) {
    return ExecutionStrategyType.getExecutionStrategy(s);
  }
}
