/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.ServiceGuardLogAnalysisState;
import io.harness.cvng.statemachine.services.api.ServiceGuardLogAnalysisStateExecutor;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceGuardLogAnalysisStateExecutorTest extends CategoryTest {
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  @Mock private LogAnalysisService logAnalysisService;

  private ServiceGuardLogAnalysisState logAnalysisState;
  private ServiceGuardLogAnalysisStateExecutor serviceGuardLogAnalysisStateExecutor =
      new ServiceGuardLogAnalysisStateExecutor();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    verificationTaskId = generateUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);

    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    logAnalysisState = ServiceGuardLogAnalysisState.builder().build();
    logAnalysisState.setInputs(input);
    FieldUtils.writeField(serviceGuardLogAnalysisStateExecutor, "logAnalysisService", logAnalysisService, true);

    when(logAnalysisService.scheduleServiceGuardLogAnalysisTask(any())).thenReturn(generateUuid());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecute() {
    logAnalysisState = (ServiceGuardLogAnalysisState) serviceGuardLogAnalysisStateExecutor.execute(logAnalysisState);

    assertThat(logAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(logAnalysisState.getWorkerTaskId()).isNotNull();
    assertThat(logAnalysisState.getRetryCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_success() {
    String taskId = generateUuid();
    logAnalysisState.setStatus(AnalysisStatus.RUNNING);
    logAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.SUCCESS);

    when(logAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardLogAnalysisStateExecutor.getExecutionStatus(logAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.TRANSITION.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_running() {
    String taskId = generateUuid();
    logAnalysisState.setStatus(AnalysisStatus.RUNNING);
    logAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.RUNNING);

    when(logAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardLogAnalysisStateExecutor.getExecutionStatus(logAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_failed() {
    String taskId = generateUuid();
    logAnalysisState.setStatus(AnalysisStatus.RUNNING);
    logAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.FAILED);

    when(logAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardLogAnalysisStateExecutor.getExecutionStatus(logAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RETRY.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_timeout() {
    String taskId = generateUuid();
    logAnalysisState.setStatus(AnalysisStatus.RUNNING);
    logAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.TIMEOUT);

    when(logAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardLogAnalysisStateExecutor.getExecutionStatus(logAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RETRY.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_queued() {
    String taskId = generateUuid();
    logAnalysisState.setStatus(AnalysisStatus.RUNNING);
    logAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.QUEUED);

    when(logAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardLogAnalysisStateExecutor.getExecutionStatus(logAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleRerun() {
    logAnalysisState.setRetryCount(2);
    logAnalysisState.setStatus(AnalysisStatus.FAILED);

    logAnalysisState =
        (ServiceGuardLogAnalysisState) serviceGuardLogAnalysisStateExecutor.handleRerun(logAnalysisState);

    assertThat(logAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleRunning() {
    logAnalysisState.setStatus(AnalysisStatus.RUNNING);

    logAnalysisState =
        (ServiceGuardLogAnalysisState) serviceGuardLogAnalysisStateExecutor.handleRunning(logAnalysisState);

    assertThat(logAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleSuccess() {
    AnalysisState state = serviceGuardLogAnalysisStateExecutor.handleSuccess(logAnalysisState);
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleTransition() {
    AnalysisState state = serviceGuardLogAnalysisStateExecutor.handleTransition(logAnalysisState);
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.CREATED.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleRetry() {
    logAnalysisState.setRetryCount(1);

    logAnalysisState =
        (ServiceGuardLogAnalysisState) serviceGuardLogAnalysisStateExecutor.handleRetry(logAnalysisState);

    assertThat(logAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(logAnalysisState.getWorkerTaskId()).isNotNull();
    assertThat(logAnalysisState.getRetryCount()).isEqualTo(2);
  }
}
