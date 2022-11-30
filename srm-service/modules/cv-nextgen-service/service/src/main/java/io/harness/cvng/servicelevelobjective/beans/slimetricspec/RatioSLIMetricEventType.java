/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slimetricspec;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RatioSLIMetricEventType {
  @JsonProperty("Good") GOOD,
  @JsonProperty("Bad") BAD;

  public double computeSLIMetricValue(double metricValue1, double metricValue2) {
    double metricValue = (metricValue1 / metricValue2) * 100;
    if (this.equals(RatioSLIMetricEventType.BAD)) {
      metricValue = (1 - (metricValue1 / metricValue2)) * 100;
    }
    return metricValue;
  }
}
