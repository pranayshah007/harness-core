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
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.repositories.instancestatsiterator.InstanceStatsIteratorRepository;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancestats.InstanceStatsService;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
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
  private static final int SYNC_INTERVAL_MINUTES = 30;
  private static final long SYNC_INTERVAL = TimeUnit.MINUTES.toMinutes(SYNC_INTERVAL_MINUTES);
  private static final long RELAXED_SYNC_INTERVAL_IN_MILLIS = 15 * 60 * 1000L;
  private static final int MAX_CALLS_PER_SERVICE = 60 / SYNC_INTERVAL_MINUTES * 24;

  private InstanceStatsService instanceStatsService;
  private InstanceService instanceService;
  private UsageMetricsEventPublisher usageMetricsEventPublisher;
  private HPersistence persistence;
  private InstanceStatsIteratorRepository instanceStatsIteratorRepository;

  @Override
  public boolean createStats(String accountId) {
    // Currently we are fetching last snapshot for each service separately in the loop.
    // We explored other options as well, these can be revisited if we see any perf issues,
    // - group by org, project, service - takes more than a minute
    // - IN / UNION query for each org, project, service combination
    // - store metadata and latest time snapshot in a separate table
    boolean ranAtLeastOnce = false;
    log.info("Collect and publish stats. Account: {}", accountId);
    try (HIterator<ServiceEntity> services = new HIterator<>(getFetchServicesQuery(accountId).fetch())) {
      while (services.hasNext()) {
        ServiceEntity service = services.next();
        try {
          Instant lastSnapshot = instanceStatsService.getLastSnapshotTime(
              accountId, service.getOrgIdentifier(), service.getProjectIdentifier(), service.getIdentifier());
          if (null == lastSnapshot) {
            boolean success = createStats(accountId, service.getOrgIdentifier(), service.getProjectIdentifier(),
                service.getIdentifier(), alignedWithMinute(Instant.now(), SYNC_INTERVAL_MINUTES));
            ranAtLeastOnce = ranAtLeastOnce || success;
          } else {
            SnapshotTimeProvider snapshotTimeProvider = new SnapshotTimeProvider(lastSnapshot, SYNC_INTERVAL);
            int callsPerService = 0;
            while (snapshotTimeProvider.hasNext()) {
              if (callsPerService >= MAX_CALLS_PER_SERVICE) {
                log.warn(
                    "Tried publishing {} stats for service {}. Pending backlog will be published in the next iteration",
                    MAX_CALLS_PER_SERVICE, service.getIdentifier());
                instanceStatsIteratorRepository.updateTimestampForIterator(accountId, service.getOrgIdentifier(),
                    service.getProjectIdentifier(), service.getIdentifier(),
                    snapshotTimeProvider.currentlyAt().toEpochMilli());
                break;
              }
              Instant nextTs = snapshotTimeProvider.next();
              if (nextTs == null) {
                throw new IllegalStateException(
                    "nextTs is null even though hasNext() returned true. Shouldn't be possible");
              }
              boolean success = createStats(accountId, service.getOrgIdentifier(), service.getProjectIdentifier(),
                  service.getIdentifier(), nextTs);
              ranAtLeastOnce = ranAtLeastOnce || success;
              ++callsPerService;
            }
          }
        } catch (Exception ex) {
          log.error("Could not create stats for service: {} (account: {}, org: {}, project: {})",
              service.getIdentifier(), service.getAccountId(), service.getOrgIdentifier(),
              service.getProjectIdentifier(), ex);
        }
      }
    }
    log.info("Published instance stats. Account: {}", accountId);

    return ranAtLeastOnce;
  }

  // ------------------------ PRIVATE METHODS -----------------------------

  private Query<ServiceEntity> getFetchServicesQuery(String accountId) {
    return persistence.createQuery(ServiceEntity.class)
        .filter(ServiceEntityKeys.accountId, accountId)
        .filter(ServiceEntityKeys.deleted, false)
        .project(ServiceEntityKeys.orgIdentifier, true)
        .project(ServiceEntityKeys.projectIdentifier, true)
        .project(ServiceEntityKeys.identifier, true);
  }

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
  private boolean createStats(String accountId, String orgId, String projectId, String serviceId, Instant instant) {
    List<InstanceDTO> instances;
    try {
      if (isRecentCollection(instant)) {
        instances =
            instanceService.getActiveInstancesByAccountOrgProjectAndService(accountId, orgId, projectId, serviceId, -1);
        long timestamp = Instant.now().toEpochMilli();
        usageMetricsEventPublisher.publishInstanceStatsTimeSeries(accountId, timestamp, instances);
        instanceStatsIteratorRepository.updateTimestampForIterator(accountId, orgId, projectId, serviceId, timestamp);
      } else {
        instances = instanceService.getActiveInstancesByAccountOrgProjectAndService(
            accountId, orgId, projectId, serviceId, instant.toEpochMilli());
        usageMetricsEventPublisher.publishInstanceStatsTimeSeries(accountId, instant.toEpochMilli(), instances);
        instanceStatsIteratorRepository.updateTimestampForIterator(
            accountId, orgId, projectId, serviceId, instant.toEpochMilli());
      }
      return true;
    } catch (Exception e) {
      log.error("Unable to publish instance stats for service: {} (account: {}, org: {}, project: {}) at {}", serviceId,
          accountId, orgId, projectId, instant, e);
      return false;
    }
  }

  private boolean isRecentCollection(Instant instant) {
    return Instant.now().toEpochMilli() - instant.toEpochMilli() < RELAXED_SYNC_INTERVAL_IN_MILLIS;
  }
}
