/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.exception;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.FailureType;
import io.harness.exception.GeneralException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.ngexception.AzureAppServiceTaskException;

import com.google.common.collect.ImmutableSet;
import com.microsoft.aad.adal4j.AuthenticationException;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AzureClientExceptionHandler implements ExceptionHandler {
  private static final Pattern ERROR_CODE_URI_PATTERN = Pattern.compile("\"error_uri\":\"([A-z0-9.?=:/]+)\"");
  private static final Pattern TENANT_NOT_FOUND_PATTERN = Pattern.compile("Tenant '([A-z0-9-_]+)' not found");
  private static final Pattern APPLICATION_NOT_FOUND_PATTERN =
      Pattern.compile("Application with identifier '([A-z0-9-_]+)' was not found in the directory '(.*?)'");
  private static final Pattern INVALID_SECRET_PROVIDED = Pattern.compile("Invalid client secret provided");

  private static final String HINT_ERROR_CODE_DESCRIPTION = "More details: %s";

  private static final String AUTHENTICATION_ERROR_MESSAGE = "Failed to authenticate in azure cloud";

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(AuthenticationException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof AuthenticationException) {
      String message = exception.getMessage();
      Matcher tenantNotFoundMatcher = TENANT_NOT_FOUND_PATTERN.matcher(message);
      if (tenantNotFoundMatcher.find()) {
        return exceptionFromAuthExceptionMatcher(message, tenantNotFoundMatcher);
      }

      Matcher applicationNotFoundMatcher = APPLICATION_NOT_FOUND_PATTERN.matcher(message);
      if (applicationNotFoundMatcher.find()) {
        return exceptionFromAuthExceptionMatcher(message, applicationNotFoundMatcher);
      }

      Matcher invalidSecretKeyMatcher = INVALID_SECRET_PROVIDED.matcher(message);
      if (invalidSecretKeyMatcher.find()) {
        return exceptionFromAuthExceptionMatcher(message, invalidSecretKeyMatcher);
      }
    }

    return new GeneralException(exception.getMessage());
  }

  private String extractErrorCodeUriHelpMessage(String message) {
    Matcher errorCodeUri = ERROR_CODE_URI_PATTERN.matcher(message);
    if (errorCodeUri.find()) {
      String uri = errorCodeUri.group(1);
      return format(HINT_ERROR_CODE_DESCRIPTION, uri.replaceAll("\\\\/", "/"));
    }

    return "";
  }

  private WingsException exceptionFromAuthExceptionMatcher(String message, Matcher matcher) {
    String preprocessedMessage = message.substring(matcher.end() + 1);
    String explanation = matcher.group(0).trim();
    String hint = preprocessedMessage.substring(0, preprocessedMessage.indexOf("\\r\\n")).trim();
    String errorCodeUriHelpMessage = extractErrorCodeUriHelpMessage(message);

    return NestedExceptionUtils.hintWithExplanationException(hint, explanation + ". " + errorCodeUriHelpMessage,
        new AzureAppServiceTaskException(AUTHENTICATION_ERROR_MESSAGE, EnumSet.of(FailureType.AUTHENTICATION)));
  }
}
