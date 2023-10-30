/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GcpCredentialType {
  @JsonProperty(GcpConstants.INHERIT_FROM_DELEGATE) INHERIT_FROM_DELEGATE(GcpConstants.INHERIT_FROM_DELEGATE),
  @JsonProperty(GcpConstants.MANUAL_CONFIG) MANUAL_CREDENTIALS(GcpConstants.MANUAL_CONFIG),
  @JsonProperty(GcpConstants.OIDC_AUTHENTICATION) OIDC_AUTHENTICATION(GcpConstants.OIDC_AUTHENTICATION);

  private final String displayName;

  GcpCredentialType(String displayName) {
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

  public static GcpCredentialType fromString(String typeEnum) {
    for (GcpCredentialType enumValue : GcpCredentialType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
