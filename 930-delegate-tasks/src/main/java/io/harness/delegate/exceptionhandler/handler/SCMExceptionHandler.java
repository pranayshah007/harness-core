/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.exception.WingsException.USER;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.runtime.SCMRuntimeException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class SCMExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(SCMRuntimeException.class).build();
  }
  @Override
  public WingsException handleException(Exception exception) {
    SCMRuntimeException scmException = (SCMRuntimeException) exception;
    ErrorCode errorCode = scmException.getErrorCode();

    if (errorCode == ErrorCode.SCM_UNAUTHORIZED) {
      return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_API_AUTHORIZATION,
          ExplanationException.INVALID_GIT_API_AUTHORIZATION,
          new InvalidRequestException(exception.getMessage(), USER));
    } else if (errorCode == ErrorCode.INVALID_REQUEST) {
      return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_SCM_INVALID_REQUEST,
          ExplanationException.EXPLANATION_SCM_INVALID_REQUEST,
          new InvalidRequestException("SCM service running with delegate has error", USER));
    } else if (errorCode == ErrorCode.GIT_CONNECTION_ERROR) {
      return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_REPO,
          ExplanationException.INVALID_GIT_REPO, new InvalidRequestException(exception.getMessage(), USER));
    } else if (errorCode == ErrorCode.CONNECTION_TIMEOUT) {
      return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_GIT_CONNECTIVITY,
          ExplanationException.GIT_TIME_OUT, new InvalidRequestException(exception.getMessage(), USER));
    }
    return new InvalidRequestException(exception.getMessage(), USER);
  }
}
