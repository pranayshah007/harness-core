/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
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
import io.harness.exception.runtime.azure.AzureAppServicesRuntimeException;
import io.harness.exception.runtime.azure.AzureAppServicesSlotSteadyStateException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
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
      startPollingTask(
          steadyCheckTimeoutInMinutes, statusCheckIntervalInSeconds, logCallback, commandUnitName, slotStatusVerifier);
    } catch (AzureAppServicesRuntimeException e) {
      logCallback.saveExecutionLog(e.getMessage(), LogLevel.ERROR);
      throw e;
    } catch (UncheckedTimeoutException e) {
      String exceptionMessage = e.getMessage() == null ? "" : format(", %n %s", e.getMessage());
      String message = format("Timed out waiting for executing operation [%s]%s", commandUnitName, exceptionMessage);
      logCallback.saveExecutionLog(message, LogLevel.ERROR);
      throw new AzureAppServicesSlotSteadyStateException(message, commandUnitName, steadyCheckTimeoutInMinutes, e);
    } catch (InterruptedException e) {
      String message = format("Operation [%s] timed out or aborted", commandUnitName);
      logCallback.saveExecutionLog(message, LogLevel.ERROR);
      throw new AzureAppServicesSlotSteadyStateException(message, commandUnitName, steadyCheckTimeoutInMinutes, e);
    } catch (Exception e) {
      String message =
          format("Error while waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      logCallback.saveExecutionLog(message, LogLevel.ERROR);
      throw new AzureAppServicesSlotSteadyStateException(message, commandUnitName, steadyCheckTimeoutInMinutes, null);
    }
  }

  public void waitUntilDeploymentCompleteWithTimeout(long steadyCheckTimeoutInMinutes,
      long statusCheckIntervalInSeconds, LogCallback logCallback, String commandUnitName,
      SlotStatusVerifier slotStatusVerifier) {
    try {
      startPollingTask(
          steadyCheckTimeoutInMinutes, statusCheckIntervalInSeconds, logCallback, commandUnitName, slotStatusVerifier);
    } catch (UncheckedTimeoutException e) {
      String message =
          "Timed out waiting for deployment to complete. \nUnable to determine the deployment status through slot streaming log. \nPlease verify manually the deployment status";
      logCallback.saveExecutionLog(color(message, White, Bold), LogLevel.ERROR);
      slotStatusVerifier.stopPolling();
    } catch (Exception e) {
      String message =
          format("Error while waiting for executing operation [%s], %n %s", commandUnitName, e.getMessage());
      logCallback.saveExecutionLog(message, LogLevel.ERROR);
      slotStatusVerifier.stopPolling();
      throw new InvalidRequestException(message, e);
    }
  }

  private void startPollingTask(long steadyCheckTimeoutInMinutes, long statusCheckIntervalInSeconds,
      LogCallback logCallback, String commandUnitName, SlotStatusVerifier slotStatusVerifier) throws Exception {
    Callable<AtomicReference<Boolean>> objectCallable = () -> {
      AtomicReference<Boolean> result = new AtomicReference<>();
      if (slotStatusVerifier.getResponseMono() != null) {
        // checking slot status
        slotStatusVerifier.getResponseMono()
            .doOnError(error -> {
              logCallback.saveExecutionLog(error.getMessage(), LogLevel.ERROR, FAILURE);
              throw new AzureAppServicesSlotSteadyStateException(
                  error.getMessage(), commandUnitName, steadyCheckTimeoutInMinutes, null);
            })
            .doOnSuccess(response -> {
              if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                result.set(Boolean.TRUE);
              } else {
                String errorMsg = format("Task has failed with response code [%s]", response.getStatusCode());
                logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, FAILURE);
                throw new AzureAppServicesSlotSteadyStateException(
                    errorMsg, commandUnitName, steadyCheckTimeoutInMinutes, null);
              }
            })
            .block(Duration.ofMinutes(steadyCheckTimeoutInMinutes));
      } else {
        // checking slot deployment status
        while (true) {
          sleep(ofSeconds(statusCheckIntervalInSeconds));
          if (slotStatusVerifier.hasReachedSteadyState()) {
            result.set(Boolean.TRUE);
            break;
          }
        }
      }

      return result;
    };

    HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(steadyCheckTimeoutInMinutes), objectCallable);
  }
}
