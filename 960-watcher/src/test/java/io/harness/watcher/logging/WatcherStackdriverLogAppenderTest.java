/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.logging;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.RemoteStackdriverLogAppender.MIN_BATCH_SIZE;
import static io.harness.logging.RemoteStackdriverLogAppender.logLevelToSeverity;
import static io.harness.rule.OwnerRule.BRETT;

import static ch.qos.logback.classic.Level.INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.AccessTokenBean;
import io.harness.managerclient.ManagerClientV2;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response.Builder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.DEL)
public class WatcherStackdriverLogAppenderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ManagerClientV2 managerClient;
  @Mock private Call<RestResponse<AccessTokenBean>> callAccessTokenBean;

  private WatcherStackdriverLogAppender appender = new WatcherStackdriverLogAppender();

  private final TimeLimiter timeLimiter = new FakeTimeLimiter();
  private final Logger logger = new LoggerContext().getLogger(WatcherStackdriverLogAppenderTest.class);
  private final RestResponse<AccessTokenBean> accessTokenBeanRestResponse =
      new RestResponse<>(AccessTokenBean.builder()
                             .projectId("project-id")
                             .tokenValue("token-value")
                             .expirationTimeMillis(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
                             .build());
  private final okhttp3.Response rawResponse = new Builder()
                                                   .protocol(Protocol.HTTP_2)
                                                   .code(200)
                                                   .message("")
                                                   .request(new Request.Builder().url("http://test.harness.io").build())
                                                   .build();

  @Before
  public void setUp() throws Exception {
    when(managerClient.getLoggingToken(anyString())).thenReturn(callAccessTokenBean);
    when(callAccessTokenBean.execute()).thenReturn(Response.success(accessTokenBeanRestResponse, rawResponse));
    WatcherStackdriverLogAppender.setManagerClient(managerClient);
    WatcherStackdriverLogAppender.setTimeLimiter(timeLimiter);
    appender.start();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAppend() {
    String message = "my log message";
    appendLog(INFO, message);
    waitForMessage(INFO, message);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldSubmit() {
    String message = "my log message";
    for (int i = 0; i < MIN_BATCH_SIZE; ++i) {
      appendLog(INFO, message);
    }
    waitForMessage(INFO, message);
    BlockingQueue<LogEntry> logQueue = appender.getLogQueue();
    await().atMost(30L, TimeUnit.SECONDS).until(logQueue::isEmpty);
  }

  private void appendLog(Level level, String message) {
    appender.doAppend(new LoggingEvent("a.b.class", logger, level, message, null, null));
  }

  private void waitForMessage(Level level, String message) {
    BlockingQueue<LogEntry> logQueue = appender.getLogQueue();
    await().atMost(5L, TimeUnit.SECONDS).until(() -> {
      if (isEmpty(logQueue)) {
        return false;
      }

      for (LogEntry entry : logQueue) {
        if (entry == null) {
          continue;
        }
        if (entry.getSeverity() != logLevelToSeverity(level)) {
          continue;
        }
        Map<String, ?> jsonMap = ((JsonPayload) entry.getPayload()).getDataAsMap();
        if (jsonMap.get("message").equals(message)) {
          assertThat(jsonMap.get("logger")).isEqualTo("io.harness.watcher.logging.WatcherStackdriverLogAppenderTest");
          return true;
        }
      }

      return false;
    });
  }
}
