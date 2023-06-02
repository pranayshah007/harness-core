/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.dto.CVActivityLog.CVActivityLogKeys;
import software.wings.beans.dto.CVActivityLog.LogLevel;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.verification.CVActivityLog;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVActivityLogServiceTest extends WingsBaseTest {
  @Inject CVActivityLogService cvActivityLogService;
  @Inject private HPersistence persistence;

  private String stateExecutionId;
  private String accountId;

  @Before
  public void setupTests() {
    accountId = generateUuid();
    stateExecutionId = generateUuid();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSavingLog() {
    String cvConfigId = generateUuid();
    long now = System.currentTimeMillis();

    cvActivityLogService.getLoggerByCVConfigId(accountId, cvConfigId, TimeUnit.MILLISECONDS.toMinutes(now))
        .info("activity log from test");
    CVActivityLog cvActivityLog =
        persistence.createQuery(CVActivityLog.class).filter(CVActivityLogKeys.cvConfigId, cvConfigId).get();
    assertThat(cvActivityLog.getLog()).isEqualTo("activity log from test");
    assertThat(cvActivityLog.getDataCollectionMinute()).isEqualTo(TimeUnit.MILLISECONDS.toMinutes(now));
    assertThat(cvActivityLog.getTimestampParams()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSavingLogWithTimestampParams() {
    String cvConfigId = generateUuid();
    long now = System.currentTimeMillis();
    long nextMinute = Instant.ofEpochMilli(now).plus(1, ChronoUnit.MINUTES).toEpochMilli();
    cvActivityLogService.getLoggerByCVConfigId(accountId, cvConfigId, TimeUnit.MILLISECONDS.toMinutes(now))
        .info("activity log from test at minute %t and another minute %t", now, nextMinute);
    CVActivityLog cvActivityLog =
        persistence.createQuery(CVActivityLog.class).filter(CVActivityLogKeys.cvConfigId, cvConfigId).get();
    assertThat(cvActivityLog.getTimestampParams()).hasSize(2);
    assertThat((long) cvActivityLog.getTimestampParams().get(1)).isEqualTo(nextMinute);
    assertThat((long) cvActivityLog.getTimestampParams().get(0)).isEqualTo(now);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetAnsi() {
    String cvConfigId = generateUuid();
    long now = System.currentTimeMillis();
    cvActivityLogService.getLoggerByCVConfigId(accountId, cvConfigId, TimeUnit.MILLISECONDS.toMinutes(now))
        .error("activity log from test");
    CVActivityLog cvActivityLog =
        persistence.createQuery(CVActivityLog.class).filter(CVActivityLogKeys.cvConfigId, cvConfigId).get();
    assertThat(cvActivityLog.getAnsiLog()).isEqualTo("\u001B[1;91m\u001B[40mactivity log from test\u001B[0m");

    cvConfigId = generateUuid();
    cvActivityLogService.getLoggerByCVConfigId(accountId, cvConfigId, TimeUnit.MILLISECONDS.toMinutes(now))
        .info("activity log from test");
    cvActivityLog = persistence.createQuery(CVActivityLog.class).filter(CVActivityLogKeys.cvConfigId, cvConfigId).get();
    assertThat(cvActivityLog.getAnsiLog()).isEqualTo("activity log from test");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByCVConfigIdToReturnEmptyIfNoLogs() {
    String cvConfigId = generateUuid();
    assertThat(cvActivityLogService.findByCVConfigId(
                   cvConfigId, 0, TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis())))
        .isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByCVConfigIdWithSameStartTimeAndEndTime() {
    String cvConfigId = generateUuid();
    long nowMilli = System.currentTimeMillis();
    String logLine = "test log message: " + generateUuid();
    long nowMinute = TimeUnit.MILLISECONDS.toMinutes(nowMilli);
    createLog(cvConfigId, nowMinute, logLine);
    createLog(cvConfigId, nowMinute + 1, "log line");
    assertThat(cvActivityLogService.findByCVConfigId(cvConfigId, nowMinute, nowMinute - 1))
        .isEqualTo(Collections.emptyList());
    List<CVActivityLog> activityLogs = cvActivityLogService.findByCVConfigId(cvConfigId, nowMinute, nowMinute);
    assertThat(activityLogs).hasSize(1);
    assertThat(activityLogs.get(0).getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(activityLogs.get(0).getStateExecutionId()).isNull();
    assertThat(activityLogs.get(0).getDataCollectionMinute()).isEqualTo(nowMinute);
    assertThat(activityLogs.get(0).getLog()).isEqualTo(logLine);
  }

  @Test

  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByStateExecutionId() {
    String stateExecutionId = generateUuid();
    String logLine = "test log message: " + generateUuid();
    cvActivityLogService.getLoggerByStateExecutionId(accountId, stateExecutionId).info(logLine);
    List<CVActivityLog> activityLogs = cvActivityLogService.findByStateExecutionId(stateExecutionId);
    assertThat(activityLogs).hasSize(1);
    assertThat(activityLogs.get(0).getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(activityLogs.get(0).getCvConfigId()).isNull();
    assertThat(activityLogs.get(0).getDataCollectionMinute()).isEqualTo(0);
    assertThat(activityLogs.get(0).getLog()).isEqualTo(logLine);
    assertThat(activityLogs.get(0).getLogLevel()).isEqualTo(LogLevel.INFO);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByCVConfigIdWithDiffSameStartTimeAndEndTime() {
    String cvConfigId = generateUuid();
    long now = System.currentTimeMillis();
    String logLine1 = "test log message: " + generateUuid();
    String logLine2 = "test log message: " + generateUuid();

    long nowMinute = TimeUnit.MILLISECONDS.toMinutes(now);
    createLog(cvConfigId, nowMinute, logLine1);
    createLog(cvConfigId, nowMinute + 1, logLine2);

    List<CVActivityLog> activityLogs = cvActivityLogService.findByCVConfigId(cvConfigId, nowMinute, nowMinute + 1);
    assertThat(activityLogs).hasSize(2);
    assertThat(activityLogs.get(0).getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(activityLogs.get(0).getDataCollectionMinute()).isEqualTo(nowMinute);
    assertThat(activityLogs.get(0).getLog()).isEqualTo(logLine1);
    assertThat(activityLogs.get(1).getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(activityLogs.get(1).getDataCollectionMinute()).isEqualTo(nowMinute + 1);
    assertThat(activityLogs.get(1).getLog()).isEqualTo(logLine2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveActivityLogs() {
    List<CVActivityLog> cvActivityLogs =
        IntStream.range(0, 10).mapToObj(i -> getActivityLog()).collect(Collectors.toList());
    cvActivityLogService.saveActivityLogs(cvActivityLogs);
    List<CVActivityLog> savedActivityLogs = cvActivityLogService.findByStateExecutionId(stateExecutionId);
    assertThat(cvActivityLogs).isEqualTo(savedActivityLogs);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetActivityLogs_whenNoAnalysisContextAvailable() {
    List<CVActivityLog> result = cvActivityLogService.getActivityLogs(accountId, stateExecutionId, null, 0, 0);
    assertThat(result).hasSize(1);
    CVActivityLog placeholder = result.get(0);
    assertThat(placeholder.getDataCollectionMinute()).isEqualTo(0);
    assertThat(placeholder.getLog()).isEqualTo("Execution logs are not available for old executions");
    assertThat(placeholder.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(placeholder.getLogLevel()).isEqualTo(LogLevel.INFO);
    assertThat(placeholder.getTimestampParams()).isEmpty();
    assertThat(placeholder.getAnsiLog()).isEqualTo("Execution logs are not available for old executions");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetActivityLogs_whenNoActivityLogsAndStateIsInFinalState() throws IllegalAccessException {
    WorkflowService workflowService = mock(WorkflowService.class);
    FieldUtils.writeField(cvActivityLogService, "workflowService", workflowService, true);
    String appId = generateUuid();
    AnalysisContext analysisContext =
        AnalysisContext.builder().stateExecutionId(stateExecutionId).appId(appId).accountId(generateUuid()).build();
    persistence.save(analysisContext);
    when(workflowService.getExecutionStatus(eq(appId), eq(stateExecutionId))).thenReturn(ExecutionStatus.FAILED);
    List<CVActivityLog> result = cvActivityLogService.getActivityLogs(accountId, stateExecutionId, null, 0, 0);
    assertThat(result).hasSize(1);
    CVActivityLog placeholder = result.get(0);
    assertThat(placeholder.getDataCollectionMinute()).isEqualTo(0);
    assertThat(placeholder.getLog()).isEqualTo("Execution logs are not available for old executions");
    assertThat(placeholder.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(placeholder.getLogLevel()).isEqualTo(LogLevel.INFO);
    assertThat(placeholder.getTimestampParams()).isEmpty();
    assertThat(placeholder.getAnsiLog()).isEqualTo("Execution logs are not available for old executions");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetActivityLogs_whenNoActivityLogsAndStateIsNotInFinalState() throws IllegalAccessException {
    WorkflowService workflowService = mock(WorkflowService.class);
    FieldUtils.writeField(cvActivityLogService, "workflowService", workflowService, true);
    String appId = generateUuid();
    AnalysisContext analysisContext =
        AnalysisContext.builder().stateExecutionId(stateExecutionId).appId(appId).accountId(generateUuid()).build();
    persistence.save(analysisContext);
    when(workflowService.getExecutionStatus(eq(appId), eq(stateExecutionId))).thenReturn(ExecutionStatus.RUNNING);
    List<CVActivityLog> result = cvActivityLogService.getActivityLogs(accountId, stateExecutionId, null, 0, 0);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetActivityLogs_withFindByCVConfigId() {
    String cvConfigId = generateUuid();
    long nowMilli = System.currentTimeMillis();
    String logLine = "test log message: " + generateUuid();
    long nowMinute = TimeUnit.MILLISECONDS.toMinutes(nowMilli);
    createLog(cvConfigId, nowMinute, logLine);
    createLog(cvConfigId, nowMinute + 1, "log line");
    assertThat(cvActivityLogService.findByCVConfigId(cvConfigId, nowMinute, nowMinute - 1))
        .isEqualTo(Collections.emptyList());
    List<CVActivityLog> activityLogs =
        cvActivityLogService.getActivityLogs(accountId, null, cvConfigId, nowMinute, nowMinute);
    assertThat(activityLogs).hasSize(1);
    assertThat(activityLogs.get(0).getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(activityLogs.get(0).getStateExecutionId()).isNull();
    assertThat(activityLogs.get(0).getDataCollectionMinute()).isEqualTo(nowMinute);
    assertThat(activityLogs.get(0).getLog()).isEqualTo(logLine);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testIfCVTaskValidUntilIsBeingSetTo2Weeks() {
    CVActivityLog cvActivityLog = createLog(UUID.randomUUID().toString(), System.currentTimeMillis(), "Test log");
    assertThat(cvActivityLog.getValidUntil().getTime() > Instant.now().toEpochMilli()).isTrue();
    assertThat(Math.abs(cvActivityLog.getValidUntil().getTime()
                   - OffsetDateTime.now().plus(2, ChronoUnit.WEEKS).toInstant().toEpochMilli())
        < TimeUnit.DAYS.toMillis(1));
  }

  private CVActivityLog createLog(String cvConfigId, long dataCollectionMinute, String logLine) {
    CVActivityLog cvActivityLog = CVActivityLog.builder()
                                      .cvConfigId(cvConfigId)
                                      .dataCollectionMinute(dataCollectionMinute)
                                      .log(logLine)
                                      .logLevel(LogLevel.INFO)
                                      .build();
    persistence.save(cvActivityLog);
    return cvActivityLog;
  }

  private CVActivityLog getActivityLog() {
    return CVActivityLog.builder()
        .stateExecutionId(stateExecutionId)
        .logLevel(LogLevel.INFO)
        .log("test log: " + generateUuid())
        .build();
  }
}
