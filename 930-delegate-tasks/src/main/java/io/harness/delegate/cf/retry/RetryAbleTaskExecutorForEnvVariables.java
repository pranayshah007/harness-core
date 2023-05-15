/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf.retry;

import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.pcf.PivotalClientApiException;
import org.slf4j.Logger;

import java.time.Duration;

import static io.harness.threading.Morpheus.sleep;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

public class RetryAbleTaskExecutorForEnvVariables {
  public static final int MIN_RETRY = 3;
  private static final int[] exponentialBackOffTime = new int[] {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};

  public static RetryAbleTaskExecutorForEnvVariables getExecutor() {
    return new RetryAbleTaskExecutorForEnvVariables();
  }

  public void execute(RetryAbleTask task, LogCallback executionLogCallback, Logger log, RetryPolicy policy, RetryAbleTaskForVerfication task1) {
    int attempt = 0;
    boolean isComplete = false;
    int retry = policy.getRetry() == 0 ? MIN_RETRY : policy.getRetry();
    Exception ex = null;

    while (!isComplete && attempt < retry) {
      try {
        task.execute();
        boolean status = task1.execute();
        if(status) {
          isComplete = true;
        } else {
          executionLogCallback.saveExecutionLog(policy.getUserMessageOnFailure(), LogLevel.ERROR);
          int seconds = exponentialBackOffTime[attempt];
          executionLogCallback.saveExecutionLog(
                  String.format("Sleeping for %d seconds. Retry attempt - [%d]", seconds, attempt + 1));
          sleep(Duration.ofSeconds(seconds));
          attempt++;
        }
      } catch (PivotalClientApiException exception) {
        ex = exception;
        executionLogCallback.saveExecutionLog(policy.getUserMessageOnFailure(), LogLevel.ERROR);
        log.warn(exception.getMessage());

        int seconds = exponentialBackOffTime[attempt];
        executionLogCallback.saveExecutionLog(
                String.format("Sleeping for %d seconds. Retry attempt - [%d]", seconds, attempt + 1));
        sleep(Duration.ofSeconds(seconds));
        attempt++;
      }
    }

    if (retry == attempt) {
      executionLogCallback.saveExecutionLog(color(policy.getFinalErrorMessage(), White, Bold));
      if (policy.isThrowError()) {
        throw new InvalidRequestException(String.format("Failed to complete task after retry - [%d]", retry), ex);
      }
    }
  }
}
