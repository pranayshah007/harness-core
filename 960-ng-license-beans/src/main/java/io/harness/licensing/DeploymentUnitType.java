/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HarnessTeam.GTM)
public enum DeploymentUnitType {
  @JsonProperty("SERVICE") SERVICE,
  @JsonProperty("FUNCTION") FUNCTION;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static DeploymentUnitType fromString(String type) {
    for (DeploymentUnitType typeEnum : DeploymentUnitType.values()) {
      if (typeEnum.name().equalsIgnoreCase(type)) {
        return typeEnum;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + type);
  }
}
