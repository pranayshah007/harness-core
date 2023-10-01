/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.network.FibonacciBackOff;

import java.io.IOException;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@UtilityClass
@Slf4j
public class ManagerCallHelper {
  private static final int MAX_ATTEMPTS = 3;
  public <T> T executeAcquireCallWithRetry(Call<T> call, String failureMessage, Consumer<Response<T>> handler)
      throws IOException {
    Response<T> response = null;
    try {
      response = executeCallWithRetryableException(call, failureMessage);
      return response.body();
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      if (handler != null) {
        handler.accept(response);
      }
    }
  }

  // TODO: add unit test
  public <T> Response<T> executeCallWithRetryableException(Call<T> call, String failureMessage) throws IOException {
    T responseBody = null;
    Response<T> response = null;
    int attempt = 1;
    while (attempt <= MAX_ATTEMPTS && responseBody == null) {
      try {
        response = call.clone().execute();
        responseBody = response.body();
        if (responseBody == null) {
          log.warn("No response from manager on attempt {}, retrying. {}}", attempt, failureMessage);
          attempt++;
        }
      } catch (Exception exception) {
        if (attempt < MAX_ATTEMPTS) {
          attempt++;
          log.warn(String.format("%s : Attempt: %d", failureMessage, attempt));
        } else {
          throw exception;
        }
      }
    }
    return response;
  }

  public <T> Response<T> executeCallWithBackOffRetry(Call<T> call, int retryCount, String failureMessage) {
    Response<T> response = null;
    try {
      int retries = retryCount;
      for (int attempt = 0; attempt < retries; attempt++) {
        response = call.execute();
        if (response != null && response.code() >= 200 && response.code() <= 299) {
          return response;
        }
        log.warn("Failed to call. message: {}. {} error: {}. requested url: {} {}", failureMessage,
            response == null ? "null" : response.code(),
            response == null || response.errorBody() == null ? "null" : response.errorBody().string(),
            response == null || response.raw() == null || response.raw().request() == null
                ? "null"
                : response.raw().request().url(),
            attempt < (retries - 1) ? "Retrying." : "Giving up.");
        if (attempt < retries - 1) {
          // Do not sleep for last loop round, as we are going to fail.
          sleep(ofSeconds(FibonacciBackOff.getFibonacciElement(attempt)));
        }
      }
    } catch (IOException e) {
      log.error("Unable to call, message: {}", failureMessage, e);
    }
    return response;
  }

  public <T> T executeRestCall(Call<T> call, Consumer<Response<T>> handler) throws IOException {
    Response<T> response = null;
    try {
      response = call.execute();
      return response.body();
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      handler.accept(response);
    }
  }
}
