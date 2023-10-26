/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.logging.common.AccessTokenBean;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.network.SafeHttpCall;

import software.wings.app.MainConfiguration;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class LoggingTokenCache {
  private final LogStreamingServiceRestClient logServiceClient;
  private final MainConfiguration mainConfiguration;

  public AccessTokenBean getToken(final String accountId) throws ExecutionException {
    return loggingTokenCache.get(accountId);
  }

  private final LoadingCache<String, AccessTokenBean> loggingTokenCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(24, TimeUnit.HOURS).build(new CacheLoader<>() {
        @Override
        public AccessTokenBean load(@NonNull final String accountId) throws IOException {
          return retrieveLogStreamingAccountToken(accountId);
        }
      });

  private AccessTokenBean retrieveLogStreamingAccountToken(final String accountId) throws IOException {
    final var token = SafeHttpCall.executeWithExceptions(logServiceClient.retrieveAccountToken(
        mainConfiguration.getLogStreamingServiceConfig().getServiceToken(), accountId));

    // Logging Service makes token expire after 48h, but doesn't keep any kind of cache
    // so each API call would produce new 48h token. We'll expire cache every 24h to be on the safe side.
    final var expirationTime = Instant.now().plus(Duration.ofDays(1));
    log.info("Refreshed logging service token for account {}. Expires at {}", accountId, expirationTime);
    return new AccessTokenBean(accountId, token, expirationTime.toEpochMilli());
  }
}
