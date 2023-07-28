/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AggregationType {
  @JsonProperty("average") AVERAGE("average"),
  @JsonProperty("maximum") MAXIMUM("maximum"),
  @JsonProperty("minimum") MINIMUM("minimum"),
  @JsonProperty("total") TOTAL("total"),
  @JsonProperty("count") COUNT("count");

  private String value;

  @Override
  public String toString() {
    return this.value;
  }
}
