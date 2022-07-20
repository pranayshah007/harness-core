/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.EntitySubtype;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(PL)
public enum SettingValueType implements EntitySubtype {
  @JsonProperty("String") STRING("String"),
  @JsonProperty("Boolean") BOOLEAN("Boolean"),
  @JsonProperty("Number") NUMBER("Number");

  private final String displayName;

  SettingValueType(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static SettingValueType getSettingValueType(@JsonProperty("valueType") String displayName) {
    for (SettingValueType settingValueType : SettingValueType.values()) {
      if (settingValueType.displayName.equalsIgnoreCase(displayName)) {
        return settingValueType;
      }
    }
    throw new IllegalArgumentException(String.format("Invalid setting value type: %s", displayName));
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
