/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ActiveInstanceIterator {
  private static int OFFSET_DAYS = 50 * 365;

  public static Instant getActiveInstanceIteratorFromStartTime(Instant startTime) {
    return startTime.plus(OFFSET_DAYS, ChronoUnit.DAYS).plus(getOffsetValue(0, 100), ChronoUnit.MILLIS);
  }

  public static Instant getActiveInstanceIteratorFromStopTime(Instant stopTime) {
    return stopTime.plus(getOffsetValue(0, 100), ChronoUnit.MILLIS);
  }

  public static int getOffsetValue(int min, int max) {
    return (int) ((Math.random() * (max - min)) + min);
  }
}
