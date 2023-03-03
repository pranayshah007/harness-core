/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stackdriver;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.resources.DelegateStackDriverLog;
import io.harness.logging.AccessTokenBean;
import io.harness.logging.StackdriverLoggerFactory;
import io.harness.ng.beans.PageRequest;
import io.harness.rule.Owner;
import io.harness.service.impl.stackdriver.DelegateStackdriverLogServiceImpl;
import io.harness.service.impl.stackdriver.EpochToUTCConverter;
import io.harness.service.intfc.DelegateStackdriverLogService;

import software.wings.service.impl.infra.InfraDownloadService;

import com.google.api.gax.paging.Page;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Payload.StringPayload;
import com.google.cloud.logging.Severity;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
public class DelegateStackdriverLogServiceTest {
  private final long timestamp = 1677025536729L;
  private final Map<String, String> labels = Map.of("app", "delegate", "accountId", "04Iq9MDcT9WOBwwS6C4oKw",
      "delegateId", "O8iFplPwS8iX8SfJbpg9BA", "managerHost", "app.harness.io/gratis", "version", "22.12.77617-000",
      "source", "general-us-5b86d45c5f-zc6hh", "processId", "1");
  private final Map<String, Object> payloadMap =
      Map.of("logger", "io.harness.delegate.service.DelegateAgentServiceImpl", "message", "example log message",
          "thread", "task-exec-7", "harness", Map.of("taskId", "abc_task", "accountId", "O8iFplPwS8iX8SfJbpg9BA"));
  private final String stringLogMessage = "I have an exception here";

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testFetchDelegateLogs() {
    InfraDownloadService downloadService = mock(InfraDownloadService.class);
    AccessTokenBean tokenBean = AccessTokenBean.builder().build();
    when(downloadService.getStackdriverLoggingToken()).thenReturn(tokenBean);
    DelegateStackdriverLogService delegateStackdriverLogService =
        new DelegateStackdriverLogServiceImpl(downloadService);
    Logging logging = mock(Logging.class);
    Page<LogEntry> page = mock(Page.class);
    when(page.iterateAll()).thenReturn(getMockedLogEntries());
    when(logging.listLogEntries(any())).thenReturn(page);

    try (MockedStatic<StackdriverLoggerFactory> logger = Mockito.mockStatic(StackdriverLoggerFactory.class)) {
      logger.when(() -> StackdriverLoggerFactory.get(tokenBean)).thenReturn(logging);
      var response = delegateStackdriverLogService.fetchPageLogs(
          "any_id", List.of(), mock(PageRequest.class), 1675807200L, 1675808100L);
      List<DelegateStackDriverLog> content = response.getContent();
      assertThat(content.size()).isEqualTo(2);

      // Verify log 1 - json log
      var log1 = content.get(0);
      assertThat(log1.getISOTime()).isEqualTo(EpochToUTCConverter.fromEpoch(timestamp / 1000L));
      assertThat(log1.getSeverity()).isEqualTo(Severity.INFO.name());
      assertThat(log1.getMessage()).isEqualTo(payloadMap.get("message").toString());
      verifyPayloadAndLabel(log1);

      // Verify log 2 - string log
      var log2 = content.get(1);
      assertThat(log2.getISOTime()).isEqualTo(EpochToUTCConverter.fromEpoch(timestamp / 1000L));
      assertThat(log2.getSeverity()).isEqualTo(Severity.ERROR.name());
      assertThat(log2.getMessage()).isEqualTo(stringLogMessage);
    }
  }

  private List<LogEntry> getMockedLogEntries() {
    JsonPayload jsonPayload = JsonPayload.of(payloadMap);
    StringPayload stringPayload = StringPayload.of(stringLogMessage);
    LogEntry.Builder jsonLog = LogEntry.newBuilder(jsonPayload).setSeverity(Severity.INFO).setTimestamp(timestamp);
    LogEntry.Builder stringLog = LogEntry.newBuilder(stringPayload).setSeverity(Severity.ERROR).setTimestamp(timestamp);
    labels.forEach((key, val) -> {
      jsonLog.addLabel(key, val);
      stringLog.addLabel(key, val);
    });
    return List.of(jsonLog.build(), stringLog.build());
  }

  private void verifyPayloadAndLabel(DelegateStackDriverLog log) {
    assertThat(log.getDelegateId()).isEqualTo(labels.get("delegateId"));
    assertThat(log.getLogger()).isEqualTo(payloadMap.get("logger").toString());
    assertThat(log.getThread()).isEqualTo(payloadMap.get("thread").toString());
    assertThat(log.getApp()).isEqualTo(labels.get("app"));
    assertThat(log.getAccountId()).isEqualTo(((Map<String, String>) payloadMap.get("harness")).get("accountId"));
    assertThat(log.getTaskId()).isEqualTo(((Map<String, String>) payloadMap.get("harness")).get("taskId"));
    assertThat(log.getVersion()).isEqualTo(labels.get("version"));
    assertThat(log.getSource()).isEqualTo(labels.get("source"));
    assertThat(log.getManagerHost()).isEqualTo(labels.get("managerHost"));
    assertThat(log.getProcessId()).isEqualTo(labels.get("processId"));
  }
}
