/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.when.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@TargetModule(value = HarnessModule._889_YAML_COMMONS)
@OwnedBy(PIPELINE)
public enum WhenConditionStatus {
  @JsonProperty(WhenConditionConstants.SUCCESS) SUCCESS(WhenConditionConstants.SUCCESS),
  @JsonProperty(WhenConditionConstants.FAILURE) FAILURE(WhenConditionConstants.FAILURE),
  @JsonProperty(WhenConditionConstants.ALL) ALL(WhenConditionConstants.ALL);

  private final String displayName;

  WhenConditionStatus(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }
  @Override
  public String toString() {
    return displayName;
  }

  public static WhenConditionStatus getWhenConditionStatus(String displayName) {
    for (WhenConditionStatus value : WhenConditionStatus.values()) {
      if (value.getDisplayName().equals(displayName)) {
        return value;
      }
    }
    throw new InvalidArgumentsException("Invalid value: " + displayName);
  }
}
