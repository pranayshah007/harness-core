/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.azure.AzureAppServicesSlotSteadyStateException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.Callable;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class SlotSteadyStateChecker {
  @Inject protected TimeLimiter timeLimiter;

  public void waitUntilCompleteWithTimeout(long steadyCheckTimeoutInMinutes, long statusCheckIntervalInSeconds,
      LogCallback logCallback, String commandUnitName, SlotStatusVerifier slotStatusVerifier) {
    try {
      startPollingTask(steadyCheckTimeoutInMinutes, statusCheckIntervalInSeconds, logCallback, slotStatusVerifier);
    } catch (UncheckedTimeoutException e) {
      String message = format("Timed out waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      logCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
      throw new InvalidRequestException(message, e);
    } catch (Exception e) {
      String message =
          format("Error while waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      logCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
      throw new AzureAppServicesSlotSteadyStateException(message);
    }
  }

  public void waitUntilDeploymentCompleteWithTimeout(long steadyCheckTimeoutInMinutes,
      long statusCheckIntervalInSeconds, LogCallback logCallback, String commandUnitName,
      SlotStatusVerifier slotStatusVerifier) {
    try {
      startPollingTask(steadyCheckTimeoutInMinutes, statusCheckIntervalInSeconds, logCallback, slotStatusVerifier);
    } catch (UncheckedTimeoutException e) {
      String message =
          "Timed out waiting for deployment to complete. \nUnable to determine the deployment status through slot streaming log. \nPlease verify manually the deployment status";
      logCallback.saveExecutionLog(color(message, White, Bold), LogLevel.INFO, SUCCESS);
      slotStatusVerifier.stopPolling();
    } catch (Exception e) {
      String message =
          format("Error while waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      logCallback.saveExecutionLog(message, LogLevel.ERROR, FAILURE);
      slotStatusVerifier.stopPolling();
      throw new InvalidRequestException(message, e);
    }
  }

  private void startPollingTask(long steadyCheckTimeoutInMinutes, long statusCheckIntervalInSeconds,
      LogCallback logCallback, SlotStatusVerifier slotStatusVerifier) throws Exception {
    Callable<Object> objectCallable = () -> {
      while (true) {
        sleep(ofSeconds(statusCheckIntervalInSeconds));

        if (slotStatusVerifier.operationFailed()) {
          String errorMessage = slotStatusVerifier.getErrorMessage();
          logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, FAILURE);
          throw new InvalidRequestException(errorMessage);
        }

        if (slotStatusVerifier.hasReachedSteadyState()) {
          return Boolean.TRUE;
        }
      }
    };

    HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(steadyCheckTimeoutInMinutes), objectCallable);
  }
}
