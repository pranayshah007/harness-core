/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@OwnedBy(CDP)
public enum TerraformCloudCliStepConfigurationType implements TerraformStepConfigurationEnumInterface {
  @JsonProperty("Inline") INLINE("Inline");

  @Getter private final String displayName;
  TerraformCloudCliStepConfigurationType(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TerraformCloudCliStepConfigurationType getConfigurationType(String displayName) {
    for (TerraformCloudCliStepConfigurationType value : TerraformCloudCliStepConfigurationType.values()) {
      if (value.displayName.equalsIgnoreCase(displayName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  public String getDisplayName() {
    return "Inline";
  }
}
