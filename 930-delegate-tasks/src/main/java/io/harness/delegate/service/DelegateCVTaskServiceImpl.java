/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.network.SafeHttpCall.execute;

import static software.wings.delegatetasks.cv.CVConstants.MAX_RETRIES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.managerclient.VerificationServiceClient;

import software.wings.delegatetasks.DelegateCVTaskService;
import software.wings.service.impl.analysis.DataCollectionTaskResult;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateCVTaskServiceImpl implements DelegateCVTaskService {
  private static final int TIMEOUT_DURATION_SEC = 5;
  @VisibleForTesting static Duration DELAY = Duration.ofSeconds(1);
  @Inject private VerificationServiceClient verificationClient;
  @Inject private TimeLimiter timeLimiter;
  @Override
  public void updateCVTaskStatus(String accountId, String cvTaskId, DataCollectionTaskResult dataCollectionTaskResult)
      throws TimeoutException {
    try {
      RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
                                            .handle(Exception.class)
                                            .withDelay(DELAY)
                                            .withMaxRetries(MAX_RETRIES)
                                            .onFailedAttempt(event
                                                -> log.info("[Retrying]: Failed updating task status attempt: {}",
                                                    event.getAttemptCount(), event.getLastFailure()))
                                            .onFailure(event
                                                -> log.error("[Failed]: Failed updating task status attempt: {}",
                                                    event.getAttemptCount(), event.getFailure()));
      Failsafe.with(retryPolicy)
          .run(()
                   -> HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(TIMEOUT_DURATION_SEC),
                       ()
                           -> execute(
                               verificationClient.updateCVTaskStatus(accountId, cvTaskId, dataCollectionTaskResult))));
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timeout of " + TIMEOUT_DURATION_SEC + " sec and " + MAX_RETRIES
          + " retries exceeded while updating CVTask status");
    }
  }
}
