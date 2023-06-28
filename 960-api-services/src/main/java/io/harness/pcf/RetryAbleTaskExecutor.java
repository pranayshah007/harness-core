/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.time.Duration;
import org.slf4j.Logger;

public class RetryAbleTaskExecutor {
  public static final int MIN_RETRY = 3;
  private static final int[] exponentialBackOffTime = new int[] {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};

  public static RetryAbleTaskExecutor getExecutor() {
    return new RetryAbleTaskExecutor();
  }

  public void execute(RetryAbleTask task, LogCallback executionLogCallback, Logger log, RetryPolicy policy) {
    int attempt = 0;
    boolean isComplete = false;
    int retry = policy.getRetry() == 0 ? MIN_RETRY : policy.getRetry();
    Exception ex = null;

    while (!isComplete && attempt < retry) {
      try {
        task.execute();
        isComplete = true;
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
