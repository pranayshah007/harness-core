/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.exception;

import static java.lang.String.format;

import io.harness.exception.FailureType;
import io.harness.exception.KubernetesApiTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.reflection.ReflectionUtils;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
@Singleton
public class KubernetesApiExceptionHandler implements ExceptionHandler {
  private static final String API_CALL_FAIL_MESSAGE = "Kubernetes API call failed with message: %s";
  private static final String UNAUTHORIZED_ERROR_REGEX =
      ".* forbidden: User \"(.*?)\".* resource \"(.*?)\" in API group \"(.*?)\".* namespace \"(.*?)\"";
  private static final Pattern UNAUTHORIZED_ERROR_PATTERN =
      Pattern.compile(UNAUTHORIZED_ERROR_REGEX, Pattern.MULTILINE);

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.of(ApiException.class);
  }

  @Override
  public WingsException handleException(Exception exception) {
    ApiException apiException = (ApiException) exception;

    if (apiException.getCause() != null) {
      String message = apiException.getCause().getMessage();
      Throwable cause = apiException.getCause();
      resetApiExceptionCause(apiException);
      if (cause instanceof SocketTimeoutException) {
        return NestedExceptionUtils.hintWithExplanationException(
            KubernetesExceptionHints.K8S_API_SOCKET_TIMEOUT_EXCEPTION,
            KubernetesExceptionExplanation.K8S_API_SOCKET_TIMEOUT_EXCEPTION,
            new KubernetesApiTaskException(message, FailureType.TIMEOUT_ERROR));
      } else if (cause instanceof SSLHandshakeException) {
        return NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.K8S_API_SSL_VALIDATOR,
            KubernetesExceptionExplanation.K8S_API_SSL_VALIDATOR + ": " + message,
            new KubernetesApiTaskException(message, FailureType.CONNECTIVITY));
      } else if (cause instanceof IOException) {
        return NestedExceptionUtils.hintWithExplanationException(
            KubernetesExceptionHints.K8S_API_GENERIC_NETWORK_EXCEPTION,
            KubernetesExceptionExplanation.K8S_API_IO_EXCEPTION,
            new KubernetesApiTaskException(message, FailureType.CONNECTIVITY));
      } else {
        return NestedExceptionUtils.hintWithExplanationException(
            KubernetesExceptionHints.K8S_API_GENERIC_NETWORK_EXCEPTION,
            KubernetesExceptionExplanation.K8S_API_GENERIC_NETWORK_EXCEPTION,
            new KubernetesApiTaskException(message, FailureType.CONNECTIVITY));
      }
    } else if (apiException.getCode() > 0) {
      switch (apiException.getCode()) {
        case 403:
          String parsedExceptionMessage = parseUnauthorizedError(apiException.getResponseBody());
          return NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.K8S_API_FORBIDDEN_EXCEPTION,
              parsedExceptionMessage,
              new KubernetesApiTaskException(
                  format(API_CALL_FAIL_MESSAGE, apiException.getMessage()), FailureType.AUTHORIZATION_ERROR));

        case 401:
          return NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.K8S_API_UNAUTHORIZED_EXCEPTION,
              KubernetesExceptionExplanation.K8S_API_UNAUTHORIZED_EXCEPTION,
              new KubernetesApiTaskException(
                  format(API_CALL_FAIL_MESSAGE, apiException.getMessage()), FailureType.AUTHENTICATION));

        default:
          return new KubernetesApiTaskException(format(API_CALL_FAIL_MESSAGE, apiException.getMessage()));
      }

    } else {
      // Missing cause or code & response body in all known cases is related to validation exception
      return NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.K8S_API_VALIDATION_ERROR,
          KubernetesExceptionExplanation.K8S_API_VALIDATION_ERROR,
          new KubernetesApiTaskException(apiException.getMessage()));
    }
  }

  private String parseUnauthorizedError(String responseBody) {
    try {
      JSONObject apiExceptionBody = new JSONObject(responseBody);
      String errorMessage = apiExceptionBody.getString("message");
      Matcher matcher = UNAUTHORIZED_ERROR_PATTERN.matcher(errorMessage);
      if (matcher.find()) {
        return String.format(KubernetesExceptionExplanation.K8S_API_FORBIDDEN_ERROR, matcher.group(1), matcher.group(4),
            matcher.group(2), matcher.group(3));
      }
    } catch (Exception e) {
      return KubernetesExceptionExplanation.K8S_API_FORBIDDEN_EXCEPTION;
    }
    return KubernetesExceptionExplanation.K8S_API_FORBIDDEN_EXCEPTION;
  }

  /**
   * This is a workarround on the way we handle the exceptions in ExceptionManager. If we leave the cause part
   * of ApiException, the stacktrace is filled with redundant cause multiple times, leading to too many details in UI.
   * @param apiException
   */
  private void resetApiExceptionCause(ApiException apiException) {
    try {
      ReflectionUtils.setObjectField(
          ReflectionUtils.getFieldByName(apiException.getClass(), "cause"), apiException, null);
    } catch (Exception e) {
      log.error("Failed to reset exception cause", e);
    }
  }
}
