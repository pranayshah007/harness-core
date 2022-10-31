/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.delegate.service.DelegateLogServiceImpl.ACTIVITY_LOGS_TOTAL_SIZE;
import static io.harness.delegate.service.DelegateLogServiceImpl.ACTIVITY_STATUS_LOGLINE_LIMIT;
import static io.harness.delegate.service.DelegateLogServiceImpl.TRUNCATION_MESSAGE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.Log.Builder.aLog;

import static com.google.common.collect.Iterables.getLast;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.managerclient.VerificationServiceClient;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DelegateLogServiceImplTest extends CategoryTest {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private VerificationServiceClient verificationServiceClient;
  @Mock private ExecutorService executorService;
  @Mock private KryoSerializer kryoSerializer;
  private DelegateLogServiceImpl delegateLogService;
  @Captor private ArgumentCaptor<Log> logCaptor;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    delegateLogService = Mockito.spy(new DelegateLogServiceImpl(
        delegateAgentManagerClient, executorService, verificationServiceClient, kryoSerializer));
  }

  /**
   * Test With Single ActivityId
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC1_saveAllIfLessThanMaxSize() {
    List<Log> logs = buildLogs(1000, 20);
    logs.add(buildLog(100, SUCCESS));

    logs.forEach(log -> delegateLogService.save("accountId", log));

    verify(delegateLogService, times(21)).insertLogToCache(eq("accountId"), logCaptor.capture());
    assertLogLinesLength(logCaptor.getAllValues(), 1100);
    String finalLogLine = getLast(logCaptor.getAllValues()).getLogLine();
    assertThat(finalLogLine).isEqualTo(buildLog(100, SUCCESS).getLogLine());
  }

  /**
   * Test With Multiple ActivityIds
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC2_saveAllIfLessThanMaxSize() {
    List<Log> logs = new ArrayList<>();
    logs.addAll(buildLogs(5000, 10, "activityId-1"));
    logs.addAll(buildLogs(5000, 10, "activityId-2"));
    logs.add(buildLog(100, SUCCESS, "activityId-1"));
    logs.addAll(buildLogs(1000, 10, "activityId-2"));
    logs.addAll(buildLogs(2000, 20, "activityId-3"));
    logs.add(buildLog(100, FAILURE, "activityId-2"));
    logs.addAll(buildLogs(200, 5, "activityId-3"));
    logs.add(buildLog(100, SUCCESS, "activityId-3"));

    logs.forEach(log -> delegateLogService.save("accountId", log));
    verify(delegateLogService, times(58)).insertLogToCache(eq("accountId"), logCaptor.capture());
    assertLogLinesLength(logCaptor.getAllValues(), 13500);
  }

  /**
   * Test With Single ActivityId
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC1_saveBoundedIfRunningLogsExceedMaxSize() {
    List<Log> logs = buildLogs(ACTIVITY_LOGS_TOTAL_SIZE, 1000);
    logs.addAll(buildLogs(50000, 250));
    logs.add(buildLog(100, SUCCESS));

    logs.forEach(log -> delegateLogService.save("accountId", log));

    verify(delegateLogService, times(1001)).insertLogToCache(eq("accountId"), logCaptor.capture());
    assertLogLinesLength(logCaptor.getAllValues(), ACTIVITY_LOGS_TOTAL_SIZE + 100 + TRUNCATION_MESSAGE.length());
    String finalLogLine = getLast(logCaptor.getAllValues()).getLogLine();
    assertThat(finalLogLine).isEqualTo(buildLog(100, SUCCESS).getLogLine() + TRUNCATION_MESSAGE);
  }

  /**
   * Test With Multiple ActivityIds
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC2_saveBoundedIfRunningLogsExceedMaxSize() {
    List<Log> logs = new ArrayList<>();
    logs.addAll(buildLogs(ACTIVITY_LOGS_TOTAL_SIZE, 1000, "activityId-1"));
    logs.addAll(buildLogs(50000, 250, "activityId-1"));

    logs.addAll(buildLogs(ACTIVITY_LOGS_TOTAL_SIZE, 1000, "activityId-2"));
    logs.addAll(buildLogs(50000, 250, "activityId-2"));
    logs.add(buildLog(100, SUCCESS, "activityId-1"));

    logs.addAll(buildLogs(ACTIVITY_LOGS_TOTAL_SIZE, 1000, "activityId-3"));
    logs.addAll(buildLogs(50000, 250, "activityId-3"));
    logs.add(buildLog(100, SUCCESS, "activityId-2"));
    logs.add(buildLog(100, SUCCESS, "activityId-3"));

    logs.forEach(log -> delegateLogService.save("accountId", log));

    verify(delegateLogService, times(3 * 1001)).insertLogToCache(eq("accountId"), logCaptor.capture());

    List<Log> activity1Logs = logCaptor.getAllValues()
                                  .stream()
                                  .filter(l -> l.getActivityId().equals("activityId-1"))
                                  .collect(Collectors.toList());
    assertLogLinesLength(activity1Logs, ACTIVITY_LOGS_TOTAL_SIZE + 100 + TRUNCATION_MESSAGE.length());
    String finalLogLine = getLast(activity1Logs).getLogLine();
    assertThat(finalLogLine).isEqualTo(buildLog(100, SUCCESS).getLogLine() + TRUNCATION_MESSAGE);

    List<Log> activity2Logs = logCaptor.getAllValues()
                                  .stream()
                                  .filter(l -> l.getActivityId().equals("activityId-2"))
                                  .collect(Collectors.toList());
    assertLogLinesLength(activity2Logs, ACTIVITY_LOGS_TOTAL_SIZE + 100 + TRUNCATION_MESSAGE.length());
    finalLogLine = getLast(activity2Logs).getLogLine();
    assertThat(finalLogLine).isEqualTo(buildLog(100, SUCCESS).getLogLine() + TRUNCATION_MESSAGE);

    List<Log> activity3Logs = logCaptor.getAllValues()
                                  .stream()
                                  .filter(l -> l.getActivityId().equals("activityId-3"))
                                  .collect(Collectors.toList());
    assertLogLinesLength(activity3Logs, ACTIVITY_LOGS_TOTAL_SIZE + 100 + TRUNCATION_MESSAGE.length());
    finalLogLine = getLast(activity3Logs).getLogLine();
    assertThat(finalLogLine).isEqualTo(buildLog(100, SUCCESS).getLogLine() + TRUNCATION_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void trimFinalLogWhenRunningLogsExceedMaxSize() {
    List<Log> logs = buildLogs(ACTIVITY_LOGS_TOTAL_SIZE, 1000);
    logs.addAll(buildLogs(50000, 250));
    logs.add(buildLog(ACTIVITY_STATUS_LOGLINE_LIMIT + 50, SUCCESS));

    logs.forEach(log -> delegateLogService.save("accountId", log));

    verify(delegateLogService, times(1001)).insertLogToCache(eq("accountId"), logCaptor.capture());
    assertLogLinesLength(logCaptor.getAllValues(),
        ACTIVITY_LOGS_TOTAL_SIZE + ACTIVITY_STATUS_LOGLINE_LIMIT + TRUNCATION_MESSAGE.length());
    String finalLogLine = getLast(logCaptor.getAllValues()).getLogLine();
    assertThat(finalLogLine).hasSize(ACTIVITY_STATUS_LOGLINE_LIMIT + TRUNCATION_MESSAGE.length());
  }

  private void assertLogLinesLength(List<Log> logs, int desiredLength) {
    int actualLength = logs.stream().map(Log::getLogLine).map(String::length).reduce(Integer::sum).orElse(0);
    assertThat(actualLength).isEqualTo(desiredLength);
  }

  private List<Log> buildLogs(int totalLen, int n) {
    List<Log> logs = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      logs.add(buildLog(totalLen / n));
    }
    if (totalLen % n != 0) {
      logs.add(buildLog(totalLen % n));
    }
    return logs;
  }

  private List<Log> buildLogs(int totalLen, int n, String activityId) {
    List<Log> logs = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      logs.add(buildLog(totalLen / n, activityId));
    }
    if (totalLen % n != 0) {
      logs.add(buildLog(totalLen % n, activityId));
    }
    return logs;
  }

  private Log buildLog(int len) {
    return aLog()
        .accountId("accountId")
        .activityId("activityId")
        .appId("appId")
        .commandUnitName("Execute")
        .logLine(sampleString(len))
        .executionResult(RUNNING)
        .build();
  }

  private Log buildLog(int len, CommandExecutionStatus status) {
    return aLog()
        .accountId("accountId")
        .activityId("activityId")
        .appId("appId")
        .commandUnitName("Execute")
        .logLine(sampleString(len))
        .executionResult(status)
        .build();
  }

  private Log buildLog(int len, String activityId) {
    return aLog()
        .accountId("accountId")
        .activityId(activityId)
        .appId("appId")
        .commandUnitName("Execute")
        .logLine(sampleString(len))
        .executionResult(RUNNING)
        .build();
  }

  private Log buildLog(int len, CommandExecutionStatus status, String activityId) {
    return aLog()
        .accountId("accountId")
        .activityId(activityId)
        .appId("appId")
        .commandUnitName("Execute")
        .logLine(sampleString(len))
        .executionResult(status)
        .build();
  }

  private String sampleString(int len) {
    StringBuilder builder = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      builder.append("a");
    }
    return builder.toString();
  }
}
