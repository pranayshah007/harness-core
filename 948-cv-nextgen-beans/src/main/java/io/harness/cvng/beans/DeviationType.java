/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.util.List;

public enum DeviationType {
  HIGHER_IS_RISKY,
  LOWER_IS_RISKY,
  BOTH_ARE_RISKY;

  public static DeviationType getDeviationType(List<TimeSeriesThresholdType> thresholdTypes) {
    if (isNotEmpty(thresholdTypes)) {
      if (thresholdTypes.size() == 2) {
        return DeviationType.BOTH_ARE_RISKY;
      } else if (thresholdTypes.contains(TimeSeriesThresholdType.ACT_WHEN_HIGHER)) {
        return DeviationType.HIGHER_IS_RISKY;
      } else {
        return DeviationType.LOWER_IS_RISKY;
      }
    }
    return null;
  }
}
