/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.terraformcloudconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.CDP)
public enum TerraformCloudCredentialType {
  @JsonProperty(TerraformCloudConstants.API_TOKEN) API_TOKEN(TerraformCloudConstants.API_TOKEN);

  private final String displayName;

  TerraformCloudCredentialType(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }

  public static TerraformCloudCredentialType fromString(String typeEnum) {
    for (TerraformCloudCredentialType enumValue : TerraformCloudCredentialType.values()) {
      if (enumValue.displayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
