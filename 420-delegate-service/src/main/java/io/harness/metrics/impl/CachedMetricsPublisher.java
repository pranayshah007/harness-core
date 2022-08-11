/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import io.harness.metrics.beans.DelegateIdMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import java.util.concurrent.TimeUnit;

public class CachedMetricsPublisher implements MetricsPublisher {
  public static final String WEBSOCKET_REQUESTS = "websocket_requests";

  private final MetricService metricService;
  private final LoadingCache<CacheKey, Integer> cntCache =
      Caffeine.newBuilder().expireAfterWrite(3000, TimeUnit.MILLISECONDS).build(acc -> 0);

  @Inject
  @java.beans.ConstructorProperties({"metricService"})
  public CachedMetricsPublisher(MetricService metricService) {
    this.metricService = metricService;
  }

  @Override
  public void recordMetrics() {
    cntCache.asMap().forEach((key, value) -> {
      try (DelegateIdMetricContext ignore = new DelegateIdMetricContext(key.accountId, key.delegateId)) {
        metricService.recordMetric(WEBSOCKET_REQUESTS, value);
      }
    });
  }

  public void incrementMetric(final String accountId, final String delegateId) {
    cntCache.asMap().merge(new CacheKey(accountId, delegateId), 1, Integer::sum);
  }

  private static class CacheKey {
    private final String accountId;
    private final String delegateId;

    @java.beans.ConstructorProperties({"accountId", "delegateId"})
    public CacheKey(String accountId, String delegateId) {
      this.accountId = accountId;
      this.delegateId = delegateId;
    }

    public String getAccountId() {
      return this.accountId;
    }

    public String getDelegateId() {
      return this.delegateId;
    }

    public boolean equals(final Object o) {
      if (o == this) return true;
      if (!(o instanceof CacheKey)) return false;
      final CacheKey other = (CacheKey) o;
      if (!other.canEqual((Object) this)) return false;
      final Object this$accountId = this.getAccountId();
      final Object other$accountId = other.getAccountId();
      if (this$accountId == null ? other$accountId != null : !this$accountId.equals(other$accountId)) return false;
      final Object this$delegateId = this.getDelegateId();
      final Object other$delegateId = other.getDelegateId();
      if (this$delegateId == null ? other$delegateId != null : !this$delegateId.equals(other$delegateId))
        return false;
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof CacheKey;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $accountId = this.getAccountId();
      result = result * PRIME + ($accountId == null ? 43 : $accountId.hashCode());
      final Object $delegateId = this.getDelegateId();
      result = result * PRIME + ($delegateId == null ? 43 : $delegateId.hashCode());
      return result;
    }

    public String toString() {
      return "CachedMetricsPublisher.CacheKey(accountId=" + this.getAccountId() + ", delegateId=" + this.getDelegateId() + ")";
    }
  }
}
