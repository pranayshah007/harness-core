/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MARKO;

import static software.wings.beans.LogHelper.COMMAND_UNIT_PLACEHOLDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class LogStreamingTaskClientTest extends CategoryTest {
  private final DelegateLogService logServiceMock = mock(DelegateLogService.class);
  private final LogStreamingClient logStreamingClientMock = mock(LogStreamingClient.class);
  private final LogStreamingSanitizer logStreamingSanitizerMock = mock(LogStreamingSanitizer.class);

  private static final String ACCOUNT_ID = generateUuid();
  private static final String TOKEN = generateUuid();
  private static final String BASE_LOG_KEY = generateUuid();
  private static final String APP_ID = generateUuid();
  private static final String ACTIVITY_ID = generateUuid();

  private LogStreamingTaskClient completeLogStreamingTaskClient = LogStreamingTaskClient.builder()
                                                                      .logStreamingClient(logStreamingClientMock)
                                                                      .accountId(ACCOUNT_ID)
                                                                      .token(TOKEN)
                                                                      .logStreamingSanitizer(logStreamingSanitizerMock)
                                                                      .baseLogKey(BASE_LOG_KEY)
                                                                      .logService(logServiceMock)
                                                                      .appId(APP_ID)
                                                                      .activityId(ACTIVITY_ID)
                                                                      .build();

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokeOpenLogStreamWithoutKeySuffix() {
    completeLogStreamingTaskClient.openStream(null);
    verify(logStreamingClientMock).openLogStream(TOKEN, ACCOUNT_ID, BASE_LOG_KEY);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokeOpenLogStreamWithKeySuffix() {
    completeLogStreamingTaskClient.openStream("keySuffix");
    verify(logStreamingClientMock)
        .openLogStream(TOKEN, ACCOUNT_ID, BASE_LOG_KEY + String.format(COMMAND_UNIT_PLACEHOLDER, "keySuffix"));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokeCloseLogStreamWithoutKeySuffix() {
    completeLogStreamingTaskClient.openStream(null);
    completeLogStreamingTaskClient.closeStream(null);
    verify(logStreamingClientMock).closeLogStream(TOKEN, ACCOUNT_ID, BASE_LOG_KEY, true);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokeCloseLogStreamWithKeySuffix() {
    completeLogStreamingTaskClient.openStream("keySuffix");
    completeLogStreamingTaskClient.closeStream("keySuffix");
    verify(logStreamingClientMock)
        .closeLogStream(TOKEN, ACCOUNT_ID, BASE_LOG_KEY + String.format(COMMAND_UNIT_PLACEHOLDER, "keySuffix"), true);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldFailWritingLogsWithException() {
    assertThatThrownBy(() -> completeLogStreamingTaskClient.writeLogLine(null, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Log line parameter is mandatory.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokePushMessageWithoutKeySuffix() {
    LogLine logLine = LogLine.builder().level(LogLevel.INFO).message("msg").build();

    completeLogStreamingTaskClient.writeLogLine(logLine, null);
    completeLogStreamingTaskClient.dispatchLogs();

    verify(logStreamingSanitizerMock).sanitizeLogMessage(logLine);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(logStreamingClientMock).pushMessage(eq(TOKEN), eq(ACCOUNT_ID), eq(BASE_LOG_KEY), captor.capture());
    List logLines = captor.getValue();
    assertThat(logLines).containsExactly(logLine);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokePushMessageWithKeySuffix() {
    LogLine logLine = LogLine.builder().level(LogLevel.INFO).message("msg").build();
    completeLogStreamingTaskClient.writeLogLine(logLine, "keySuffix");
    completeLogStreamingTaskClient.dispatchLogs();

    verify(logStreamingSanitizerMock).sanitizeLogMessage(logLine);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(logStreamingClientMock)
        .pushMessage(eq(TOKEN), eq(ACCOUNT_ID), eq(BASE_LOG_KEY + String.format(COMMAND_UNIT_PLACEHOLDER, "keySuffix")),
            captor.capture());
    List logLines = captor.getValue();
    assertThat(logLines).containsExactly(logLine);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldReturnExecutionLogCallbackInstance() {
    LogCallback logCallback = completeLogStreamingTaskClient.obtainLogCallback("commandName");

    assertThat(logCallback).isNotNull();
    assertThat(logCallback).isInstanceOf(ExecutionLogCallback.class);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenAppIdOrActivityIdAreNotPresent() {
    // Test no appId scenario
    LogStreamingTaskClient logStreamingTaskClientWithoutAppId = LogStreamingTaskClient.builder()
                                                                    .logStreamingClient(logStreamingClientMock)
                                                                    .accountId(ACCOUNT_ID)
                                                                    .token(TOKEN)
                                                                    .logStreamingSanitizer(logStreamingSanitizerMock)
                                                                    .baseLogKey(BASE_LOG_KEY)
                                                                    .logService(logServiceMock)
                                                                    .activityId(ACTIVITY_ID)
                                                                    .build();

    assertThatThrownBy(() -> logStreamingTaskClientWithoutAppId.obtainLogCallback("commandName"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Application id and activity id were not available as part of task params. Please make sure that task params class implements Cd1ApplicationAccess and ActivityAccess interfaces.");

    // Test no activityId scenario
    LogStreamingTaskClient logStreamingTaskClientWithoutActivityId =
        LogStreamingTaskClient.builder()
            .logStreamingClient(logStreamingClientMock)
            .accountId(ACCOUNT_ID)
            .token(TOKEN)
            .logStreamingSanitizer(logStreamingSanitizerMock)
            .baseLogKey(BASE_LOG_KEY)
            .logService(logServiceMock)
            .appId(APP_ID)
            .build();

    assertThatThrownBy(() -> logStreamingTaskClientWithoutActivityId.obtainLogCallback("commandName"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Application id and activity id were not available as part of task params. Please make sure that task params class implements Cd1ApplicationAccess and ActivityAccess interfaces.");
  }
}
