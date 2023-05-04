/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats.collector;

import io.harness.event.usagemetrics.UsageMetricsEventPublisher;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.Mapper;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.instance.DashboardStatisticsService;
import software.wings.service.intfc.instance.ServerlessDashboardService;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.service.intfc.instance.stats.ServerlessInstanceStatService;
import software.wings.service.intfc.instance.stats.collector.StatsCollector;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;

@ParametersAreNonnullByDefault
@Slf4j
public class StatsCollectorImpl implements StatsCollector {
  private static final int SYNC_INTERVAL_MINUTES = 10;
  private static final long SYNC_INTERVAL = TimeUnit.MINUTES.toMinutes(SYNC_INTERVAL_MINUTES);

  private static final int MAX_CALLS_PER_ACCOUNT = 60 / SYNC_INTERVAL_MINUTES * 6;

  private InstanceStatService statService;
  private ServerlessInstanceStatService serverlessInstanceStatService;
  private DashboardStatisticsService dashboardStatisticsService;
  private UsageMetricsEventPublisher usageMetricsEventPublisher;
  private ServerlessDashboardService serverlessDashboardService;

  @Inject
  public StatsCollectorImpl(DashboardStatisticsService dashboardStatisticsService, InstanceStatService statService,
      WingsPersistence persistence, UsageMetricsEventPublisher usageMetricsEventPublisher,
      ServerlessInstanceStatService serverlessInstanceStatService,
      ServerlessDashboardService serverlessDashboardService) {
    this.dashboardStatisticsService = dashboardStatisticsService;
    this.statService = statService;
    this.usageMetricsEventPublisher = usageMetricsEventPublisher;
    this.serverlessInstanceStatService = serverlessInstanceStatService;
    this.serverlessDashboardService = serverlessDashboardService;
  }

  @Override
  public boolean createStats(String accountId) {
    Instant lastSnapshot = statService.getLastSnapshotTime(accountId);
    if (null == lastSnapshot) {
      return createStats(accountId, alignedWithMinute(Instant.now(), 10));
    }

    SnapshotTimeProvider snapshotTimeProvider = new SnapshotTimeProvider(lastSnapshot, SYNC_INTERVAL);
    boolean ranAtLeastOnce = false;
    int instanceStatsCallsMade = 0;
    while (snapshotTimeProvider.hasNext()) {
      if (instanceStatsCallsMade >= MAX_CALLS_PER_ACCOUNT) {
        log.warn("Tried publishing {} stats for accountId {}. Pending backlog will be published in the next iteration",
            MAX_CALLS_PER_ACCOUNT, accountId);
        break;
      }
      Instant nextTs = snapshotTimeProvider.next();
      if (nextTs == null) {
        throw new IllegalStateException("nextTs is null even though hasNext() returned true. Shouldn't be possible");
      }
      boolean success = createStats(accountId, nextTs);
      ranAtLeastOnce = ranAtLeastOnce || success;
      instanceStatsCallsMade++;
    }

    return ranAtLeastOnce;
  }

  @Override
  public boolean createStatsAtIfMissing(String accountId, long timestamp) {
    Instant snapshotTimestamp = Instant.ofEpochMilli(timestamp);
    Instant lastSnapshotTimestamp = statService.getLastSnapshotTime(accountId);
    if (lastSnapshotTimestamp != null && lastSnapshotTimestamp.isAfter(snapshotTimestamp)) {
      log.info(
          "Instance stats snapshots already exist for account {}, lastSnapshotTimestamp: {}, snapshotTimestamp: {}",
          accountId, lastSnapshotTimestamp, snapshotTimestamp);
      return true;
    } else {
      log.info(
          "Start creating instance stats snapshot for account {}, lastSnapshotTimestamp: {}, snapshotTimestamp: {}",
          accountId, lastSnapshotTimestamp, snapshotTimestamp);
      boolean statsSaved = createStats(accountId, alignedWithMinute(snapshotTimestamp, SYNC_INTERVAL_MINUTES));
      if (statsSaved) {
        log.info("Instance stats snapshot is created successfully for account {}", accountId);
      }
      if (!statsSaved) {
        log.info("Failed to create instance stats for account {}", accountId);
      }

      return statsSaved;
    }
  }

  @Override
  public boolean createServerlessStats(String accountId) {
    Instant lastSnapshot = serverlessInstanceStatService.getLastSnapshotTime(accountId);
    if (null == lastSnapshot) {
      return createServerlessStats(accountId, alignedWithMinute(Instant.now(), SYNC_INTERVAL_MINUTES));
    }

    SnapshotTimeProvider snapshotTimeProvider = new SnapshotTimeProvider(lastSnapshot, SYNC_INTERVAL);
    boolean ranAtLeastOnce = false;
    int instanceStatsCallsMade = 0;
    while (snapshotTimeProvider.hasNext()) {
      if (instanceStatsCallsMade >= MAX_CALLS_PER_ACCOUNT) {
        log.warn(
            "Tried publishing {} serverless stats for accountId {}. Pending backlog will be published in the next iteration",
            MAX_CALLS_PER_ACCOUNT, accountId);
        break;
      }
      Instant nextTs = snapshotTimeProvider.next();
      if (nextTs == null) {
        throw new IllegalStateException("nextTs is null even though hasNext() returned true. Shouldn't be possible");
      }
      boolean success = createServerlessStats(accountId, nextTs);
      ranAtLeastOnce = ranAtLeastOnce || success;
      instanceStatsCallsMade++;
    }

    return ranAtLeastOnce;
  }

  // see tests for behaviour
  static Instant alignedWithMinute(Instant instant, int minuteToTruncateTo) {
    if (LocalDateTime.ofInstant(instant, ZoneOffset.UTC).getMinute() % minuteToTruncateTo == 0) {
      return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    Instant value = instant.truncatedTo(ChronoUnit.HOURS);
    while (!value.plus(minuteToTruncateTo, ChronoUnit.MINUTES).isAfter(instant)) {
      value = value.plus(minuteToTruncateTo, ChronoUnit.MINUTES);
    }

    return value;
  }

  boolean createStats(String accountId, Instant timestamp) {
    List<Instance> instances = null;
    try {
      InstanceStatsSnapshot stats;
      int count = 0;

      if (dashboardStatisticsService.isInstanceConsumerEnabled(accountId)) {
        InstanceMapperConsumer instanceMapper = new InstanceMapperConsumer(timestamp, accountId);
        count = dashboardStatisticsService.consumeAppInstancesForAccount(
            accountId, timestamp.toEpochMilli(), instanceMapper);
        log.info("Fetched instances. Count: {}, Account: {}, Time: {}", count, accountId, timestamp);

        stats = instanceMapper.map(null);
      } else {
        instances = dashboardStatisticsService.getAppInstancesForAccount(accountId, timestamp.toEpochMilli());
        log.info("Fetched instances. Count: {}, Account: {}, Time: {}", instances.size(), accountId, timestamp);

        Mapper<Collection<Instance>, InstanceStatsSnapshot> instanceMapper = new InstanceMapper(timestamp, accountId);
        stats = instanceMapper.map(instances);
      }
      boolean saved = statService.save(stats);
      if (!saved) {
        log.error("Error saving instance usage stats. AccountId: {}, Timestamp: {}", accountId, timestamp);
      }

      return saved;

    } catch (Exception e) {
      log.error("Could not create stats. AccountId: {}", accountId, e);
      return false;
    } finally {
      try {
        usageMetricsEventPublisher.publishInstanceTimeSeries(accountId, timestamp.toEpochMilli(), instances);
      } catch (Exception e) {
        log.error(
            "Error while publishing metrics for account {}, timestamp {}", accountId, timestamp.toEpochMilli(), e);
      }
    }
  }

  private boolean createServerlessStats(String accountId, Instant timesamp) {
    List<ServerlessInstance> instances = null;
    try {
      instances = serverlessDashboardService.getAppInstancesForAccount(accountId, timesamp.toEpochMilli());
      log.info("Fetched Serverless instances. Count: {}, Account: {}, Time: {}", instances.size(), accountId, timesamp);

      Mapper<Collection<ServerlessInstance>, ServerlessInstanceStats> instanceMapper =
          new ServerlessInstanceMapper(timesamp, accountId);

      ServerlessInstanceStats stats = instanceMapper.map(instances);
      boolean saved = serverlessInstanceStatService.save(stats);
      if (!saved) {
        log.error("Error saving instance usage stats. AccountId: {}, Timestamp: {}", accountId, timesamp);
      }

      return saved;

    } catch (Exception e) {
      log.error("Could not create stats. AccountId: {}", accountId, e);
      return false;
    }
  }
}
