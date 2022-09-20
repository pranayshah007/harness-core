/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;

@UtilityClass
@OwnedBy(PL)
public class RetryUtils {
  public RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage,
      List<Class> exceptionClasses, Duration retrySleepDuration, int maxAttempts, Logger log) {
    RetryPolicy<Object> retryPolicy =
        new RetryPolicy<>()
            .withBackoff(retrySleepDuration.toMillis(), 10000, ChronoUnit.MILLIS)
            .withMaxAttempts(maxAttempts)
            .onFailedAttempt(event -> log.warn(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
            .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
    exceptionClasses.forEach(retryPolicy::handle);
    return retryPolicy;
  }
}
