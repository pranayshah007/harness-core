/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.retry;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static io.github.resilience4j.core.IntervalFunction.ofExponentialRandomBackoff;
import static java.util.Arrays.stream;
import static org.apache.commons.lang3.exception.ExceptionUtils.indexOfType;

import io.harness.annotations.dev.OwnedBy;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class RetryHelper {
  private static final int BACKOFF_INTERVAL_SECONDS = 1;
  private static final int MAX_ATTEMPTS = 3;
  private static final RetryRegistry globalRegistry = RetryRegistry.ofDefaults();

  static {
    globalRegistry.getEventPublisher()
        .onEntryAdded(entryAddedEvent -> {
          Retry addedRetry = entryAddedEvent.getAddedEntry();
          log.info("Retry {} added", addedRetry.getName());
        })
        .onEntryRemoved(entryRemovedEvent -> {
          Retry removedRetry = entryRemovedEvent.getRemovedEntry();
          log.info("Retry {} removed", removedRetry.getName());
        });
  }

  public static Retry getExponentialRetry(String name, Class[] retryExceptions) {
    final RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(ofExponentialRandomBackoff(Duration.ofSeconds(BACKOFF_INTERVAL_SECONDS).toMillis(), 2d))
            .retryOnException(t -> stream(retryExceptions).anyMatch(exception -> indexOfType(t, exception) != -1))
            .build();
    return globalRegistry.retry(name, config);
  }

  public static Retry getExponentialRetryOnException(String name, Predicate<Throwable> throwablePredicate) {
    final RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(ofExponentialRandomBackoff(Duration.ofSeconds(BACKOFF_INTERVAL_SECONDS).toMillis(), 2d))
            .retryOnException(throwablePredicate)
            .build();
    return globalRegistry.retry(name, config);
  }

  public static Retry getExponentialRetry(String name, Predicate<Object> predicate) {
    final RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(ofExponentialRandomBackoff(Duration.ofSeconds(BACKOFF_INTERVAL_SECONDS).toMillis(), 2d))
            .retryOnResult(predicate)
            .build();
    return globalRegistry.retry(name, config);
  }

  public static void registerEventListeners(Retry retry) {
    retry.getEventPublisher()
        .onRetry(event -> log.warn(event.toString()))
        .onError(event -> log.error(event.toString()))
        .onSuccess(event -> log.info(event.toString()));
  }
}
