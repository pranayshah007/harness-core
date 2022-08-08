/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.CDP)
public enum AzureManagedIdentityType {
  @JsonProperty(AzureConstants.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
  SYSTEM_ASSIGNED_MANAGED_IDENTITY(AzureConstants.SYSTEM_ASSIGNED_MANAGED_IDENTITY),
  @JsonProperty(AzureConstants.USER_ASSIGNED_MANAGED_IDENTITY)
  USER_ASSIGNED_MANAGED_IDENTITY(AzureConstants.USER_ASSIGNED_MANAGED_IDENTITY);

  private final String displayName;

  AzureManagedIdentityType(String displayName) {
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

  public static AzureManagedIdentityType fromString(String typeEnum) {
    for (AzureManagedIdentityType enumValue : AzureManagedIdentityType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
