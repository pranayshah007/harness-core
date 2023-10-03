/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum EcsInstanceUnitType {
  @JsonProperty(EcsConstants.PERCENTAGE) PERCENTAGE(EcsConstants.PERCENTAGE),
  @JsonProperty(EcsConstants.COUNT) COUNT(EcsConstants.COUNT);

  private final String displayName;
  EcsInstanceUnitType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
