/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.metrics.beans.DelegateAccountMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Singleton
public class CachedMetricsPublisher implements MetricsPublisher {
  public static final String WEBSOCKET_CONNECTIONS_CNT = "websocket_connections_count";
  public static final String ACTIVE_DELEGATE_PROCESS_CNT = "active_delegate_process_count";

  private final MetricService metricService;
  // delegateConnectionId & connectionUuid needs to be the Key to allow for eviction of stale records
  private final Map<String, Cache<String, String>> metricCaches =
      ImmutableMap.of(ACTIVE_DELEGATE_PROCESS_CNT, Caffeine.newBuilder().expireAfterWrite(90, TimeUnit.SECONDS).build(),
          WEBSOCKET_CONNECTIONS_CNT, Caffeine.newBuilder().expireAfterWrite(90, TimeUnit.SECONDS).build());

  @Override
  public void recordMetrics() {
    metricCaches.entrySet().forEach(this::recordMetric);
  }

  private void recordMetric(final Map.Entry<String, Cache<String, String>> metricEntry) {
    final Map<String, Long> metricValues = metricEntry.getValue().asMap().entrySet().stream().collect(
        Collectors.groupingBy(Map.Entry::getValue, Collectors.counting()));

    metricValues.forEach((key, value) -> {
      try (DelegateAccountMetricContext ignore = new DelegateAccountMetricContext(key)) {
        metricService.recordMetric(metricEntry.getKey(), value);
      }
    });
  }

  public void recordDelegateProcess(final String accountId, final String delegateConnectionId) {
    metricCaches.get(ACTIVE_DELEGATE_PROCESS_CNT).put(delegateConnectionId, accountId);
  }

  public void recordDelegateWebsocketConnection(final String accountId, final String connectionUuid) {
    metricCaches.get(WEBSOCKET_CONNECTIONS_CNT).put(connectionUuid, accountId);
  }
}
