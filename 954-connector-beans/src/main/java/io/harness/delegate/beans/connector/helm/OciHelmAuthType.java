/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.helm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OciHelmAuthType {
  @JsonProperty(HelmConstants.USERNAME_PASSWORD) USER_PASSWORD(HelmConstants.USERNAME_PASSWORD),
  @JsonProperty(HelmConstants.ANONYMOUS) ANONYMOUS(HelmConstants.ANONYMOUS);

  private final String displayName;

  OciHelmAuthType(String displayName) {
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

  public static OciHelmAuthType fromString(String typeEnum) {
    for (OciHelmAuthType enumValue : OciHelmAuthType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
