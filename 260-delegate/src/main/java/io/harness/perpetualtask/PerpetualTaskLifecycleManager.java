/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExceptionUtils;
import io.harness.grpc.utils.AnyUtils;
import io.harness.grpc.utils.HTimestamps;
import io.harness.logging.AutoLogContext;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.protobuf.util.Durations;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class PerpetualTaskLifecycleManager {
  private final long timeoutMillis;
  private final PerpetualTaskId taskId;
  private final TimeLimiter timeLimiter;
  private final PerpetualTaskExecutionParams params;
  private final PerpetualTaskExecutionContext context;
  private final PerpetualTaskExecutor perpetualTaskExecutor;
  private final PerpetualTaskServiceAgentClient perpetualTaskServiceAgentClient;
  private final AtomicInteger currentlyExecutingPerpetualTasksCount;
  private final String accountId;

  PerpetualTaskLifecycleManager(PerpetualTaskId taskId, PerpetualTaskExecutionContext context,
      Map<String, PerpetualTaskExecutor> factoryMap, PerpetualTaskServiceAgentClient perpetualTaskServiceAgentClient,
      TimeLimiter timeLimiter, AtomicInteger currentlyExecutingPerpetualTasksCount, String accountId) {
    this.taskId = taskId;
    this.context = context;
    this.timeLimiter = timeLimiter;
    this.perpetualTaskServiceAgentClient = perpetualTaskServiceAgentClient;
    params = context.getTaskParams();
    perpetualTaskExecutor = factoryMap.get(getTaskType(params));
    timeoutMillis = Durations.toMillis(context.getTaskSchedule().getTimeout());
    this.currentlyExecutingPerpetualTasksCount = currentlyExecutingPerpetualTasksCount;
    this.accountId = accountId;
  }

  void startTask() {
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeoutMillis), this::call);
    } catch (UncheckedTimeoutException tex) {
      log.error("Timed out starting task", tex);
    } catch (Exception ex) {
      log.error("Exception is ", ex);
    }
  }

  void stopTask() {
    try {
      if (perpetualTaskExecutor != null) {
        perpetualTaskExecutor.cleanup(taskId, params);
      }
    } catch (Exception ex) {
      log.error("Error while stopping task ", ex);
    }
  }

  Void call() {
    Instant taskStartTime = Instant.now();
    PerpetualTaskResponse perpetualTaskResponse;
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId.getId(), OVERRIDE_ERROR)) {
      currentlyExecutingPerpetualTasksCount.getAndIncrement();

      // question? some tasks are internally failing on this runOnce and putting failure reason in the
      // PerpetualTaskResponse.responseMessage, but they are setting responseCode as 200 only, as if nothing failed.
      // What should we do in that case. for example check AwsCodeDeployInstanceSyncExecutor#getPerpetualTaskResponse
      // method
      perpetualTaskResponse =
          perpetualTaskExecutor.runOnce(taskId, params, HTimestamps.toInstant(context.getHeartbeatTimestamp()));

      decrementTaskCounter();
    } catch (UncheckedTimeoutException tex) {
      perpetualTaskResponse =
          PerpetualTaskResponse.builder().responseCode(408).responseMessage(ExceptionUtils.getMessage(tex)).build();
      log.debug("Timed out starting task", tex);

      decrementTaskCounter();
    } catch (Exception ex) {
      perpetualTaskResponse =
          PerpetualTaskResponse.builder().responseCode(500).responseMessage(ExceptionUtils.getMessage(ex)).build();
      log.error("Exception during execution of perpetual task ", ex);
      decrementTaskCounter();
    }
    if (perpetualTaskResponse != null && perpetualTaskResponse.getResponseCode() != SC_OK) {
      perpetualTaskServiceAgentClient.recordPerpetualTaskFailure(taskId, accountId, perpetualTaskResponse);
    }
    return null;
  }

  private String getTaskType(PerpetualTaskExecutionParams params) {
    String fullyQualifiedClassName = AnyUtils.toFqcn(params.getCustomizedParams());
    return StringUtils.substringAfterLast(fullyQualifiedClassName, ".");
  }

  private void decrementTaskCounter() {
    if (currentlyExecutingPerpetualTasksCount.get() > 0) {
      currentlyExecutingPerpetualTasksCount.getAndDecrement();
    }
  }
}
