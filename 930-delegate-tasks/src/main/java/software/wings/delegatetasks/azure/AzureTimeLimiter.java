/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.delegatetasks.azure.DefaultCompletableSubscriber.CompletableSubscriberStatus.COMPLETED;
import static software.wings.delegatetasks.azure.DefaultCompletableSubscriber.CompletableSubscriberStatus.ERROR;
import static software.wings.delegatetasks.azure.DefaultCompletableSubscriber.CompletableSubscriberStatus.SUBSCRIBED;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AzureTimeLimiter {
  @Inject protected TimeLimiter timeLimiter;

  public void waitUntilCompleteWithTimeout(long steadyCheckTimeoutInMinutes, long statusCheckIntervalInSeconds,
      DefaultCompletableSubscriber subscriber, Supplier<Void> pollAction, LogCallback errorLogCallback,
      String commandUnitName) {
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(steadyCheckTimeoutInMinutes), () -> {
        while (true) {
          if (ERROR == subscriber.getStatus()) {
            Throwable cause = subscriber.getError().getCause();
            throw new UncheckedExecutionException(cause);
          }
          if (COMPLETED == subscriber.getStatus()) {
            return Boolean.TRUE;
          }
          if (SUBSCRIBED == subscriber.getStatus()) {
            pollAction.get();
          }
          sleep(ofSeconds(statusCheckIntervalInSeconds));
        }
      });
    } catch (UncheckedTimeoutException e) {
      String message = format("Timed out waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      errorLogCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
      subscriber.unsubscribe();
      throw new InvalidRequestException(message, e);
    } catch (Exception e) {
      String message =
          format("Error while waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      errorLogCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
      subscriber.unsubscribe();
      throw new InvalidRequestException(message, e);
    }
  }
}
