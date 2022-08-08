/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateMetricsPublisher implements MetricsPublisher {
  @Inject private MetricService metricService;
  @Inject private HPersistence persistence;
  private static final String ACTIVE_DELEGATES_COUNT = "active_delegates_count";

  @Override
  public void recordMetrics() {
    sendActiveDelegateCountMetrics();
  }

  @VisibleForTesting
  void sendActiveDelegateCountMetrics() {
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

    log.info("Total time taken to collect metrics for active delegates count: {} (ms)",
        Instant.now().toEpochMilli() - startTime);
  }

  @Data
  @AllArgsConstructor
  private static class DelegateLabel {
    String accountId;
    String version;
  }
}
