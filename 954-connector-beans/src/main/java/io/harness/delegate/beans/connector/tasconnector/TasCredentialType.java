/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.beans.connector.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.CDP)
public enum TasCredentialType {
  @JsonProperty(TasConstants.MANUAL_CONFIG) MANUAL_CREDENTIALS(TasConstants.MANUAL_CONFIG, true);

  private final String displayName;
  private final boolean decryptable;

  TasCredentialType(String displayName, boolean decryptable) {
    this.displayName = displayName;
    this.decryptable = decryptable;
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

  @JsonIgnore
  public boolean isDecryptable() {
    return decryptable;
  }
  public static TasCredentialType fromString(String typeEnum) {
    for (TasCredentialType enumValue : TasCredentialType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
