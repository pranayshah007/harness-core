/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public enum SLIMissingDataType {
  @JsonProperty("Good") GOOD,
  @JsonProperty("Bad") BAD,
  @JsonProperty("Ignore") IGNORE;

  public SLIValue calculateSLIValue(long goodCount, long badCount, long totalMinutes) {
    return calculateSLIValue(goodCount, badCount, totalMinutes, 0L);
  }

  public SLIValue calculateSLIValue(long goodCount, long badCount, long totalMinutes, long disabledMinutes) {
    Preconditions.checkState(totalMinutes != 0);
    long missingDataCount = totalMinutes - (goodCount + badCount) - disabledMinutes;
    switch (this) {
      case GOOD:
        return SLIValue.builder()
            .goodCount(goodCount + missingDataCount)
            .badCount(badCount)
            .total(totalMinutes - disabledMinutes)
            .build();
      case BAD:
        return SLIValue.builder()
            .goodCount(goodCount)
            .badCount(badCount + missingDataCount)
            .total(totalMinutes - disabledMinutes)
            .build();
      case IGNORE:
        return SLIValue.builder().goodCount(goodCount).badCount(badCount).total(goodCount + badCount).build();
      default:
        throw new IllegalStateException("Unhanded SLIMissingDataType " + this);
    }
  }
}
