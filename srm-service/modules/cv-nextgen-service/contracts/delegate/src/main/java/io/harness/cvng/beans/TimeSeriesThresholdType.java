/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

public enum TimeSeriesThresholdType {
  ACT_WHEN_LOWER("<"),
  ACT_WHEN_HIGHER(">");

  private String symbol;

  TimeSeriesThresholdType(String symbol) {
    this.symbol = symbol;
  }

  public static TimeSeriesThresholdType valueFromSymbol(String symbol) {
    for (TimeSeriesThresholdType timeSeriesThresholdType : TimeSeriesThresholdType.values()) {
      if (timeSeriesThresholdType.symbol.equals(symbol.trim())) {
        return timeSeriesThresholdType;
      }
    }

    throw new IllegalArgumentException("Invalid type symbol");
  }
}
