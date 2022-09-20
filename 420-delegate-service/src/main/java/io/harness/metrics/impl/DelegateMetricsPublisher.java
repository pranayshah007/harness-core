/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import static io.harness.metrics.impl.DelegateMetricsServiceImpl.IMMUTABLE_DELEGATES;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.MUTABLE_DELEGATES;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_ASSIGNED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_TO_REBALANCE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_UNASSIGNED;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.metrics.beans.DelegateAccountMetricContext;
import io.harness.metrics.beans.DelegateMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Singleton
public class DelegateMetricsPublisher implements MetricsPublisher {
  private static final String ACTIVE_DELEGATES_COUNT = "active_delegate_count";

  private final MetricService metricService;
  private final HPersistence persistence;

  @Override
  public void recordMetrics() {
    sendActiveDelegateCountMetrics();
  }

  @VisibleForTesting
  void sendActiveDelegateCountMetrics() {
    log.info("Starting getting delegate metrics.");
    long startTime = Instant.now().toEpochMilli();

    recordDelegateMetrics();
    recordPerpetualTaskMetrics();

    log.info("Total time taken to collect metrics for active delegates count: {} (ms)",
        Instant.now().toEpochMilli() - startTime);
  }

  private void recordDelegateMetrics() {
    List<Delegate> delegates = new ArrayList<>();
    try (HIterator<Delegate> iterator = new HIterator<>(persistence.createQuery(Delegate.class, excludeAuthority)
                                                            .project(DelegateKeys.accountId, true)
                                                            .project(DelegateKeys.version, true)
                                                            .project(DelegateKeys.immutable, true)
                                                            .field(DelegateKeys.lastHeartBeat)
                                                            .greaterThan(Instant.now().minusSeconds(300).toEpochMilli())
                                                            .fetch())) {
      while (iterator.hasNext()) {
        delegates.add(iterator.next());
      }
    }

    Function<Delegate, DelegateLabel> compositeKey =
        delegate -> new DelegateLabel(delegate.getAccountId(), delegate.getVersion());
    Map<DelegateLabel, List<Delegate>> map =
        delegates.stream().collect(Collectors.groupingBy(compositeKey, Collectors.toList()));

    map.forEach((key, delegateList) -> {
      try (DelegateMetricContext ignore = new DelegateMetricContext(key.getAccountId(), key.getVersion())) {
        metricService.recordMetric(ACTIVE_DELEGATES_COUNT, delegateList.size());
        long immutableDelegates = delegateList.stream().filter(delegate -> delegate.isImmutable()).count();
        metricService.recordMetric(IMMUTABLE_DELEGATES, immutableDelegates);
        metricService.recordMetric(MUTABLE_DELEGATES, delegateList.size() - immutableDelegates);
      }
    });
  }

  private void recordPerpetualTaskMetrics() {
    List<PerpetualTaskRecord> perpetualTaskRecords = new ArrayList<>();
    try (HIterator<PerpetualTaskRecord> iterator =
             new HIterator<>(persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
                                 .project(PerpetualTaskRecordKeys.accountId, true)
                                 .project(PerpetualTaskRecordKeys.state, true)
                                 .fetch())) {
      while (iterator.hasNext()) {
        perpetualTaskRecords.add(iterator.next());
      }
    }
    Function<PerpetualTaskRecord, String> ptAccountKey = perpetualTaskRecord -> perpetualTaskRecord.getAccountId();
    Map<String, List<PerpetualTaskRecord>> accountPTs =
        perpetualTaskRecords.stream().collect(Collectors.groupingBy(ptAccountKey, Collectors.toList()));
    accountPTs.forEach((accountId, pts) -> {
      try (DelegateAccountMetricContext ignore = new DelegateAccountMetricContext(accountId)) {
        metricService.recordMetric(PERPETUAL_TASKS, pts.size());
        metricService.recordMetric(PERPETUAL_TASKS_ASSIGNED,
            pts.stream().filter(pt -> PerpetualTaskState.TASK_ASSIGNED.equals(pt.getState())).count());
        metricService.recordMetric(PERPETUAL_TASKS_UNASSIGNED,
            pts.stream().filter(pt -> PerpetualTaskState.TASK_UNASSIGNED.equals(pt.getState())).count());
        metricService.recordMetric(PERPETUAL_TASKS_TO_REBALANCE,
            pts.stream().filter(pt -> PerpetualTaskState.TASK_TO_REBALANCE.equals(pt.getState())).count());
      }
    });
  }

  @Data
  private static class DelegateLabel {
    private final String accountId;
    private final String version;
  }
}
