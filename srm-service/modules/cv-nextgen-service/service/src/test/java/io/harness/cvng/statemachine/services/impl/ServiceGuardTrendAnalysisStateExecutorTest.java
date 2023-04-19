/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.ServiceGuardTrendAnalysisState;
import io.harness.cvng.statemachine.services.api.ServiceGuardTrendAnalysisStateExecutor;
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

public class ServiceGuardTrendAnalysisStateExecutorTest extends CategoryTest {
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  @Mock private TrendAnalysisService trendAnalysisService;

  private ServiceGuardTrendAnalysisState trendAnalysisState;
  private ServiceGuardTrendAnalysisStateExecutor serviceGuardTrendAnalysisStateExecutor =
      new ServiceGuardTrendAnalysisStateExecutor();

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    verificationTaskId = generateUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);

    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    trendAnalysisState = ServiceGuardTrendAnalysisState.builder().build();
    trendAnalysisState.setInputs(input);

    FieldUtils.writeField(serviceGuardTrendAnalysisStateExecutor, "trendAnalysisService", trendAnalysisService, true);

    when(trendAnalysisService.scheduleTrendAnalysisTask(any())).thenReturn(generateUuid());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testExecute() {
    trendAnalysisState =
        (ServiceGuardTrendAnalysisState) serviceGuardTrendAnalysisStateExecutor.execute(trendAnalysisState);

    assertThat(trendAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(trendAnalysisState.getWorkerTaskId()).isNotNull();
    assertThat(trendAnalysisState.getRetryCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_success() {
    String taskId = generateUuid();
    trendAnalysisState.setStatus(AnalysisStatus.RUNNING);
    trendAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.SUCCESS);

    when(trendAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardTrendAnalysisStateExecutor.getExecutionStatus(trendAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_running() {
    String taskId = generateUuid();
    trendAnalysisState.setStatus(AnalysisStatus.RUNNING);
    trendAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.RUNNING);

    when(trendAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardTrendAnalysisStateExecutor.getExecutionStatus(trendAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_failed() {
    String taskId = generateUuid();
    trendAnalysisState.setStatus(AnalysisStatus.RUNNING);
    trendAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.FAILED);

    when(trendAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardTrendAnalysisStateExecutor.getExecutionStatus(trendAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RETRY.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_timeout() {
    String taskId = generateUuid();
    trendAnalysisState.setStatus(AnalysisStatus.RUNNING);
    trendAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.TIMEOUT);

    when(trendAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardTrendAnalysisStateExecutor.getExecutionStatus(trendAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RETRY.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_queued() {
    String taskId = generateUuid();
    trendAnalysisState.setStatus(AnalysisStatus.RUNNING);
    trendAnalysisState.setWorkerTaskId(taskId);
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.QUEUED);

    when(trendAnalysisService.getTaskStatus(anyList())).thenReturn(taskStatusMap);

    AnalysisStatus status = serviceGuardTrendAnalysisStateExecutor.getExecutionStatus(trendAnalysisState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHandleRerun() {
    trendAnalysisState.setRetryCount(2);
    trendAnalysisState.setStatus(AnalysisStatus.FAILED);

    trendAnalysisState =
        (ServiceGuardTrendAnalysisState) serviceGuardTrendAnalysisStateExecutor.handleRerun(trendAnalysisState);

    assertThat(trendAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHandleRunning() {
    trendAnalysisState.setStatus(AnalysisStatus.RUNNING);

    trendAnalysisState =
        (ServiceGuardTrendAnalysisState) serviceGuardTrendAnalysisStateExecutor.handleRunning(trendAnalysisState);

    assertThat(trendAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHandleSuccess() {
    AnalysisState state = serviceGuardTrendAnalysisStateExecutor.handleSuccess(trendAnalysisState);
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHandleTransition() {
    AnalysisState state = serviceGuardTrendAnalysisStateExecutor.handleTransition(trendAnalysisState);
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHandleRetry() {
    trendAnalysisState.setRetryCount(1);

    trendAnalysisState =
        (ServiceGuardTrendAnalysisState) serviceGuardTrendAnalysisStateExecutor.handleRetry(trendAnalysisState);

    assertThat(trendAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(trendAnalysisState.getWorkerTaskId()).isNotNull();
    assertThat(trendAnalysisState.getRetryCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHandleRetry_noMoreRetry() {
    trendAnalysisState.setRetryCount(2);

    trendAnalysisState =
        (ServiceGuardTrendAnalysisState) serviceGuardTrendAnalysisStateExecutor.handleRetry(trendAnalysisState);

    assertThat(trendAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.IGNORED.name());
  }
}
