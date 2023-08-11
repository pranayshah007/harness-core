/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats;

import io.harness.eraro.ErrorCode;
import io.harness.exception.NoResultFoundException;
import io.harness.persistence.HIterator;

import software.wings.beans.User;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats.ServerlessInstanceStatsKeys;
import software.wings.dl.WingsMongoPersistence;
import software.wings.resources.stats.model.ServerlessInstanceTimeline;
import software.wings.resources.stats.rbac.TimelineRbacFilters;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.instance.ServerlessDashboardService;
import software.wings.service.intfc.instance.stats.ServerlessInstanceStatService;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
public class ServerlessInstanceStatServiceImpl implements ServerlessInstanceStatService {
  public static final String TIMESTAMP = "timestamp";
  @Inject private WingsMongoPersistence persistence;
  @Inject private ServerlessDashboardService serverlessDashboardService;
  @Inject private AppService appService;
  @Inject private UserService userService;

  @Override
  public boolean save(ServerlessInstanceStats stats) {
    String id = persistence.save(stats);

    if (null == id) {
      log.error("Could not save instance stats. Stats: {}", stats);
      return false;
    }

    log.info("Saved stats. Time: {}, Account: {}, ID: {} ", stats.getTimestamp(), stats.getAccountId(), id);
    return true;
  }

  @Override
  @Nullable
  public Instant getLastSnapshotTime(@NotNull String accountId) {
    FindOptions options = persistence.analyticNodePreferenceOptions();
    options.limit(1);

    List<ServerlessInstanceStats> snapshots = persistence.createQuery(ServerlessInstanceStats.class)
                                                  .filter(ServerlessInstanceStatsKeys.accountId, accountId)
                                                  .order(Sort.descending(TIMESTAMP))
                                                  .asList(options);

    if (CollectionUtils.isEmpty(snapshots)) {
      return null;
    }

    return snapshots.get(0).getTimestamp();
  }

  @Override
  @Nullable
  public Instant getFirstSnapshotTime(@NotNull String accountId) {
    FindOptions options = persistence.analyticNodePreferenceOptions();
    options.limit(1);

    List<ServerlessInstanceStats> snapshots = persistence.createQuery(ServerlessInstanceStats.class)
                                                  .filter(ServerlessInstanceStatsKeys.accountId, accountId)
                                                  .order(Sort.ascending(TIMESTAMP))
                                                  .asList(options);

    if (CollectionUtils.isEmpty(snapshots)) {
      return null;
    }

    return snapshots.get(0).getTimestamp();
  }

  @Override
  public ServerlessInstanceTimeline aggregate(
      String accountId, long fromTsMillis, long toTsMillis, InvocationCountKey invocationCountKey) {
    Instant from = Instant.ofEpochMilli(fromTsMillis);
    Instant to = Instant.ofEpochMilli(toTsMillis);
    Stopwatch stopwatch = Stopwatch.createStarted();
    List<ServerlessInstanceStats> stats = aggregate(accountId, from, to);
    log.info("Aggregate Time: {} ms, accountId={}, from={} to={} with Invocation count key={}",
        stopwatch.elapsed(TimeUnit.MILLISECONDS), accountId, from, to, invocationCountKey);
    Set<String> deletedAppIds = serverlessDashboardService.getDeletedAppIds(accountId, fromTsMillis, toTsMillis);
    log.info("Get Deleted App Time: {} ms, accountId={}", stopwatch.elapsed(TimeUnit.MILLISECONDS), accountId);

    User user = UserThreadLocal.get();
    if (null != user) {
      TimelineRbacFilters rbacFilters = new TimelineRbacFilters(user, accountId, appService, userService);
      List<ServerlessInstanceStats> filteredStats = rbacFilters.filterServerlessStats(stats, deletedAppIds);
      log.info("Stats before and after filtering. Before: {}, After: {}", stats.size(), filteredStats.size());
      log.info("Time till RBAC filters: {} ms, accountId={}", stopwatch.elapsed(TimeUnit.MILLISECONDS), accountId);
      ServerlessInstanceTimeline timeline =
          ServerlessInstanceTimeline.create(filteredStats, deletedAppIds, invocationCountKey);
      ServerlessInstanceTimeline top = ServerlessInstanceTimeline.copyWithLimit(timeline, 5);
      log.info("Total time taken: {} ms, accountId={}", stopwatch.elapsed(TimeUnit.MILLISECONDS), accountId);
      return top;
    } else {
      throw NoResultFoundException.newBuilder().code(ErrorCode.USER_DOES_NOT_EXIST).build();
    }
  }

  private List<ServerlessInstanceStats> aggregate(String accountId, Instant from, Instant to) {
    Preconditions.checkArgument(to.isAfter(from), "'to' timestamp should be after 'from'");

    Query<ServerlessInstanceStats> query = persistence.createQuery(ServerlessInstanceStats.class)
                                               .filter(ServerlessInstanceStatsKeys.accountId, accountId)
                                               .field(TIMESTAMP)
                                               .greaterThanOrEq(from)
                                               .field(TIMESTAMP)
                                               .lessThan(to)
                                               .project("accountId", true)
                                               .project(TIMESTAMP, true)
                                               .project("aggregateCounts", true)
                                               .order(Sort.ascending(TIMESTAMP));

    List<ServerlessInstanceStats> timeline = new LinkedList<>();

    try (HIterator<ServerlessInstanceStats> iterator =
             new HIterator<>(query.fetch(persistence.analyticNodePreferenceOptions()))) {
      while (iterator.hasNext()) {
        timeline.add(iterator.next());
      }
    }

    return timeline;
  }
}
