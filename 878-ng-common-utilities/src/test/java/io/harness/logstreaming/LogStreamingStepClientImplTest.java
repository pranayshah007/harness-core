/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.rule.OwnerRule.ARVIND;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class LogStreamingStepClientImplTest extends CategoryTest {
  private static final String PREFIX = "prefix";
  private static final String LOG_KEY_SUFFIX = "logKeySuffix";
  private static final String ACCOUNT_ID = "accountId";
  private static final String TOKEN = "Token";
  private static final String BASE_LOG_KEY = "BaseLogKey";
  private static final String LOG_KEY = "BaseLogKey-commandUnit:logKeySuffix";

  private LogStreamingClient logStreamingClient = Mockito.mock(LogStreamingClient.class);
  private ThreadPoolExecutor logStreamingClientExecutor = Mockito.mock(ThreadPoolExecutor.class);
  private LogStreamingSanitizer logStreamingSanitizer = Mockito.mock(LogStreamingSanitizer.class);
  private LogStreamingStepClientImpl logStreamingStepClient = spy(new LogStreamingStepClientImpl(
      logStreamingClient, logStreamingSanitizer, TOKEN, ACCOUNT_ID, BASE_LOG_KEY, logStreamingClientExecutor));
  private static final LogLine LOG_LINE =
      new LogLine(LogLevel.INFO, "Message", Instant.ofEpochMilli(1L), 1, new HashMap<>());
  @Before
  public void before() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testOpenStreamMain() throws Exception {
    logStreamingStepClient.openStream(LOG_KEY_SUFFIX);
    verify(logStreamingClient).openLogStream(TOKEN, ACCOUNT_ID, LOG_KEY);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testOpenStream() throws Exception {
    logStreamingStepClient.openStream(LOG_KEY_SUFFIX);

    verify(logStreamingClient).openLogStream(TOKEN, ACCOUNT_ID, LOG_KEY);
    verifyNoMoreInteractions(logStreamingClient);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCloseStream() throws Exception {
    logStreamingStepClient.closeStream(LOG_KEY_SUFFIX);

    verify(logStreamingClientExecutor).submit(any(Runnable.class));

    // Simulate execution of the submitted task
    Runnable submittedTask = getSubmittedTask();
    submittedTask.run();

    verify(logStreamingClient).closeLogStream(TOKEN, ACCOUNT_ID, LOG_KEY, true);
    verifyNoMoreInteractions(logStreamingClient);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testWriteLogLineSuccess() throws Exception {
    LogLine logLine = LOG_LINE;

    logStreamingStepClient.writeLogLine(logLine, LOG_KEY_SUFFIX);

    verify(logStreamingSanitizer).sanitizeLogMessage(logLine);
    verify(logStreamingClient).pushMessage(TOKEN, ACCOUNT_ID, LOG_KEY, Collections.singletonList(logLine));
    verifyNoMoreInteractions(logStreamingClient);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testWriteLogLineException() throws Exception {
    LogLine logLine = LOG_LINE;

    when(logStreamingClient.pushMessage(anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new RuntimeException());

    logStreamingStepClient.writeLogLine(logLine, LOG_KEY_SUFFIX);

    verify(logStreamingSanitizer).sanitizeLogMessage(logLine);
    verify(logStreamingClient).pushMessage(TOKEN, ACCOUNT_ID, LOG_KEY, Collections.singletonList(logLine));
    verifyNoMoreInteractions(logStreamingClient);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCloseAllOpenStreamsWithPrefix() throws Exception {
    logStreamingStepClient.closeAllOpenStreamsWithPrefix(PREFIX);

    verify(logStreamingClientExecutor).submit(any(Runnable.class));

    // Simulate execution of the submitted task
    Runnable submittedTask = getSubmittedTask();
    submittedTask.run();

    verify(logStreamingClient).closeLogStreamWithPrefix(TOKEN, ACCOUNT_ID, PREFIX, true, true);
    verifyNoMoreInteractions(logStreamingClient);
  }

  private Runnable getSubmittedTask() {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(logStreamingClientExecutor).submit(captor.capture());
    verifyNoMoreInteractions(logStreamingClientExecutor);
    return captor.getValue();
  }
}
