/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@UtilityClass
@Slf4j
@OwnedBy(PL)
public class GitSyncGrpcClientUtils {
  private static final RetryPolicy<Object> RETRY_POLICY = createRetryPolicy();
  private static final RetryPolicy<Object> RETRY_POLICY_V2 = createRetryPolicyV2();

  public <T, R> R retryAndProcessException(Function<T, R> fn, T arg) {
    try {
      return Failsafe.with(RETRY_POLICY).get(() -> fn.apply(arg));
    } catch (Exception ex) {
      throw processException(ex);
    }
  }

  public <T, R> R retryAndProcessExceptionV2(Function<T, R> fn, T arg) {
    try {
      return Failsafe.with(RETRY_POLICY_V2).get(() -> fn.apply(arg));
    } catch (Exception ex) {
      throw processException(ex);
    }
  }

  private WingsException processException(Exception ex) {
    if (ex instanceof WingsException) {
      return (WingsException) ex;
    } else if (ex instanceof StatusRuntimeException) {
      return processStatusRuntimeException((StatusRuntimeException) ex);
    } else {
      return new GeneralException(
          ex == null ? "Unknown error while communicating with git-sync service" : ExceptionUtils.getMessage(ex));
    }
  }

  private WingsException processStatusRuntimeException(StatusRuntimeException ex) {
    if (ex.getStatus().getCode() == Status.Code.INTERNAL) {
      return new GeneralException(EmptyPredicate.isEmpty(ex.getStatus().getDescription())
              ? "Unknown grpc error while communicating with pgit-sync service"
              : ex.getStatus().getDescription());
    } else if (ex.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
      return new GeneralException("Request to git-sync service timed out");
    }

    log.error("Error connecting to git-sync service. Is it running?", ex);
    return new GeneralException("Error connecting to git-sync service");
  }

  private RetryPolicy<Object> createRetryPolicy() {
    return new RetryPolicy<>()
        .withDelay(Duration.ofMillis(750))
        .withMaxAttempts(3)
        .onFailedAttempt(event
            -> log.warn(
                String.format("Git sync grpc retry attempt: %d", event.getAttemptCount()), event.getLastFailure()))
        .onFailure(event
            -> log.error(String.format("Git Sync grpc retry failed after attempts: %d", event.getAttemptCount()),
                event.getFailure()))
        .handleIf(throwable -> {
          if (!(throwable instanceof StatusRuntimeException)) {
            return false;
          }
          StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
          return statusRuntimeException.getStatus().getCode() == Status.Code.UNAVAILABLE
              || statusRuntimeException.getStatus().getCode() == Status.Code.UNKNOWN;
        });
  }
  private RetryPolicy<Object> createRetryPolicyV2() {
    return new RetryPolicy<>()
        .withDelay(Duration.ofMillis(750))
        .withMaxAttempts(1)
        .onFailedAttempt(event
            -> log.warn(
                String.format("Git-service grpc retry attempt: %d", event.getAttemptCount()), event.getLastFailure()))
        .onFailure(event
            -> log.error(String.format("Git-service grpc retry failed after attempts: %d", event.getAttemptCount()),
                event.getFailure()))
        .handleIf(throwable -> {
          if (!(throwable instanceof StatusRuntimeException)) {
            return false;
          }
          StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
          return statusRuntimeException.getStatus().getCode() == Status.Code.UNAVAILABLE
              || statusRuntimeException.getStatus().getCode() == Status.Code.UNKNOWN;
        });
  }
}
