/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@OwnedBy(CDP)
public enum TerraformStepConfigurationType implements TerraformStepConfigurationEnumInterface {
  @JsonProperty("Inline") INLINE("Inline"),
  @JsonProperty("InheritFromPlan") INHERIT_FROM_PLAN("InheritFromPlan"),
  @JsonProperty("InheritFromApply") INHERIT_FROM_APPLY("InheritFromApply");

  @Getter private final String displayName;
  TerraformStepConfigurationType(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TerraformStepConfigurationType getConfigurationType(String displayName) {
    for (TerraformStepConfigurationType value : TerraformStepConfigurationType.values()) {
      if (value.displayName.equalsIgnoreCase(displayName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  public String getDisplayName() {
    return displayName;
  }
}
