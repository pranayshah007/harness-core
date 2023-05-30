/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.rancher;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@OwnedBy(HarnessTeam.CDP)
@Getter
@AllArgsConstructor
public enum RancherConfigType {
  @JsonProperty(RancherConstants.MANUAL_CONFIG) MANUAL_CONFIG(RancherConstants.MANUAL_CONFIG);

  private final String displayName;

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }

  public static RancherConfigType fromString(String typeEnum) {
    for (RancherConfigType enumValue : RancherConfigType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException(format("Invalid value for rancher config type: %s", typeEnum));
  }
}
