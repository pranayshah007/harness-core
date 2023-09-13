/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SCMExceptionHandlerTest extends CategoryTest {
  SCMExceptionHandler scmExceptionHandler = new SCMExceptionHandler();
  Exception exception;

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testSCMUnauthorised() {
    exception = new SCMRuntimeException("blah", ErrorCode.SCM_UNAUTHORIZED);
    Exception actual = scmExceptionHandler.handleException(exception);
    assertThat(actual instanceof HintException).isTrue();
    assertThat(actual.getCause() instanceof ExplanationException).isTrue();
    assertThat(actual.getMessage()).isEqualTo(HintException.HINT_INVALID_GIT_API_AUTHORIZATION);
    assertThat(actual.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testSCMInvalidRequest() {
    exception = new SCMRuntimeException("blah", ErrorCode.INVALID_REQUEST);
    Exception actual = scmExceptionHandler.handleException(exception);
    assertThat(actual instanceof HintException).isTrue();
    assertThat(actual.getMessage()).isEqualTo(HintException.HINT_SCM_INVALID_REQUEST);
    assertThat(actual.getCause() instanceof ExplanationException).isTrue();
    assertThat(actual.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testSCMUnexpectedErrorCode() {
    exception = new SCMRuntimeException("blah", ErrorCode.UNEXPECTED);
    Exception actual = scmExceptionHandler.handleException(exception);
    assertThat(actual instanceof InvalidRequestException).isTrue();
    assertThat(actual.getMessage()).isEqualTo("blah");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGitConnectionError() {
    exception = new SCMRuntimeException("blah", ErrorCode.GIT_CONNECTION_ERROR);
    Exception actual = scmExceptionHandler.handleException(exception);
    assertThat(actual instanceof HintException).isTrue();
    assertThat(actual.getMessage()).isEqualTo(HintException.HINT_INVALID_GIT_REPO);
    assertThat(actual.getCause() instanceof ExplanationException).isTrue();
    assertThat(actual.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGitTimeoutError() {
    exception = new SCMRuntimeException("blah", ErrorCode.CONNECTION_TIMEOUT);
    Exception actual = scmExceptionHandler.handleException(exception);
    assertThat(actual instanceof HintException).isTrue();
    assertThat(actual.getMessage()).isEqualTo(HintException.HINT_GIT_CONNECTIVITY);
    assertThat(actual.getCause() instanceof ExplanationException).isTrue();
    assertThat(actual.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInvalidPrivateKey() {
    exception = new SCMRuntimeException("(Hello World) Invalid private key error", ErrorCode.INVALID_KEY);
    Exception ex = scmExceptionHandler.handleException(exception);
    assertThat(ex instanceof HintException).isTrue();
    assertThat(ex.getMessage()).isEqualTo(HintException.HINT_MALFORMED_GIT_SSH_KEY);
    assertThat(ex.getCause() instanceof ExplanationException).isTrue();
    assertThat(ex.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInvalidGitUsername() {
    exception = new SCMRuntimeException("(Hello World) Invalid git username", ErrorCode.SSH_CONNECTION_ERROR);
    Exception ex = scmExceptionHandler.handleException(exception);
    assertThat(ex instanceof HintException).isTrue();
    assertThat(ex.getMessage()).isEqualTo(HintException.HINT_INVALID_GIT_SSH_KEY);
    assertThat(ex.getCause() instanceof ExplanationException).isTrue();
    assertThat(ex.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testAllOtherCases() {
    exception = new SCMRuntimeException("(Hello World) Something went wrong", ErrorCode.GENERAL_ERROR);
    Exception ex = scmExceptionHandler.handleException(exception);
    assertThat(ex instanceof HintException).isTrue();
    assertThat(ex.getMessage()).isEqualTo("Try re-connecting the delegate. Make sure credentials are correct");
    assertThat(ex.getCause() instanceof ExplanationException).isTrue();
    assertThat(ex.getCause().getCause() instanceof InvalidRequestException).isTrue();
  }
}
