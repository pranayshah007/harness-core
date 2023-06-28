/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.grpc.Status.Code.CANCELLED;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.INTERNAL;
import static io.grpc.Status.Code.UNAVAILABLE;

import io.harness.delegate.beans.NoDelegatesException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;

import com.google.common.collect.ImmutableSet;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.EnumMap;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateServiceDriverExceptionHandler implements ExceptionHandler {
  private static final String HINT_MESSAGE = "DelegateServiceDriverException";
  private static final String DEFAULT_MESSAGE = "DelegateServiceDriver";
  private final EnumMap<Status.Code, String> statusCodeToMessageMap = getStatusCodeToMessageMap();

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(DelegateServiceDriverException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof StatusRuntimeException) {
      StatusRuntimeException statusRuntimeException = (StatusRuntimeException) exception;
      log.error("Delegate Service Drive Exception: {}", statusRuntimeException.getMessage());
      final String message = statusRuntimeException.getStatus() != null
              && statusCodeToMessageMap.containsKey(statusRuntimeException.getStatus().getCode())
          ? statusCodeToMessageMap.get(statusRuntimeException.getStatus().getCode())
          : DEFAULT_MESSAGE;
      return NestedExceptionUtils.hintWithExplanationException(HINT_MESSAGE + " " + statusRuntimeException.getMessage(),
          message, new InvalidRequestException(statusRuntimeException.getMessage(), exception));
    }
    if (exception instanceof NoDelegatesException) {
      return new InvalidRequestException(exception.getMessage());
    }
    return new InvalidRequestException(exception.getMessage(), exception);
  }

  private EnumMap<Status.Code, String> getStatusCodeToMessageMap() {
    EnumMap<Status.Code, String> statusCodeToMessageMap = new EnumMap<>(Status.Code.class);
    statusCodeToMessageMap.put(INTERNAL, "Unknown grpc error on DelegateServiceDriver.");
    statusCodeToMessageMap.put(DEADLINE_EXCEEDED, "Operation Timed out before completion");
    statusCodeToMessageMap.put(CANCELLED, "The operation was cancelled typically by the caller.");
    statusCodeToMessageMap.put(UNAVAILABLE, "Grpc Service is currently unavailable.");
    return statusCodeToMessageMap;
  }
}
