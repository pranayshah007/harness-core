/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.pcf.PcfResizeStrategyType")
public enum PcfResizeStrategyType {
  @JsonProperty(PcfConstants.RESIZE_NEW_FIRST) RESIZE_NEW_FIRST(PcfConstants.RESIZE_NEW_FIRST),
  @JsonProperty(PcfConstants.DOWNSIZE_OLD_FIRST) DOWNSIZE_OLD_FIRST(PcfConstants.DOWNSIZE_OLD_FIRST);

  private final String displayName;

  PcfResizeStrategyType(String displayName) {
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

  public static PcfResizeStrategyType fromString(String typeEnum) {
    for (PcfResizeStrategyType enumValue : PcfResizeStrategyType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
