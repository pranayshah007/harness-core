/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static java.lang.Thread.sleep;
import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.entities.CVTask;
import io.harness.entities.CVTask.CVTaskKeys;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.service.intfc.verification.CVTaskService;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CVTaskServiceTest extends VerificationBase {
  @Inject CVTaskService cvTaskService;
  @Inject WingsPersistence wingsPersistence;
  private String stateExecutionId;
  private String cvConfigId;
  private String accountId;

  @Mock VerificationManagerClientHelper verificationManagerClientHelper;

  @Before
  public void setupTests() throws IllegalAccessException {
    initMocks(this);
    accountId = generateUUID();
    stateExecutionId = generateUUID();
    cvConfigId = generateUUID();

    cvTaskService = Mockito.spy(cvTaskService);

    FieldUtils.writeField(cvTaskService, "verificationManagerClientHelper", verificationManagerClientHelper, true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveCVTask() {
    CVTask cvTask = createCVTaskWithStateExecutionId();
    cvTaskService.saveCVTask(cvTask);
    assertThat(cvTask.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
    CVTask updatedCVTask = getCVTask(cvTask.getUuid());
    assertThat(updatedCVTask.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidAfterWhenNotSetOnGettingNextTask() {
    CVTask cvTask = createCVTaskWithStateExecutionId();
    cvTaskService.saveCVTask(cvTask);
    Optional<CVTask> nextTask = cvTaskService.getNextTask(cvTask.getAccountId());
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(cvTask.getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidAfterWhenSetToFutureOnGettingNextTask() {
    CVTask cvTask = createCVTaskWithStateExecutionId();
    cvTask.setValidAfter(Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli());
    cvTaskService.saveCVTask(cvTask);
    Optional<CVTask> nextTask = cvTaskService.getNextTask(cvTask.getAccountId());
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidAfterWhenSetToPastOnGettingNextTask() {
    CVTask cvTask = createCVTaskWithStateExecutionId();
    cvTask.setValidAfter(Instant.now().minus(10, ChronoUnit.MINUTES).toEpochMilli());
    cvTaskService.saveCVTask(cvTask);
    Optional<CVTask> nextTask = cvTaskService.getNextTask(cvTask.getAccountId());
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(cvTask.getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecutionStatusFilterOnGettingNextTask() {
    CVTask cvTask = createCVTaskWithStateExecutionId();
    cvTask.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(cvTask);
    Optional<CVTask> nextTask = cvTaskService.getNextTask(cvTask.getAccountId());
    assertThat(nextTask.isPresent()).isFalse();
    cvTask.setStatus(ExecutionStatus.QUEUED);
    wingsPersistence.save(cvTask);
    nextTask = cvTaskService.getNextTask(cvTask.getAccountId());
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(cvTask.getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecutionStatusUpdateOnGettingNextTask() {
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId();
    Optional<CVTask> nextTask = cvTaskService.getNextTask(cvTask.getAccountId());
    assertThat(nextTask.get().getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    CVTask reloadedCVTask = getCVTask(cvTask.getUuid());
    assertThat(reloadedCVTask.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testOrderOnGettingNextTask() throws InterruptedException {
    List<CVTask> cvTasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      cvTasks.add(createAndSaveCVTaskWithStateExecutionId());
      sleep(1); // make sure last updated at is different for each cvTask.
    }
    cvTasks.get(0).setCorrelationId(generateUUID()); // update first
    wingsPersistence.save(cvTasks.get(0));
    for (int i = 1; i < 10; i++) {
      Optional<CVTask> nextTask = cvTaskService.getNextTask(accountId);
      assertThat(nextTask.isPresent()).isTrue();
      assertThat(nextTask.get().getUuid()).isEqualTo(cvTasks.get(i).getUuid());
    }
    Optional<CVTask> nextTask = cvTaskService.getNextTask(accountId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(cvTasks.get(0).getUuid());
    assertThat(cvTaskService.getNextTask(accountId).isPresent()).isFalse();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testIfCVTaskValidUntilIsBeingSetToOneMonth() {
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId();
    assertThat(cvTask.getValidUntil().getTime() > Instant.now().toEpochMilli()).isTrue();
    assertThat(cvTask.getValidUntil().getTime() >= Instant.now().plus(29, ChronoUnit.DAYS).toEpochMilli()).isTrue();
    assertThat(cvTask.getValidUntil().getTime() <= Instant.now().plus(31, ChronoUnit.DAYS).toEpochMilli()).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCVTaskById() {
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId();
    assertThat(cvTask.getUuid()).isEqualTo(cvTaskService.getCVTask(cvTask.getUuid()).getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testEnqueueSeqTasks() {
    long startTime = Instant.now().toEpochMilli();
    List<CVTask> cvTasks = new ArrayList<>();
    for (int minute = 0; minute < 10; minute++) {
      long startTimeMSForCurrentMinute = startTime + Duration.ofMinutes(minute).toMillis();
      CVTask cvTask = CVTask.builder()
                          .accountId(generateUUID())
                          .stateExecutionId(generateUUID())
                          .correlationId(generateUUID())
                          .dataCollectionInfo(createDataCollectionInfo())
                          .validAfter(startTimeMSForCurrentMinute)
                          .status(ExecutionStatus.WAITING)
                          .build();
      cvTasks.add(cvTask);
    }
    cvTaskService.enqueueSequentialTasks(cvTasks);
    for (int i = 0; i < cvTasks.size(); i++) {
      cvTasks.set(i, cvTaskService.getCVTask(cvTasks.get(i).getUuid()));
    }
    assertThat(cvTasks.get(0).getStatus()).isEqualTo(ExecutionStatus.QUEUED);
    for (int i = 0; i < 9; i++) {
      assertThat(cvTasks.get(i + 1).getUuid()).isEqualTo(cvTasks.get(i).getNextTaskId());
    }
    assertThat(cvTasks.get(cvTasks.size() - 1).getNextTaskId()).isNull();
    for (int i = 1; i < 10; i++) {
      assertThat(cvTasks.get(i).getStatus()).isEqualTo(ExecutionStatus.WAITING);
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatusWhenTaskResultIsSuccessful() throws IllegalAccessException {
    CVActivityLogService activityLogService = mock(CVActivityLogService.class);
    CVActivityLogger logger = mock(CVActivityLogger.class);
    when(activityLogService.getLogger(any(), any(), anyLong(), any())).thenReturn(logger);
    FieldUtils.writeField(cvTaskService, "activityLogService", activityLogService, true);
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.RUNNING);
    CVTask nextTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.WAITING);
    cvTask.setNextTaskId(nextTask.getUuid());
    cvTaskService.saveCVTask(cvTask);
    DataCollectionTaskResult dataCollectionTaskResult =
        DataCollectionTaskResult.builder().status(DataCollectionTaskStatus.SUCCESS).build();
    cvTaskService.updateTaskStatus(cvTask.getUuid(), dataCollectionTaskResult);
    assertThat(cvTaskService.getCVTask(cvTask.getUuid()).getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(cvTaskService.getCVTask(nextTask.getUuid()).getStatus()).isEqualTo(ExecutionStatus.QUEUED);
    verify(logger).info(eq("Data collection successful for time range %t to %t"),
        eq(cvTask.getDataCollectionInfo().getStartTime().toEpochMilli()),
        eq(cvTask.getDataCollectionInfo().getEndTime().toEpochMilli() + 1));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatusWhenTaskHasFailed() throws IllegalAccessException {
    CVActivityLogService activityLogService = mock(CVActivityLogService.class);
    CVActivityLogger logger = mock(CVActivityLogger.class);
    when(activityLogService.getLogger(any(), any(), anyLong(), any())).thenReturn(logger);
    FieldUtils.writeField(cvTaskService, "activityLogService", activityLogService, true);
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.RUNNING);
    CVTask nextTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.WAITING);
    cvTask.setNextTaskId(nextTask.getUuid());
    cvTaskService.saveCVTask(cvTask);
    DataCollectionTaskResult dataCollectionTaskResult = DataCollectionTaskResult.builder()
                                                            .status(DataCollectionTaskStatus.FAILURE)
                                                            .errorMessage("Error from unit test")
                                                            .build();
    cvTaskService.updateTaskStatus(cvTask.getUuid(), dataCollectionTaskResult);
    CVTask updatedTask1 = cvTaskService.getCVTask(cvTask.getUuid());
    assertThat(updatedTask1.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(updatedTask1.getException()).isEqualTo("Error from unit test");
    CVTask updatedTask2 = cvTaskService.getCVTask(nextTask.getUuid());
    assertThat(updatedTask2.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(updatedTask2.getException()).isEqualTo("Previous task failed");
    verify(logger).error(eq("Data collection failed for time range %t to %t. Error: Error from unit test"),
        eq(cvTask.getDataCollectionInfo().getStartTime().toEpochMilli()),
        eq(cvTask.getDataCollectionInfo().getEndTime().toEpochMilli() + 1));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateNotifyErrorWhenTaskHasFailed() {
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.RUNNING);
    String correlationId = generateUUID();
    cvTask.setCorrelationId(correlationId);
    cvTaskService.saveCVTask(cvTask);
    DataCollectionTaskResult dataCollectionTaskResult = DataCollectionTaskResult.builder()
                                                            .status(DataCollectionTaskStatus.FAILURE)
                                                            .errorMessage("Error from unit test")
                                                            .build();
    cvTaskService.updateTaskStatus(cvTask.getUuid(), dataCollectionTaskResult);
    ArgumentCaptor<VerificationDataAnalysisResponse> responseArgumentCaptor =
        ArgumentCaptor.forClass(VerificationDataAnalysisResponse.class);
    verify(verificationManagerClientHelper)
        .notifyManagerForVerificationAnalysis(eq(accountId), eq(correlationId), responseArgumentCaptor.capture());
    VerificationStateAnalysisExecutionData stateExecutionData =
        responseArgumentCaptor.getValue().getStateExecutionData();
    assertThat(stateExecutionData.getErrorMsg()).isEqualTo("Error from unit test");
    assertThat(stateExecutionData.getStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(stateExecutionData.getCorrelationId()).isEqualTo(correlationId);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExpireLongRunningTasksIfTaskIsExpired() throws IllegalAccessException {
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.RUNNING);
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(Instant.now().plus(16, ChronoUnit.MINUTES));
    FieldUtils.writeField(cvTaskService, "clock", clock, true);
    cvTaskService.expireLongRunningTasks(cvTask.getAccountId());
    CVTask updatedTask = cvTaskService.getCVTask(cvTask.getUuid());
    assertThat(updatedTask.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(updatedTask.getException()).isEqualTo("Task timed out");
    ArgumentCaptor<VerificationDataAnalysisResponse> responseArgumentCaptor =
        ArgumentCaptor.forClass(VerificationDataAnalysisResponse.class);
    verify(verificationManagerClientHelper)
        .notifyManagerForVerificationAnalysis(
            eq(accountId), eq(cvTask.getCorrelationId()), responseArgumentCaptor.capture());
    VerificationStateAnalysisExecutionData stateExecutionData =
        responseArgumentCaptor.getValue().getStateExecutionData();
    assertThat(stateExecutionData.getErrorMsg()).isEqualTo("Task timed out");
    assertThat(stateExecutionData.getStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(stateExecutionData.getCorrelationId()).isEqualTo(cvTask.getCorrelationId());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDependentTaskStatusUpdateIfTaskExpired() throws IllegalAccessException {
    CVTask cvTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.RUNNING);
    CVTask nextTask = createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.WAITING);
    cvTask.setNextTaskId(nextTask.getUuid());
    cvTaskService.saveCVTask(cvTask);
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(Instant.now().plus(16, ChronoUnit.MINUTES));
    FieldUtils.writeField(cvTaskService, "clock", clock, true);
    cvTaskService.expireLongRunningTasks(cvTask.getAccountId());
    CVTask updatedNextTask = cvTaskService.getCVTask(nextTask.getUuid());
    assertThat(updatedNextTask.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(updatedNextTask.getException()).isEqualTo("Previous task timed out");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateCVTasks() {
    long startDataCollectionMinute = Timestamp.currentMinuteBoundary();
    AnalysisContext analysisContext = AnalysisContext.builder().build();
    analysisContext.setStartDataCollectionMinute(TimeUnit.MILLISECONDS.toMinutes(startDataCollectionMinute));
    analysisContext.setAccountId(accountId);
    analysisContext.setDataCollectionInfov2(createDataCollectionInfo());
    analysisContext.setTimeDuration(10);
    analysisContext.setInitialDelaySeconds(120);
    analysisContext.setStateExecutionId(stateExecutionId);
    cvTaskService.createCVTasks(analysisContext);
    List<CVTask> cvTasks = getByStateExecutionId(stateExecutionId);
    Collections.sort(cvTasks, Comparator.comparing(cvTask -> cvTask.getDataCollectionInfo().getStartTime()));
    assertThat(cvTasks).hasSize(10);
    assertThat(cvTasks.get(0).getStatus()).isEqualTo(ExecutionStatus.QUEUED);
    for (int i = 0; i < 9; i++) {
      assertThat(cvTasks.get(i + 1).getUuid()).isEqualTo(cvTasks.get(i).getNextTaskId());
    }
    assertThat(cvTasks.get(cvTasks.size() - 1).getNextTaskId()).isNull();
    for (int i = 1; i < 10; i++) {
      assertThat(cvTasks.get(i).getStatus()).isEqualTo(ExecutionStatus.WAITING);
      assertThat(cvTasks.get(i).getDataCollectionInfo().getDataCollectionStartTime())
          .isEqualTo(Instant.ofEpochMilli(startDataCollectionMinute));
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateCVTasks_forPredictive() {
    AnalysisContext analysisContext = AnalysisContext.builder().build();
    analysisContext.setStartDataCollectionMinute(TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary()));
    analysisContext.setAccountId(accountId);
    analysisContext.setDataCollectionInfov2(createDataCollectionInfo());
    analysisContext.setTimeDuration(10);
    analysisContext.setPredictiveHistoryMinutes(120);
    analysisContext.setInitialDelaySeconds(120);
    analysisContext.setComparisonStrategy(AnalysisComparisonStrategy.PREDICTIVE);
    analysisContext.setStateExecutionId(stateExecutionId);
    cvTaskService.createCVTasks(analysisContext);
    List<CVTask> cvTasks = getByStateExecutionId(stateExecutionId);
    Collections.sort(cvTasks, Comparator.comparing(cvTask -> cvTask.getDataCollectionInfo().getStartTime()));
    assertThat(cvTasks).hasSize(18);
    assertThat(cvTasks.get(0).getStatus()).isEqualTo(ExecutionStatus.QUEUED);
    for (int i = 0; i < 8; i++) {
      assertThat(cvTasks.get(i).getDataCollectionInfo().getHosts()).isEmpty();
    }
    for (int i = 8; i < 18; i++) {
      assertThat(cvTasks.get(i).getDataCollectionInfo().getHosts()).isNotEmpty();
    }
    for (int i = 0; i < 17; i++) {
      assertThat(cvTasks.get(i + 1).getUuid()).isEqualTo(cvTasks.get(i).getNextTaskId());
    }
    assertThat(cvTasks.get(cvTasks.size() - 1).getNextTaskId()).isNull();
    for (int i = 1; i < 18; i++) {
      assertThat(cvTasks.get(i).getStatus()).isEqualTo(ExecutionStatus.WAITING);
    }
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateCVTasks_forHistorical() {
    AnalysisContext analysisContext = AnalysisContext.builder().build();
    analysisContext.setStartDataCollectionMinute(TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary()));
    analysisContext.setAccountId(accountId);
    analysisContext.setDataCollectionInfov2(createDataCollectionInfo());
    analysisContext.setTimeDuration(10);
    analysisContext.setInitialDelaySeconds(120);
    analysisContext.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT);
    analysisContext.setHistoricalDataCollection(true);
    analysisContext.setStateExecutionId(stateExecutionId);

    cvTaskService.createCVTasks(analysisContext);
    List<CVTask> cvTasks = getByStateExecutionId(stateExecutionId);

    // there should be 10 tasks for each of the past 7 days.
    assertThat(cvTasks).isNotEmpty();
    assertThat(cvTasks.size()).isEqualTo(80);
    Map<Integer, List<CVTask>> dayTaskMap = new HashMap<>();
    cvTasks.forEach(task -> {
      long day = TimeUnit.MILLISECONDS.toDays(Instant.now().toEpochMilli())
          - TimeUnit.MILLISECONDS.toDays(task.getDataCollectionInfo().getStartTime().toEpochMilli());
      if (!dayTaskMap.containsKey((int) day)) {
        dayTaskMap.put((int) day, new ArrayList<>());
      }
      dayTaskMap.get((int) day).add(task);
    });
    List<CVTask> tasksWithHeartbeat = cvTasks.stream()
                                          .filter(task -> task.getDataCollectionInfo().isShouldSendHeartbeat())
                                          .collect(Collectors.toList());
    assertThat(tasksWithHeartbeat.size()).isEqualTo(10);
    tasksWithHeartbeat.forEach(task -> {
      assertThat(task.getDataCollectionInfo().getHosts().size()).isEqualTo(1);
      assertThat(task.getDataCollectionInfo().getHosts().contains("controlNode-7")).isTrue();
    });
  }

  private List<CVTask> getByStateExecutionId(String stateExecutionId) {
    PageRequest<CVTask> pageRequest = aPageRequest()
                                          .addFilter(CVTaskKeys.accountId, Operator.EQ, accountId)
                                          .addFilter(CVTaskKeys.stateExecutionId, Operator.EQ, stateExecutionId)
                                          .build();
    return wingsPersistence.query(CVTask.class, pageRequest).getResponse();
  }

  private CVTask getCVTask(String cvTaskId) {
    return wingsPersistence.get(CVTask.class, cvTaskId);
  }

  private CVTask createCVTaskWithStateExecutionId() {
    return createCVTaskWithStateExecutionId(ExecutionStatus.QUEUED);
  }
  private CVTask createCVTaskWithStateExecutionId(ExecutionStatus executionStatus) {
    return CVTask.builder()
        .stateExecutionId(stateExecutionId)
        .correlationId(generateUUID())
        .accountId(accountId)
        .status(executionStatus)
        .validAfter(System.currentTimeMillis())
        .dataCollectionInfo(createDataCollectionInfo())
        .build();
  }

  private DataCollectionInfoV2 createDataCollectionInfo() {
    return SplunkDataCollectionInfoV2.builder()
        .accountId(accountId)
        .hosts(Sets.newHashSet("example.com"))
        .serviceId(generateUUID())
        .workflowId(generateUUID())
        .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
        .endTime(Instant.now())
        .build();
  }

  private CVTask createAndSaveCVTaskWithStateExecutionId() {
    return createAndSaveCVTaskWithStateExecutionId(ExecutionStatus.QUEUED);
  }

  private CVTask createAndSaveCVTaskWithStateExecutionId(ExecutionStatus executionStatus) {
    CVTask cvTask = createCVTaskWithStateExecutionId(executionStatus);
    wingsPersistence.save(cvTask);
    return cvTask;
  }

  private CVTask createCVTaskWithCVConfigId() {
    return createCVTaskWithCVConfigId(ExecutionStatus.QUEUED);
  }
  private CVTask createCVTaskWithCVConfigId(ExecutionStatus executionStatus) {
    return CVTask.builder()
        .cvConfigId(cvConfigId)
        .correlationId(generateUUID())
        .accountId(accountId)
        .status(executionStatus)
        .validAfter(System.currentTimeMillis())
        .dataCollectionInfo(createDataCollectionInfo())
        .build();
  }

  private CVTask createAndSaveCVTaskWithCVConfigId() {
    return createAndSaveCVTaskWithCVConfigId(ExecutionStatus.QUEUED);
  }

  private CVTask createAndSaveCVTaskWithCVConfigId(ExecutionStatus executionStatus) {
    CVTask cvTask = createCVTaskWithCVConfigId(executionStatus);
    wingsPersistence.save(cvTask);
    return cvTask;
  }
}
