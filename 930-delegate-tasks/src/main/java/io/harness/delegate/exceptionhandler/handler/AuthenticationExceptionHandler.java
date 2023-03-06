/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.runtime.AuthenticationAndAuthorizationRuntimeException;
import io.harness.exception.runtime.AuthenticationRuntimeException;
import io.harness.exception.runtime.AuthorizationRuntimeException;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class AuthenticationExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder()
        .add(AuthenticationAndAuthorizationRuntimeException.class)
        .build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof AuthenticationRuntimeException) {
      AuthenticationRuntimeException ex = (AuthenticationRuntimeException) exception;

      return new ExplanationException(
          String.format("While trying to make http request to %s ,I got Authentication error!!. Response code: 401",
              ex.getMessage()),
          new HintException("Make sure your credentials are valid",
              new HintException("Check you have provided the correct necessary headers",
                  new io.harness.exception.AuthenticationException(ex.getMessage(), WingsException.USER))));

    } else if (exception instanceof AuthorizationRuntimeException) {
      AuthorizationRuntimeException ex = (AuthorizationRuntimeException) exception;

      return new ExplanationException(
          String.format("While trying to make http request to %s ,I got Authorization error!!. Response Code: 403",
              ex.getMessage()),
          new HintException("Make your you have provide the correct values in the headers",
              new HintException("Check if you have the correct access to the required resource with given credentials",
                  new HintException("Make sure to connect to VPN if harness url is behind VPN",
                      new io.harness.exception.AuthorizationException(ex.getMessage(), WingsException.USER)))));
    }

    return new InvalidRequestException("Could not get exception details");
  }
}
