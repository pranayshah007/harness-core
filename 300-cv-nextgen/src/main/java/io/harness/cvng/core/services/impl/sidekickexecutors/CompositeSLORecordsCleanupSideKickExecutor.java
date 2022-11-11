/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import io.harness.cvng.core.beans.sidekick.CompositeSLORecordsCleanupSideKickData;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord.CompositeSLORecordKeys;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CompositeSLORecordsCleanupSideKickExecutor
    implements SideKickExecutor<CompositeSLORecordsCleanupSideKickData> {
  private static final List<Duration> RETRY_WAIT_DURATIONS =
      Lists.newArrayList(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10),
          Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(90),
          Duration.ofMinutes(120), Duration.ofMinutes(360));
  @Inject private Clock clock;
  @Inject private HPersistence hPersistence;
  @VisibleForTesting private long BATCH_SIZE_FOR_DELETION = TimeUnit.DAYS.toMinutes(3);

  @Override
  public void execute(CompositeSLORecordsCleanupSideKickData sideKickInfo) {
    log.info("SidekickInfo {}", sideKickInfo);
    String compositeSLOId = sideKickInfo.getSloId();
    int sloVersion = sideKickInfo.getSloVersion();
    long afterStartTime = sideKickInfo.getAfterStartTime();
    if (StringUtils.isNotBlank(compositeSLOId)) {
      log.info("Triggering cleanup for Composite SLO Records for sloId {}", compositeSLOId);
      long endTime = TimeUnit.MILLISECONDS.toMinutes(clock.millis());
      for (long startTime = afterStartTime; startTime <= endTime;) {
        long currentEndTime = startTime + getBatchSizeForDeletion();
        hPersistence.delete(hPersistence.createQuery(CompositeSLORecord.class)
                                .filter(CompositeSLORecordKeys.sloId, compositeSLOId)
                                .filter(CompositeSLORecordKeys.sloVersion, sloVersion)
                                .field(CompositeSLORecordKeys.epochMinute)
                                .greaterThanOrEq(startTime)
                                .field(CompositeSLORecordKeys.epochMinute)
                                .lessThanOrEq(currentEndTime));
        startTime = currentEndTime + 1;
      }
      log.info("Cleanup complete for Composite SLO Records for sloId {}", compositeSLOId);
    }
  }

  long getBatchSizeForDeletion() {
    return BATCH_SIZE_FOR_DELETION;
  }

  @Override
  public RetryData shouldRetry(int lastRetryCount) {
    return RetryData.builder()
        .shouldRetry(true)
        .nextRetryTime(getNextValidAfter(lastRetryCount, clock.instant()))
        .build();
  }

  public Instant getNextValidAfter(int retryCount, Instant currentTime) {
    return currentTime.plus(RETRY_WAIT_DURATIONS.get(Math.min(retryCount, RETRY_WAIT_DURATIONS.size() - 1)));
  }
}
