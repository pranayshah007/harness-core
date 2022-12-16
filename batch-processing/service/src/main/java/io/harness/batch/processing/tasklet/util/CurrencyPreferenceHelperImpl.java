/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.graphql.core.currency.CurrencyPreferenceService;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class CurrencyPreferenceHelperImpl implements CurrencyPreferenceHelper {
  @Inject private CurrencyPreferenceService currencyPreferenceService;

  private final LoadingCache<CacheKey, Double> destinationCurrencyConversionFactorCache =
      Caffeine.newBuilder()
          .maximumSize(200)
          .expireAfterWrite(12, TimeUnit.HOURS)
          .build(cacheKey
              -> currencyPreferenceService.getDestinationCurrencyConversionFactor(
                  cacheKey.getAccountId(), cacheKey.getCloudServiceProvider(), cacheKey.getSourceCurrency()));

  @Override
  public Double getDestinationCurrencyConversionFactor(@NonNull final String accountId,
      @NonNull final CloudServiceProvider cloudServiceProvider, @NonNull final Currency sourceCurrency) {
    final CacheKey cacheKey = new CacheKey(accountId, cloudServiceProvider, sourceCurrency);
    final Double destinationCurrencyConversionFactor = destinationCurrencyConversionFactorCache.get(cacheKey);

    if (Objects.isNull(destinationCurrencyConversionFactor)) {
      log.error("Unable to get destination currency conversion factor for key: {}", cacheKey);
      return 1.0D;
    }

    return destinationCurrencyConversionFactor;
  }

  @Value
  private static class CacheKey {
    String accountId;
    CloudServiceProvider cloudServiceProvider;
    Currency sourceCurrency;
  }
}
