/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.metrics.beans.DelegateMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateMetricsPublisher implements MetricsPublisher {
  private static final String ACTIVE_DELEGATES_COUNT = "active_delegates_count";

  private final MetricService metricService;
  private final HPersistence persistence;

  @Override
  public void recordMetrics() {
    sendTaskStatusMetrics();
  }

  @VisibleForTesting
  void sendTaskStatusMetrics() {
    log.info("Starting getting delegate metrics.");
    long startTime = Instant.now().toEpochMilli();

    Function<Delegate, DelegateLabel> compositeKey =
        delegate -> new DelegateLabel(delegate.getAccountId(), delegate.getVersion());

    List<Delegate> delegates = persistence.createQuery(Delegate.class, excludeAuthority)
                                   .field(DelegateKeys.status)
                                   .equal(DelegateInstanceStatus.ENABLED)
                                   .field(DelegateKeys.lastHeartBeat)
                                   .greaterThan(Instant.now().minusSeconds(300).toEpochMilli())
                                   .asList();

    Map<DelegateLabel, List<Delegate>> map =
        delegates.stream().collect(Collectors.groupingBy(compositeKey, Collectors.toList()));

    map.forEach((key, value) -> {
      try (DelegateMetricContext ignore = new DelegateMetricContext(key.getAccountId(), key.getVersion())) {
        metricService.recordMetric(ACTIVE_DELEGATES_COUNT, value.size());
      }
    });

    log.info("Total time taken to collect metrics for class {} {} (ms)", DelegateTask.class.getSimpleName(),
        Instant.now().toEpochMilli() - startTime);
  }

  @Data
  private static class DelegateLabel {
    private final String accountId;
    private final String version;
  }
}
