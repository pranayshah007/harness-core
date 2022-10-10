/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Given the last sync time, it provides next times since then for which to run batch job.
 */
@ParametersAreNonnullByDefault
class BatchJobScheduleTimeProviderDesc {
  private Instant currentlyAt;
  private Instant startTime;
  private long duration;
  private ChronoUnit chronoUnit;

  BatchJobScheduleTimeProviderDesc(Instant lastSyncTime, Instant startTime, long duration, ChronoUnit chronoUnit) {
    Objects.requireNonNull(lastSyncTime, "lastSyncTime timestamp is non-null");
    Objects.requireNonNull(startTime, "startTime  timestamp is non-null");
    Preconditions.checkArgument(duration > 0, "duration should be positive number");

    this.currentlyAt = lastSyncTime;
    this.startTime = startTime;
    this.duration = duration;
    this.chronoUnit = chronoUnit;
  }

  @Nullable
  public Instant next() {
    if (hasNext()) {
      currentlyAt = removeDurationToCurrentInstant();
      return currentlyAt;
    }

    return null;
  }

  public boolean hasNext() {
    return removeDurationToCurrentInstant().isAfter(startTime);
  }

  private Instant removeDurationToCurrentInstant() {
    return currentlyAt.minus(duration, chronoUnit).truncatedTo(chronoUnit);
  }
}
