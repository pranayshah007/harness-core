/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.stats.statscollector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.helper.SnapshotTimeProvider;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancestats.InstanceStatsService;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DX)
public class InstanceStatsCollectorImpl implements StatsCollector {
  private static final int SYNC_INTERVAL_MINUTES = 10;
  private static final long SYNC_INTERVAL = TimeUnit.MINUTES.toMinutes(SYNC_INTERVAL_MINUTES);

  private InstanceStatsService instanceStatsService;
  private InstanceService instanceService;
  private UsageMetricsEventPublisher usageMetricsEventPublisher;

  @Override
  public boolean createStats(String accountId) {
    Instant lastSnapshot = instanceStatsService.getLastSnapshotTime(accountId);
    if (null == lastSnapshot) {
      return createStats(accountId, alignedWithMinute(Instant.now(), SYNC_INTERVAL_MINUTES));
    }

    SnapshotTimeProvider snapshotTimeProvider = new SnapshotTimeProvider(lastSnapshot, SYNC_INTERVAL);
    boolean ranAtLeastOnce = false;
    while (snapshotTimeProvider.hasNext()) {
      Instant nextTs = snapshotTimeProvider.next();
      if (nextTs == null) {
        throw new IllegalStateException("nextTs is null even though hasNext() returned true. Shouldn't be possible");
      }
      boolean success = createStats(accountId, nextTs);
      ranAtLeastOnce = ranAtLeastOnce || success;
    }

    return ranAtLeastOnce;
  }

  // ------------------------ PRIVATE METHODS -----------------------------

  private Instant alignedWithMinute(Instant instant, int minuteToTruncateTo) {
    if (LocalDateTime.ofInstant(instant, ZoneOffset.UTC).getMinute() % minuteToTruncateTo == 0) {
      return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    Instant value = instant.truncatedTo(ChronoUnit.HOURS);
    while (!value.plus(minuteToTruncateTo, ChronoUnit.MINUTES).isAfter(instant)) {
      value = value.plus(minuteToTruncateTo, ChronoUnit.MINUTES);
    }

    return value;
  }

  private boolean createStats(String accountId, Instant instant) {
    List<InstanceDTO> instances = null;
    try {
      instances = instanceService.getActiveInstancesByAccount(accountId, instant.toEpochMilli());
      log.info("Fetched instances. Count: {}, Account: {}, Time: {}", instances.size(), accountId, instant);

      usageMetricsEventPublisher.publishInstanceStatsTimeSeries(accountId, instant.toEpochMilli(), instances);
      return true;
    } catch (Exception e) {
      log.error("Unable to publish instance stats for Account [{}] with exception {}", accountId, e);
      return false;
    }
  }
}
