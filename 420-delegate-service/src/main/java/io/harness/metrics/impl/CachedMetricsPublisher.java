/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.metrics.beans.DelegateIdMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CachedMetricsPublisher implements MetricsPublisher {
  public static final String WEBSOCKET_CONNECTIONS_CNT = "websocket_connections_cnt";

  private final MetricService metricService;
  private final Cache<String, String> cntCache =
      Caffeine.newBuilder().expireAfterWrite(3000, TimeUnit.MILLISECONDS).build();

  @Override
  public void recordMetrics() {
    cntCache.asMap().forEach((key, value) -> {
      try (DelegateIdMetricContext ignore = new DelegateIdMetricContext(key, value)) {
        metricService.recordMetric(WEBSOCKET_CONNECTIONS_CNT, 1);
      }
    });
  }

  public void incrementMetric(final String accountId, final String delegateId) {
    cntCache.put(accountId, delegateId);
  }
}
