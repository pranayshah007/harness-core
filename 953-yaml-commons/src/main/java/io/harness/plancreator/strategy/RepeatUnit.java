/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepeatUnit {
  @JsonProperty("Percentage") PERCENTAGE("Percentage"),
  @JsonProperty("Count") COUNT("Count");

  String displayName;

  RepeatUnit(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static RepeatUnit fromString(String displayName) {
    for (RepeatUnit repeatUnit : RepeatUnit.values()) {
      if (repeatUnit.getDisplayName().equalsIgnoreCase(displayName)) {
        return repeatUnit;
      }
      if (repeatUnit.name().equalsIgnoreCase(displayName)) {
        return repeatUnit;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  @JsonValue
  public String toString() {
    return this.displayName;
  }
}
