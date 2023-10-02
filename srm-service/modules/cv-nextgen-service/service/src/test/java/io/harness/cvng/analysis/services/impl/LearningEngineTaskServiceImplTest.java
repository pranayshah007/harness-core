/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.CANARY_LOG_ANALYSIS;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_FEEDBACK_ANALYSIS;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES;
import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.ExceptionInfo;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.beans.CVNGTaskMetadataConstants;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LearningEngineTaskServiceImplTest extends CvNextGenTestBase {
  @Inject HPersistence hPersistence;
  @Inject private Clock clock;
  @Mock HPersistence mockHPersistence;
  @Mock Query<LearningEngineTask> mockLETaskQuery;
  @Mock UpdateOperations<LearningEngineTask> mockUpdateOperations;
  @Mock FieldEnd mockField;

  @Inject private CVNGLogService cvngLogService;

  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  private String accountId;
  private String cvConfigId;
  private String verificationTaskId;
  private BuilderFactory builderFactory;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    CVConfig cvConfig = builderFactory.splunkCVConfigBuilder().build();
    cvConfigService.save(cvConfig);
    accountId = builderFactory.getContext().getAccountId();
    cvConfigId = cvConfig.getUuid();
    verificationTaskId = verificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_noTaskType() {
    LearningEngineTask task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNull();
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    hPersistence.save(taskToSave);

    task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId1");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_withTaskType() {
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_LOG_ANALYSIS);
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    hPersistence.save(taskToSave);

    taskToSave.setUuid("leTaskId2");
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_TIME_SERIES);
    hPersistence.save(taskToSave);

    LearningEngineTask task = learningEngineTaskService.getNextAnalysisTask(Arrays.asList(SERVICE_GUARD_TIME_SERIES));
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId2");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_orderOfNextTaskWithDeployment() {
    String deploymentVerificationTaskId =
        verificationTaskService.createDeploymentVerificationTask(accountId, cvConfigId, generateUuid(), APP_DYNAMICS);
    String liveMonitoringVerificationTask =
        verificationTaskService.createLiveMonitoringVerificationTask(accountId, cvConfigId, APP_DYNAMICS);
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(liveMonitoringVerificationTask);
    taskToSave.setAnalysisType(SERVICE_GUARD_LOG_ANALYSIS);
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    learningEngineTaskService.createLearningEngineTask(taskToSave);
    taskToSave = CanaryLogAnalysisLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId2");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(deploymentVerificationTaskId);
    taskToSave.setAnalysisType(CANARY_LOG_ANALYSIS);
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    learningEngineTaskService.createLearningEngineTask(taskToSave);

    LearningEngineTask task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId2");
    task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId1");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_orderOfNextTask() throws InterruptedException {
    String deploymentVerificationTaskId =
        verificationTaskService.createDeploymentVerificationTask(accountId, cvConfigId, generateUuid(), APP_DYNAMICS);
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(deploymentVerificationTaskId);
    taskToSave.setAnalysisType(SERVICE_GUARD_LOG_ANALYSIS);
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    learningEngineTaskService.createLearningEngineTask(taskToSave);
    Thread.sleep(1);
    taskToSave = CanaryLogAnalysisLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId2");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(deploymentVerificationTaskId);
    taskToSave.setAnalysisType(CANARY_LOG_ANALYSIS);
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    learningEngineTaskService.createLearningEngineTask(taskToSave);

    LearningEngineTask task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId1");
    task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId2");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_withTaskTypeNoTaskAvailable() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.QUEUED);
    hPersistence.save(taskToSave);

    taskToSave.setUuid("leTaskId2");
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_TIME_SERIES);
    hPersistence.save(taskToSave);

    LearningEngineTask task =
        learningEngineTaskService.getNextAnalysisTask(Arrays.asList(SERVICE_GUARD_FEEDBACK_ANALYSIS));
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateLearningEngineTasks() {
    LearningEngineTask taskToSave = getTaskToSave(null);
    List<String> tasks = learningEngineTaskService.createLearningEngineTasks(Arrays.asList(taskToSave));
    assertThat(tasks).isNotNull();
    assertThat(tasks.size()).isEqualTo(1);
    assertThat(tasks.get(0)).isEqualTo(taskToSave.getUuid());

    taskToSave = hPersistence.get(LearningEngineTask.class, tasks.get(0));
    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.QUEUED.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTaskStatus() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    Map<String, ExecutionStatus> statuses =
        learningEngineTaskService.getTaskStatus(Sets.newHashSet(Arrays.asList(taskToSave.getUuid())));

    assertThat(statuses.size()).isEqualTo(1);
    assertThat(statuses.containsKey(taskToSave.getUuid())).isTrue();
    assertThat(statuses.get(taskToSave.getUuid()).name()).isEqualTo(ExecutionStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTaskStatus_timeout() throws Exception {
    FieldUtils.writeField(learningEngineTaskService, "hPersistence", mockHPersistence, true);
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    taskToSave.setLastUpdatedAt(clock.instant().minus(1, ChronoUnit.HOURS).toEpochMilli());

    when(mockHPersistence.createQuery(LearningEngineTask.class)).thenReturn(mockLETaskQuery);
    when(mockHPersistence.createUpdateOperations(LearningEngineTask.class)).thenReturn(mockUpdateOperations);
    when(mockHPersistence.createQuery(LearningEngineTask.class, excludeAuthority)).thenReturn(mockLETaskQuery);
    when(mockLETaskQuery.filter(any(), any())).thenReturn(mockLETaskQuery);
    when(mockLETaskQuery.field(any())).thenReturn(mockField);
    when(mockField.in(any())).thenReturn(mockLETaskQuery);
    when(mockField.equal(any())).thenReturn(mockLETaskQuery);
    when(mockLETaskQuery.get()).thenReturn(taskToSave);
    when(mockLETaskQuery.asList()).thenReturn(Arrays.asList(taskToSave));

    Map<String, ExecutionStatus> statuses =
        learningEngineTaskService.getTaskStatus(Sets.newHashSet(Arrays.asList(taskToSave.getUuid())));

    assertThat(statuses.size()).isEqualTo(1);
    assertThat(statuses.containsKey(taskToSave.getUuid())).isTrue();
    assertThat(statuses.get(taskToSave.getUuid()).name()).isEqualTo(ExecutionStatus.TIMEOUT.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkCompleted() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markCompleted(taskToSave.getUuid());

    taskToSave = hPersistence.get(LearningEngineTask.class, taskToSave.getUuid());

    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testMarkCompletedCheckDuration() throws IllegalAccessException {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    taskToSave.setCreatedAt(clock.millis());
    hPersistence.save(taskToSave);
    clock = Clock.fixed(clock.instant().plus(5, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(learningEngineTaskService, "clock", clock, true);
    learningEngineTaskService.markCompleted(taskToSave.getUuid());
    taskToSave = hPersistence.get(LearningEngineTask.class, taskToSave.getUuid());
    List<ExecutionLogDTO> cvngLogs = cvngLogService.getExecutionLogDTOs(accountId, taskToSave.getVerificationTaskId());
    ExecutionLogDTO executionLogDTO = cvngLogs.get(0);
    assertThat(executionLogDTO.getTags().get(0).getKey()).isEqualTo(CVNGTaskMetadataConstants.TASK_ID);
    assertThat(executionLogDTO.getTags().get(0).getValue()).isNotEmpty();
    assertThat(executionLogDTO.getTags().get(1).getKey()).isEqualTo(CVNGTaskMetadataConstants.TASK_TYPE);
    assertThat(executionLogDTO.getTags().get(1).getValue()).isEqualTo("SERVICE_GUARD_LOG_ANALYSIS");
    assertThat(executionLogDTO.getTags().get(2).getKey()).isEqualTo(CVNGTaskMetadataConstants.WAIT_DURATION);
    assertThat(executionLogDTO.getTags().get(2).getValue()).isEqualTo("00:03:00.000");
    assertThat(executionLogDTO.getTags().get(3).getKey()).isEqualTo(CVNGTaskMetadataConstants.RUNNING_DURATION);
    assertThat(executionLogDTO.getTags().get(3).getValue()).isEqualTo("00:02:00.000");
    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkCompleted_badTaskId() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);
    assertThatThrownBy(() -> learningEngineTaskService.markCompleted(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkFailed() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markFailure(taskToSave.getUuid(), ExceptionInfo.builder().build());
    taskToSave = hPersistence.get(LearningEngineTask.class, taskToSave.getUuid());

    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.FAILED.name());
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testMarkFailed_exceptionIsUpdated() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markFailure(taskToSave.getUuid(),
        ExceptionInfo.builder().exception("some-exception").stackTrace("some-stacktrace").build());
    taskToSave = hPersistence.get(LearningEngineTask.class, taskToSave.getUuid());
    assertThat(taskToSave.getException()).isEqualTo("some-exception");
    assertThat(taskToSave.getStackTrace()).isEqualTo("some-stacktrace");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkFailed_badTaskId() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);
    assertThatThrownBy(() -> learningEngineTaskService.markFailure(null, ExceptionInfo.builder().build()))
        .isInstanceOf(NullPointerException.class);
  }

  private LearningEngineTask getTaskToSave(ExecutionStatus taskStatus) {
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    Instant startTime = Instant.parse("2020-07-27T10:45:00.000Z");
    Instant endTime = Instant.parse("2020-07-27T10:50:00.000Z");
    taskToSave.setUuid("leTaskId1");
    taskToSave.setAccountId(accountId);
    taskToSave.setTaskStatus(taskStatus);
    taskToSave.setVerificationTaskId(verificationTaskId);
    taskToSave.setAnalysisType(SERVICE_GUARD_LOG_ANALYSIS);
    taskToSave.setAnalysisStartTime(startTime);
    taskToSave.setAnalysisEndTime(endTime);
    taskToSave.setPickedAt(endTime.plus(Duration.ofMinutes(3)));
    return taskToSave;
  }
}
