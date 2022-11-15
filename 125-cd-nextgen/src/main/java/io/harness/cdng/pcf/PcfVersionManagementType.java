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
@RecasterAlias("io.harness.cdng.pcf.PcfInstanceCount")
public enum PcfVersionManagementType {
  @JsonProperty(PcfConstants.INCREMENTAL_VERSIONING) INCREMENTAL_VERSIONING(PcfConstants.INCREMENTAL_VERSIONING),
  @JsonProperty(PcfConstants.APP_NAME_WITH_VERSION_HISTORY)
  APP_NAME_WITH_VERSION_HISTORY(PcfConstants.APP_NAME_WITH_VERSION_HISTORY);

  private final String displayName;

  PcfVersionManagementType(String displayName) {
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

  public static PcfVersionManagementType fromString(String typeEnum) {
    for (PcfVersionManagementType enumValue : PcfVersionManagementType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
