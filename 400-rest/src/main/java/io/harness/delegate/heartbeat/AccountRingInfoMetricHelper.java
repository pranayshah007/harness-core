/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.ACCOUNT_RING_INFO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateRing;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HPersistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class AccountRingInfoMetricHelper {
  @Inject private HPersistence persistence;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject protected Clock clock;

  private final Cache<String, DelegateRing> delegateRingCache =
      Caffeine.newBuilder().maximumSize(10).expireAfterWrite(30, TimeUnit.MINUTES).build();

  private DelegateRing getDelegateRing(String ringName) {
    DelegateRing delegateRing = delegateRingCache.getIfPresent(ringName);
    if (delegateRing != null) {
      return delegateRing;
    }
    DelegateRing ringFromDB =
        persistence.createQuery(DelegateRing.class).filter(DelegateRing.DelegateRingKeys.ringName, ringName).get();
    delegateRingCache.put(ringName, ringFromDB);
    return ringFromDB;
  }

  public void addAccountRingInfoMetric(String accountId, String accountName, String ringName) {
    DelegateRing delegateRing = getDelegateRing(ringName);
    delegateMetricsService.recordAccountRingInfoMetric(
        accountId, accountName, delegateRing, clock.millis(), ACCOUNT_RING_INFO);
  }
}
