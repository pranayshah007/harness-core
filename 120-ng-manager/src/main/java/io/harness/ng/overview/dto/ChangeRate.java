/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import static java.lang.Math.abs;

import lombok.Value;

@Value
public class ChangeRate {
  public enum Trend {
    UP_TREND,
    DOWN_TREND,
    NO_CHANGE,
    INVALID;
  }
  Double percentChange;
  Trend trend;

  public ChangeRate(Double percentChange) {
    if (percentChange != null) {
      if (percentChange < 0) {
        this.trend = Trend.DOWN_TREND;
      } else if (percentChange > 0) {
        this.trend = Trend.UP_TREND;
      } else {
        this.trend = Trend.NO_CHANGE;
      }
      this.percentChange = abs(percentChange);
    } else {
      this.percentChange = percentChange;
      this.trend = Trend.INVALID;
    }
  }
}
