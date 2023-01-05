/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.remote.client;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import retrofit2.Response;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class RestClientUtils {
  private static final int MAX_ATTEMPTS = 3;
  public static <T> RetryPolicy<Response<ResponseDTO<T>>> getRetryPolicy(String failureMessage) {
    return new RetryPolicy<Response<ResponseDTO<T>>>()
        .withBackoff(1, 10, ChronoUnit.SECONDS)
        .handle(IOException.class)
        .handleResultIf(result -> !result.isSuccessful() && isRetryableHttpCode(result.code()))
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> handleFailure(event, failureMessage));
  }
  private static <T> void handleFailure(
      ExecutionAttemptedEvent<Response<ResponseDTO<T>>> event, String failureMessage) {
    if (event.getLastResult() == null) {
      log.warn(String.format("%s. Attempt : %d.", failureMessage, event.getAttemptCount()), event.getLastFailure());
    } else {
      log.warn(String.format(
                   "%s. Attempt : %d. Response : %s", failureMessage, event.getAttemptCount(), event.getLastResult()),
          event.getLastFailure());
    }
  }
  private static boolean isRetryableHttpCode(int httpCode) {
    // https://stackoverflow.com/questions/51770071/what-are-the-http-codes-to-automatically-retry-the-request
    return httpCode == 408 || httpCode == 502 || httpCode == 503 || httpCode == 504;
  }
}
