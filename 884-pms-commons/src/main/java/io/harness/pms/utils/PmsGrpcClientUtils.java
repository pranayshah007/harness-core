/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PmsGrpcClientUtils {
  private static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_DELAY_MS = 100;
  private static final long MAX_DELAY_MS = 5000;
  private static final long DELAY_FACTOR = 5;

  private static final RetryPolicy<Object> RETRY_POLICY = createRetryPolicy();

  public <T, R> R retryAndProcessException(Function<T, R> fn, T arg) {
    try {
      return Failsafe.with(RETRY_POLICY).get(() -> fn.apply(arg));
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
      log.error("Unknown Error Occurred, check exception logs", ex);
      return new GeneralException(ex == null ? "Unknown Error Occurred" : ExceptionUtils.getMessage(ex));
    }
  }

  private WingsException processStatusRuntimeException(StatusRuntimeException ex) {
    if (ex.getStatus().getCode() == Status.Code.INTERNAL) {
      return new GeneralException(EmptyPredicate.isEmpty(ex.getStatus().getDescription())
              ? "Unknown grpc error while communicating with pipeline service"
              : ex.getStatus().getDescription());
    }
    log.error("Unknown Exception Occurred.", ex);
    return new GeneralException("Unknown Exception Occurred. Please contact with Harness.", ex);
  }

  private RetryPolicy<Object> createRetryPolicy() {
    return new RetryPolicy<>()
        .withBackoff(INITIAL_DELAY_MS, MAX_DELAY_MS, ChronoUnit.MILLIS, DELAY_FACTOR)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.warn(
                String.format("Pms sdk grpc retry attempt: %d", event.getAttemptCount()), event.getLastFailure()))
        .onFailure(event
            -> log.error(String.format("Pms sdk grpc retry failed after attempts: %d", event.getAttemptCount()),
                event.getFailure()))
        .handleIf(throwable -> {
          if (!(throwable instanceof StatusRuntimeException)) {
            return false;
          }
          StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
          return statusRuntimeException.getStatus().getCode() == Status.Code.UNAVAILABLE
              || statusRuntimeException.getStatus().getCode() == Status.Code.UNKNOWN
              || statusRuntimeException.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED
              || statusRuntimeException.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
        });
  }
}
