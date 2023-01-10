/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.CDP)
public enum TasResizeStrategyType {
  @JsonProperty(TasConstants.UPSCALE_NEW_FIRST) UPSCALE_NEW_FIRST(TasConstants.UPSCALE_NEW_FIRST),
  @JsonProperty(TasConstants.DOWNSCALE_OLD_FIRST) DOWNSCALE_OLD_FIRST(TasConstants.DOWNSCALE_OLD_FIRST);

  private final String displayName;

  TasResizeStrategyType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }

  public static TasResizeStrategyType fromString(String typeEnum) {
    for (TasResizeStrategyType enumValue : TasResizeStrategyType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}