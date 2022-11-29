/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.cvng.core.utils.DateTimeUtils;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class ServiceGuardDataCollectionTask extends DataCollectionTask {
  public static final Duration SERVICE_GUARD_MAX_DATA_COLLECTION_DURATION = Duration.ofHours(2);
  private static final List<Duration> RETRY_WAIT_DURATIONS =
      Lists.newArrayList(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofMinutes(5),
          Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(3));
  @Override
  public boolean shouldCreateNextTask() {
    return true;
  }

  @Override
  public boolean eligibleForRetry(Instant currentTime) {
    return getStartTime().isAfter(getDataCollectionPastTimeCutoff(currentTime));
  }

  @Override
  public Instant getNextValidAfter(Instant currentTime) {
    return currentTime.plus(RETRY_WAIT_DURATIONS.get(Math.min(this.getRetryCount(), RETRY_WAIT_DURATIONS.size() - 1)));
  }

  public Instant getDataCollectionPastTimeCutoff(Instant currentTime) {
    return DateTimeUtils.roundDownTo5MinBoundary(currentTime).minus(SERVICE_GUARD_MAX_DATA_COLLECTION_DURATION);
  }
}
