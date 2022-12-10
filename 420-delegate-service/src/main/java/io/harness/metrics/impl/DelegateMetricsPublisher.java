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
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_INVALID;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_NON_ASSIGNABLE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_PAUSED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.PERPETUAL_TASKS_UNASSIGNED;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
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
    if (log.isDebugEnabled()) {
      log.debug("Starting getting delegate metrics.");
    }
    long startTime = Instant.now().toEpochMilli();

    recordDelegateMetrics();
    recordPerpetualTaskMetrics();

    if (log.isDebugEnabled()) {
      log.debug("Total time taken to collect metrics for active delegates count: {} (ms)",
          Instant.now().toEpochMilli() - startTime);
    }
  }

  private void recordDelegateMetrics() {
    long activeDelegatesCount = persistence.createQuery(Delegate.class, excludeAuthority)
                                    .field(DelegateKeys.lastHeartBeat)
                                    .greaterThan(Instant.now().minusSeconds(300).toEpochMilli())
                                    .count();

    long immutableDelegatesCount = persistence.createQuery(Delegate.class, excludeAuthority)
                                       .filter(DelegateKeys.immutable, true)
                                       .field(DelegateKeys.lastHeartBeat)
                                       .greaterThan(Instant.now().minusSeconds(300).toEpochMilli())
                                       .count();
    metricService.recordMetric(ACTIVE_DELEGATES_COUNT, activeDelegatesCount);
    metricService.recordMetric(IMMUTABLE_DELEGATES, immutableDelegatesCount);
    metricService.recordMetric(MUTABLE_DELEGATES, activeDelegatesCount - immutableDelegatesCount);

    if (log.isDebugEnabled()) {
      log.debug("recorded metrics, active delegates {}, immutable delegates {} mutable delegates {}",
          activeDelegatesCount, immutableDelegatesCount, activeDelegatesCount - immutableDelegatesCount);
    }
  }

  private void recordPerpetualTaskMetrics() {
    long perpetualTaskCount = persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority).count();

    long assignedPerpetualTaskCount = persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
                                          .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_ASSIGNED)
                                          .count();

    long unAssignedPerpetualTaskCount = persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
                                            .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_UNASSIGNED)
                                            .count();

    long toRebalancePerpetualTaskCount =
        persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
            .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_TO_REBALANCE)
            .count();

    long pausedPerpetualTaskCount = persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
                                        .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_PAUSED)
                                        .count();

    long nonAssignablePerpetualTaskCount =
        persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
            .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_NON_ASSIGNABLE)
            .count();

    long invalidPerpetualTaskCount = persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
                                         .filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_INVALID)
                                         .count();

    metricService.recordMetric(PERPETUAL_TASKS, perpetualTaskCount);
    metricService.recordMetric(PERPETUAL_TASKS_ASSIGNED, assignedPerpetualTaskCount);
    metricService.recordMetric(PERPETUAL_TASKS_UNASSIGNED, unAssignedPerpetualTaskCount);
    metricService.recordMetric(PERPETUAL_TASKS_NON_ASSIGNABLE, nonAssignablePerpetualTaskCount);
    metricService.recordMetric(PERPETUAL_TASKS_INVALID, invalidPerpetualTaskCount);
    metricService.recordMetric(PERPETUAL_TASKS_PAUSED, pausedPerpetualTaskCount);

    if (log.isDebugEnabled()) {
      log.debug("PT metrics, all PTs {}, assigned {}, unassigned {}, non-assignable {}, invalid {}, paused {}",
          perpetualTaskCount, assignedPerpetualTaskCount, unAssignedPerpetualTaskCount, nonAssignablePerpetualTaskCount,
          invalidPerpetualTaskCount, pausedPerpetualTaskCount);
    }
  }
}
