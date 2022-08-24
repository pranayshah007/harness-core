/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer.support;

import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Get decide if need to generate billing data or not.
 */
@Component
@Slf4j
public class BillingDataGenerationValidator {
  private final LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  private ClusterDataGenerationValidator clusterDataGenerationValidator;

  LoadingCache<BillingDataGenerationValidator.CacheKey, Boolean> billingDataGenerationValidationCache =
      Caffeine.newBuilder()
          .expireAfterWrite(6, TimeUnit.HOURS)
          .build(key -> fetchClusterBillingDataValidationInfo(key.accountId, key.clusterId, key.startTime));

  @Value
  private static class CacheKey {
    private String accountId;
    private String clusterId;
    private Instant startTime;
  }

  @Inject
  public BillingDataGenerationValidator(LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao,
      ClusterDataGenerationValidator clusterDataGenerationValidator) {
    this.lastReceivedPublishedMessageDao = lastReceivedPublishedMessageDao;
    this.clusterDataGenerationValidator = clusterDataGenerationValidator;
  }

  public boolean shouldGenerateBillingData(String accountId, String clusterId, Instant startTime) {
    if (!clusterDataGenerationValidator.shouldGenerateClusterData(accountId, clusterId)) {
      return false;
    }
    final BillingDataGenerationValidator.CacheKey cacheKey =
        new BillingDataGenerationValidator.CacheKey(accountId, clusterId, startTime);
    return billingDataGenerationValidationCache.get(cacheKey);
  }

  private boolean fetchClusterBillingDataValidationInfo(String accountId, String clusterId, Instant startTime) {
    LastReceivedPublishedMessage lastReceivedPublishedMessage =
        lastReceivedPublishedMessageDao.get(accountId, clusterId);
    Instant bufferedStartTime = startTime.minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
    boolean generateData = false;
    if (null != lastReceivedPublishedMessage
        && Instant.ofEpochMilli(lastReceivedPublishedMessage.getLastReceivedAt()).isAfter(bufferedStartTime)) {
      generateData = true;
    }
    if (!generateData) {
      log.info("Not generating biling data for {} {} {} ", accountId, clusterId, startTime);
    }
    return generateData;
  }
}
