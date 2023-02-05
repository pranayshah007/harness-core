/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.beans.TimeSeriesThresholdActionType;

public enum MetricThresholdType {
  FAIL_FAST,
  IGNORE;

  public static MetricThresholdType fromTimeSeriesThresholdActionType(
      TimeSeriesThresholdActionType timeSeriesThresholdActionType) {
    switch (timeSeriesThresholdActionType) {
      case IGNORE:
        return IGNORE;
      case FAIL:
        return FAIL_FAST;
      default:
        throw new IllegalStateException("Unhanded TimeSeriesThresholdActionType " + timeSeriesThresholdActionType);
    }
  }
}
