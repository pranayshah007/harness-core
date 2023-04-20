/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.concurrent;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class HTimeLimiter {
  // Warning: using a bounded executor may be counterproductive!
  // If the thread pool fills up, any time callers spend waiting for a thread may count toward their time limit,
  // and in this case the call may even time out before the target method is ever invoked.
  @Deprecated
  public TimeLimiter create() {
    return SimpleTimeLimiter.create(Executors.newCachedThreadPool());
  }

  public TimeLimiter create(ExecutorService executor) {
    return SimpleTimeLimiter.create(executor);
  }

  @CanIgnoreReturnValue
  @Deprecated
  public static <T> T callInterruptible21(TimeLimiter timeLimiter, Duration duration, Callable<T> callable)
      throws Exception {
    try {
      return timeLimiter.callWithTimeout(callable, duration.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new UncheckedTimeoutException(exception);
    } catch (UncheckedExecutionException | ExecutionException exception) {
      throw (Exception) exception.getCause();
    } catch (ExecutionError error) {
      throw (Error) error.getCause();
    }
  }

  @CanIgnoreReturnValue
  public static <T> T callInterruptible(TimeLimiter timeLimiter, Duration duration, Callable<T> callable)
      throws Exception {
    return timeLimiter.callWithTimeout(callable, duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  @CanIgnoreReturnValue
  public static <T> T callUninterruptible21(TimeLimiter timeLimiter, Duration duration, Callable<T> callable)
      throws Exception {
    try {
      return timeLimiter.callUninterruptiblyWithTimeout(callable, duration.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new UncheckedTimeoutException(exception);
    } catch (UncheckedExecutionException | ExecutionException exception) {
      throw (Exception) exception.getCause();
    } catch (ExecutionError error) {
      throw (Error) error.getCause();
    }
  }

  @CanIgnoreReturnValue
  public static <T> T callUninterruptible(TimeLimiter timeLimiter, Duration duration, Callable<T> callable)
      throws Exception {
    return timeLimiter.callUninterruptiblyWithTimeout(callable, duration.toMillis(), TimeUnit.MILLISECONDS);
  }
}
