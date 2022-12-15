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
class BatchJobScheduleTimeProvider {
  private Instant currentlyAt;
  private Instant endTime;
  private long duration;
  private ChronoUnit chronoUnit;

  BatchJobScheduleTimeProvider(Instant lastSyncTime, Instant endTime, long duration, ChronoUnit chronoUnit) {
    Objects.requireNonNull(lastSyncTime, "lastSyncTime timestamp is non-null");
    Objects.requireNonNull(endTime, "endTime  timestamp is non-null");
    Preconditions.checkArgument(duration > 0, "duration should be positive number");

    this.currentlyAt = lastSyncTime;
    this.endTime = endTime;
    this.duration = duration;
    this.chronoUnit = chronoUnit;
  }

  @Nullable
  public Instant next() {
    if (hasNext()) {
      currentlyAt = addDurationToCurrentInstant();
      return currentlyAt;
    }

    return null;
  }

  public boolean hasNext() {
    return addDurationToCurrentInstant().isBefore(endTime);
  }

  private Instant addDurationToCurrentInstant() {
    return currentlyAt.plus(duration, chronoUnit).truncatedTo(chronoUnit);
  }
}
