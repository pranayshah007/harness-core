/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats.collector;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot.AggregateCount;
import software.wings.beans.infrastructure.instance.stats.Mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;

/**
 * At the end, work in the same way as {@link InstanceMapper}. However, instead of receive a collection and iterate over
 * it, the class act as a consumer of {@link Instance} and aggregate data on the fly during the {@link
 * io.harness.persistence.HIterator} operation.
 *
 * @see software.wings.service.impl.instance.DashboardStatisticsServiceImpl#consumeAppInstancesForAccount(String, long,
 *     Consumer)
 */
@AllArgsConstructor
class InstanceMapperConsumer implements Mapper<Collection<Instance>, InstanceStatsSnapshot>, Consumer<Instance> {
  private Instant instant;
  private String accountId;

  private final Map<String, AggregateCount> appCounts = new HashMap<>();

  @Override
  public InstanceStatsSnapshot map(Collection<Instance> instances) {
    List<AggregateCount> aggregateCounts = new ArrayList<>(appCounts.values());

    return new InstanceStatsSnapshot(instant, accountId, aggregateCounts);
  }

  @Override
  public void accept(Instance instance) {
    AggregateCount appCount = appCounts.computeIfAbsent(instance.getAppId(),
        appId -> new AggregateCount(EntityType.APPLICATION, instance.getAppName(), instance.getAppId(), 0));
    appCount.incrementCount(1);
  }
}
