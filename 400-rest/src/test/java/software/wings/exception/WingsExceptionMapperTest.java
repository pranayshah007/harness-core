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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.MockableTestMixin;
import io.harness.category.element.UnitTests;
import io.harness.eraro.MessageManager;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.LoggerFactory;

public class WingsExceptionMapperTest extends WingsBaseTest implements MockableTestMixin {
  private <T> ListAppender<ILoggingEvent> initLogger(Class<T> aClass) {
    Logger logger = (Logger) LoggerFactory.getLogger(aClass);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    return listAppender;
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void sanity() throws IllegalAccessException {
    final WingsException exception = WingsException.builder().code(DEFAULT_ERROR_CODE).build();
    final WingsExceptionMapper mapper = new WingsExceptionMapper();
    ListAppender<ILoggingEvent> listAppender = initLogger(WingsExceptionMapper.class);

    mapper.toResponse(exception);

    assertThat(listAppender.list.get(0).getFormattedMessage())
        .isEqualTo("Response message: An error has occurred. Please contact the Harness support team.\n"
            + "Exception occurred: DEFAULT_ERROR_CODE");
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void missingParameter() throws IllegalAccessException {
    final WingsException exception = new WingsException(INVALID_ARTIFACT_SOURCE, USER);
    final WingsExceptionMapper mapper = new WingsExceptionMapper();

    ListAppender<ILoggingEvent> listAppender = initLogger(MessageManager.class);

    mapper.toResponse(exception);
    assertThat(listAppender.list).hasSize(2);
    String expectedMessage = String.format(
        "Insufficient parameter from [%s] in message \"%s\"", "", "Invalid Artifact Source: ${name}.${reason}");
    listAppender.list.forEach(
        loggingEvent -> assertThat(loggingEvent.getFormattedMessage()).isEqualTo(expectedMessage));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void overrideMessage() throws IllegalAccessException {
    final WingsException exception =
        WingsException.builder().message("Override message").code(DEFAULT_ERROR_CODE).build();
    final WingsExceptionMapper mapper = new WingsExceptionMapper();

    ListAppender<ILoggingEvent> wingsMapperAppender = initLogger(WingsExceptionMapper.class);
    ListAppender<ILoggingEvent> messageManagerAppender = initLogger(MessageManager.class);

    mapper.toResponse(exception);
    assertThat(messageManagerAppender.list).hasSize(0);
    assertThat(wingsMapperAppender.list.get(0).getFormattedMessage())
        .isEqualTo("Response message: An error has occurred. Please contact the Harness support team.\n"
            + "Exception occurred: Override message");
    assertThat(wingsMapperAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotLogHarmless() throws IllegalAccessException {
    final WingsException exception = new WingsException(DEFAULT_ERROR_CODE, USER);
    final WingsExceptionMapper mapper = new WingsExceptionMapper();
    ListAppender<ILoggingEvent> listAppender = initLogger(WingsExceptionMapper.class);

    mapper.toResponse(exception);

    assertThat(listAppender.list).hasSizeGreaterThan(0);
    assertThat(listAppender.list.stream().map(ILoggingEvent::getLevel).collect(Collectors.toList()))
        .doesNotContain(Level.ERROR);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void recursiveParamTest() throws IllegalAccessException {
    assertThatCode(() -> {
      WingsException exception = new WingsException(VAULT_OPERATION_ERROR, USER);
      exception.addParam("reason", "recursive call to ${reason}");

      final WingsExceptionMapper mapper = new WingsExceptionMapper();
      mapper.toResponse(exception); // should not throw.
    }).doesNotThrowAnyException();
  }
}
