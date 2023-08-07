/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.threading.Morpheus.sleep;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;

import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated This implementation will leak threads if the total sleep time is longer than the thread timeout as it's
 *     not interruptible
 * Use net.jodah.failsafe.Failsafe or io.github.resilience4j.retry.Retry instead.
 */
@OwnedBy(DEL)
@Slf4j
@Deprecated
public final class FibonacciBackOff {
  private static final int[] FIBONACCI = new int[] {1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144};

  private FibonacciBackOff() {}

  public static int getFibonacciElement(int index) {
    if (index < FIBONACCI.length) {
      return FIBONACCI[index];
    }
    throw new RuntimeException("Index out of bounds");
  }

  public static <T, E extends Exception> T execute(FibonacciBackOffFunction<T> fn) throws IOException {
    for (int attempt = 0; attempt < FIBONACCI.length; attempt++) {
      try {
        return fn.execute();
      } catch (IOException e) {
        handleFailure(attempt, e);
      }
    }
    throw new RuntimeException("Failed to communicate.");
  }

  public static <T> T executeForEver(FibonacciBackOffFunction<T> fn) throws IOException {
    for (int attempt = 0; attempt < FIBONACCI.length; attempt++) {
      try {
        return fn.execute();
      } catch (IOException e) {
        handleFailure(attempt, e);
      }
    }
    int attempt = FIBONACCI.length - 1;
    while (true) {
      try {
        return fn.execute();
      } catch (IOException e) {
        handleFailure(attempt, e);
      }
    }
  }

  private static void handleFailure(int attempt, IOException e) {
    if (attempt == 0) {
      log.error("Error [{}], on attempt {} ", ExceptionUtils.getMessage(e), attempt + 1, e);
    } else {
      log.warn("Error [{}], on attempt {}, please refer attempt 1 for stacktrace", ExceptionUtils.getMessage(e),
          attempt + 1);
    }
    sleep(Duration.ofSeconds(FIBONACCI[attempt]));
  }

  public static IOException peel(IOException t) {
    while (t.getCause() != null && t.getCause() instanceof IOException) {
      t = (IOException) t.getCause();
    }
    return t;
  }
}
