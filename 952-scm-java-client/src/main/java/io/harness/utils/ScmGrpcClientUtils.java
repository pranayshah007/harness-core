/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ConnectException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.ScmRequestTimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.SCMRuntimeException;

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
public class ScmGrpcClientUtils {
  private static final RetryPolicy<Object> RETRY_POLICY = createRetryPolicy(1);

  public <T, R> R retryAndProcessException(Function<T, R> fn, T arg) {
    try {
      return Failsafe.with(RETRY_POLICY).get(() -> fn.apply(arg));
    } catch (Exception ex) {
      throw processException(ex);
    }
  }

  public <T, R> R retryAndProcessException(Function<T, R> fn, T arg, int maxRetries) {
    try {
      return Failsafe.with(createRetryPolicy(maxRetries)).get(() -> fn.apply(arg));
    } catch (Exception ex) {
      throw processException(ex);
    }
  }

  private RuntimeException processException(Exception ex) {
    if (ex instanceof WingsException) {
      return (WingsException) ex;
    } else if (ex instanceof StatusRuntimeException) {
      log.error("Unable to connect to Git Provider, error while connecting to scm service", ex);
      StatusRuntimeException sre = (StatusRuntimeException) ex;
      String errorMessage = isEmpty(sre.getMessage()) || sre.getMessage().equals("UNAVAILABLE: io exception")
          ? "Unable to connect to Git Provider, Please retry after sometime"
          : sre.getMessage();
      if (sre.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        return new ConnectException(errorMessage, WingsException.USER);
      } else if (sre.getStatus().getCode() == Status.Code.CANCELLED) {
        return new ScmRequestTimeoutException(ex.getMessage() == null || ex.getMessage().isEmpty()
                ? "Request to the SCM Server timed out."
                : ex.getMessage());
      }
      return new SCMRuntimeException("SCM request failed with: " + errorMessage, ErrorCode.SCM_API_ERROR);
    } else {
      log.error("Error connecting to scm service", ex);
      return new GeneralException(
          ex == null ? "Unknown error while communicating with scm service" : ExceptionUtils.getMessage(ex));
    }
  }

  private RetryPolicy<Object> createRetryPolicy(int maxRetries) {
    return new RetryPolicy<>()
        .withDelay(Duration.ofMillis(750))
        .withMaxAttempts(maxRetries)
        .onFailedAttempt(event
            -> log.warn(String.format("Scm grpc retry attempt: %d", event.getAttemptCount()), event.getLastFailure()))
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
