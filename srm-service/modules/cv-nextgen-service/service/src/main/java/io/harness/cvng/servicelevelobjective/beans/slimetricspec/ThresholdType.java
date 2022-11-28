/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slimetricspec;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ThresholdType {
  @JsonProperty(">") GREATER_THAN,
  @JsonProperty("<") LESS_THAN,
  @JsonProperty(">=") GREATER_THAN_EQUAL_TO,
  @JsonProperty("<=") LESS_THAN_EQUAL_TO;

  public boolean compute(Double inputValue, Double comparedTo) {
    switch (this) {
      case GREATER_THAN:
        return inputValue > comparedTo;
      case LESS_THAN:
        return inputValue < comparedTo;
      case GREATER_THAN_EQUAL_TO:
        return inputValue >= comparedTo;
      case LESS_THAN_EQUAL_TO:
        return inputValue <= comparedTo;
      default:
        throw new IllegalArgumentException("Invalid state");
    }
  }
}
