/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.lib.StaticLimit;

import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.ExternalApiRateLimitingService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@Singleton
@ValidateOnExecution
@Slf4j
public class ExternalApiRateLimitingServiceImpl implements ExternalApiRateLimitingService {
  private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(2);
  // TODO this needs to be removed when generic rate limiting is implemented.
  private static final double MAX_QPM_PER_MANAGER = 50;
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private LoadingCache<String, RateLimiter> limiters =
      CacheBuilder.newBuilder()
          .expireAfterAccess(CACHE_EXPIRATION.toMillis(), TimeUnit.MILLISECONDS)
          .build(new CacheLoader<String, RateLimiter>() {
            @Override
            public RateLimiter load(String key) {
              return RateLimiter.create(getMaxQPMPerManager(key) / 60);
            }
          });

  private LimitConfigurationService limitConfigurationService;
  private ApiKeyService apiKeyService;

  @Inject
  public ExternalApiRateLimitingServiceImpl(
      @NotNull LimitConfigurationService limitConfigurationService, @NotNull ApiKeyService apiKeyService) {
    this.limitConfigurationService = limitConfigurationService;
    this.apiKeyService = apiKeyService;
  }

  @Override
  public boolean rateLimitRequest(String key) {
    return !limiters.getUnchecked(key).tryAcquire();
  }

  @Override
  public double getMaxQPMPerManager(String key) {
    String accountId = apiKeyService.getAccountIdFromApiKey(key);
    ConfiguredLimit<StaticLimit> configuredLimit;
    if (isNotEmpty(accountId)) {
      configuredLimit =
          limitConfigurationService.getOrDefaultToGlobal(accountId, GLOBAL_ACCOUNT_ID, ActionType.MAX_QPM_PER_MANAGER);
    } else {
      configuredLimit = limitConfigurationService.getOrDefault(GLOBAL_ACCOUNT_ID, ActionType.MAX_QPM_PER_MANAGER);
    }
    if (configuredLimit != null && configuredLimit.getLimit() != null) {
      return configuredLimit.getLimit().getCount();
    } else {
      return MAX_QPM_PER_MANAGER;
    }
  }
}
