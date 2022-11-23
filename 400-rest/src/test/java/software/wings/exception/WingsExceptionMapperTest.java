/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.exception;

import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.eraro.ErrorCode.INVALID_ARTIFACT_SOURCE;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.MockableTestMixin;
import io.harness.category.element.UnitTests;
import io.harness.eraro.MessageManager;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;
import org.slf4j.Logger;

public class WingsExceptionMapperTest extends WingsBaseTest implements MockableTestMixin {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void sanity() throws IllegalAccessException {
    final WingsException exception = WingsException.builder().code(DEFAULT_ERROR_CODE).build();
    final WingsExceptionMapper mapper = new WingsExceptionMapper();

    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isWarnEnabled()).thenReturn(true);
    when(mockLogger.isInfoEnabled()).thenReturn(true);

    setStaticFieldValue(WingsExceptionMapper.class, "log", mockLogger);

    mapper.toResponse(exception);

    InOrder inOrder = inOrder(mockLogger);
    inOrder.verify(mockLogger)
        .warn("Response message: An error has occurred. Please contact the Harness support team.\n"
                + "Exception occurred: DEFAULT_ERROR_CODE",
            exception);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void missingParameter() throws IllegalAccessException {
    final WingsException exception = new WingsException(INVALID_ARTIFACT_SOURCE, USER);
    final WingsExceptionMapper mapper = new WingsExceptionMapper();

    Logger mockLogger = mock(Logger.class);
    setStaticFieldValue(MessageManager.class, "log", mockLogger);

    mapper.toResponse(exception);
    verify(mockLogger, times(2))
        .info("Insufficient parameter from [{}] in message \"{}\"", "", "Invalid Artifact Source: ${name}.${reason}");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void overrideMessage() throws IllegalAccessException {
    final WingsException exception =
        WingsException.builder().message("Override message").code(DEFAULT_ERROR_CODE).build();
    final WingsExceptionMapper mapper = new WingsExceptionMapper();

    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isWarnEnabled()).thenReturn(true);
    when(mockLogger.isInfoEnabled()).thenReturn(true);
    setStaticFieldValue(WingsExceptionMapper.class, "log", mockLogger);
    setStaticFieldValue(MessageManager.class, "log", mockLogger);

    mapper.toResponse(exception);

    InOrder inOrder = inOrder(mockLogger);
    inOrder.verify(mockLogger)
        .warn("Response message: An error has occurred. Please contact the Harness support team.\n"
                + "Exception occurred: Override message",
            exception);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotLogHarmless() throws IllegalAccessException {
    final WingsException exception = new WingsException(DEFAULT_ERROR_CODE, USER);
    final WingsExceptionMapper mapper = new WingsExceptionMapper();

    Logger mockLogger = mock(Logger.class);
    setStaticFieldValue(WingsExceptionMapper.class, "log", mockLogger);

    mapper.toResponse(exception);

    InOrder inOrder = inOrder(mockLogger);
    inOrder.verify(mockLogger, never()).error(any());
    inOrder.verify(mockLogger, never()).error(any(), (Object) anyObject());
    inOrder.verify(mockLogger, never()).error(any(), (Throwable) any());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void recursiveParamTest() throws IllegalAccessException {
    assertThatCode(() -> {
      WingsException exception = new WingsException(VAULT_OPERATION_ERROR, USER);
      exception.addParam("reason", "recursive call to ${reason}");

      Logger mockLogger = mock(Logger.class);
      setStaticFieldValue(WingsExceptionMapper.class, "log", mockLogger);

      final WingsExceptionMapper mapper = new WingsExceptionMapper();
      mapper.toResponse(exception); // should not throw.
    }).doesNotThrowAnyException();
  }
}
