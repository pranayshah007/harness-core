/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NAVEEN;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogFeedbackState;
import io.harness.cvng.statemachine.services.api.DeploymentLogFeedbackStateExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DeploymentLogFeedbackStateExecutorTest {
  private VerificationJobInstance verificationJobInstance;

  @Mock private VerificationJobInstanceService verificationJobInstanceService;
  @Mock private LogAnalysisService logAnalysisService;
  @Mock private TimeSeriesAnalysisService timeSeriesAnalysisService;

  private DeploymentLogFeedbackState deploymentLogFeedbackState;
  private final DeploymentLogFeedbackStateExecutor deploymentLogFeedbackStateExecutor =
      new DeploymentLogFeedbackStateExecutor();

  public List<TimeSeriesRecordDTO> getTimeSeriesRecordDTO(List<String> hosts) {
    return hosts.stream().map(h -> TimeSeriesRecordDTO.builder().host(h).build()).collect(Collectors.toList());
  }

  @Before
  public void setup() throws Exception {
    BuilderFactory builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);

    String verificationTaskId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant endTime = startTime.plus(5, ChronoUnit.MINUTES);

    verificationJobInstance = VerificationJobInstance.builder()
                                  .deploymentStartTime(Instant.now())
                                  .startTime(Instant.now().plus(Duration.ofMinutes(2)))
                                  .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
                                  .build();

    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(startTime)
                              .verificationJobInstanceId(verificationJobInstance.getUuid())
                              .endTime(endTime)
                              .build();

    deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setInputs(input);
    FieldUtils.writeField(
        deploymentLogFeedbackStateExecutor, "verificationJobInstanceService", verificationJobInstanceService, true);
    FieldUtils.writeField(
        deploymentLogFeedbackStateExecutor, "timeSeriesAnalysisService", timeSeriesAnalysisService, true);
    FieldUtils.writeField(deploymentLogFeedbackStateExecutor, "logAnalysisService", logAnalysisService, true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testExecuteSuccess() {
    String workerTaskId = UUID.randomUUID().toString();
    when(verificationJobInstanceService.getVerificationJobInstance(
             deploymentLogFeedbackState.getInputs().getVerificationJobInstanceId()))
        .thenReturn(verificationJobInstance);
    when(logAnalysisService.scheduleDeploymentLogFeedbackTask(deploymentLogFeedbackState.getInputs()))
        .thenReturn(workerTaskId);
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.execute(deploymentLogFeedbackState);
    assert analysisState.getType() == StateType.DEPLOYMENT_LOG_FEEDBACK_STATE;
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_SUCCESS() {
    String workerTaskId = UUID.randomUUID().toString();
    DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setWorkerTaskId(workerTaskId);
    Map<String, ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(workerTaskId, ExecutionStatus.SUCCESS);
    when(logAnalysisService.getTaskStatus(List.of(workerTaskId))).thenReturn(taskStatusMap);

    AnalysisStatus analysisStatus = deploymentLogFeedbackStateExecutor.getExecutionStatus(deploymentLogFeedbackState);
    assertEquals(analysisStatus, AnalysisStatus.SUCCESS);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_FAILED() {
    String workerTaskId = UUID.randomUUID().toString();
    DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setWorkerTaskId(workerTaskId);
    Map<String, ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(workerTaskId, ExecutionStatus.FAILED);
    when(logAnalysisService.getTaskStatus(List.of(workerTaskId))).thenReturn(taskStatusMap);

    AnalysisStatus analysisStatus = deploymentLogFeedbackStateExecutor.getExecutionStatus(deploymentLogFeedbackState);
    assertEquals(analysisStatus, AnalysisStatus.RETRY);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_TIMEOUT() {
    String workerTaskId = UUID.randomUUID().toString();
    DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setWorkerTaskId(workerTaskId);
    Map<String, ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(workerTaskId, ExecutionStatus.TIMEOUT);
    when(logAnalysisService.getTaskStatus(List.of(workerTaskId))).thenReturn(taskStatusMap);

    AnalysisStatus analysisStatus = deploymentLogFeedbackStateExecutor.getExecutionStatus(deploymentLogFeedbackState);
    assertEquals(analysisStatus, AnalysisStatus.RETRY);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_QUEUED() {
    String workerTaskId = UUID.randomUUID().toString();
    DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setWorkerTaskId(workerTaskId);
    Map<String, ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(workerTaskId, ExecutionStatus.QUEUED);
    when(logAnalysisService.getTaskStatus(List.of(workerTaskId))).thenReturn(taskStatusMap);

    AnalysisStatus analysisStatus = deploymentLogFeedbackStateExecutor.getExecutionStatus(deploymentLogFeedbackState);
    assertEquals(analysisStatus, AnalysisStatus.RUNNING);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_RUNNING() {
    String workerTaskId = UUID.randomUUID().toString();
    DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
    deploymentLogFeedbackState.setWorkerTaskId(workerTaskId);
    Map<String, ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(workerTaskId, ExecutionStatus.RUNNING);
    when(logAnalysisService.getTaskStatus(List.of(workerTaskId))).thenReturn(taskStatusMap);

    AnalysisStatus analysisStatus = deploymentLogFeedbackStateExecutor.getExecutionStatus(deploymentLogFeedbackState);
    assertEquals(analysisStatus, AnalysisStatus.RUNNING);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleFinalStatuses() {
    DeploymentLogAnalysisState deploymentLogAnalysisState = DeploymentLogAnalysisState.builder().build();
    deploymentLogFeedbackStateExecutor.handleFinalStatuses(deploymentLogAnalysisState);
    Mockito.verify(logAnalysisService)
        .logDeploymentVerificationProgress(
            deploymentLogAnalysisState.getInputs(), deploymentLogAnalysisState.getStatus());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRerun() {
    String workerTaskId = UUID.randomUUID().toString();
    when(verificationJobInstanceService.getVerificationJobInstance(
             deploymentLogFeedbackState.getInputs().getVerificationJobInstanceId()))
        .thenReturn(verificationJobInstance);
    when(logAnalysisService.scheduleDeploymentLogFeedbackTask(deploymentLogFeedbackState.getInputs()))
        .thenReturn(workerTaskId);
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.handleRerun(deploymentLogFeedbackState);
    assertEquals(analysisState.getStatus(), AnalysisStatus.RUNNING);
    assertEquals(analysisState.getRetryCount(), 1);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRunning() {
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.handleRunning(deploymentLogFeedbackState);
    assertEquals(analysisState, deploymentLogFeedbackState);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleSuccess() {
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.handleSuccess(deploymentLogFeedbackState);
    assertEquals(analysisState.getStatus(), AnalysisStatus.SUCCESS);
    assertEquals(analysisState.getInputs(), deploymentLogFeedbackState.getInputs());
    assertEquals(analysisState.getType(), deploymentLogFeedbackState.getType());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleTransition() {
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.handleTransition(deploymentLogFeedbackState);
    assertEquals(analysisState.getStatus(), AnalysisStatus.SUCCESS);
    assertEquals(analysisState.getInputs(), deploymentLogFeedbackState.getInputs());
    assertEquals(analysisState.getType(), deploymentLogFeedbackState.getType());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRetry_maxRetry() {
    deploymentLogFeedbackState.setRetryCount(3);
    AnalysisState analysisState = deploymentLogFeedbackStateExecutor.handleRetry(deploymentLogFeedbackState);
    assertEquals(analysisState.getStatus(), AnalysisStatus.FAILED);
    assertEquals(analysisState.getInputs(), deploymentLogFeedbackState.getInputs());
    assertEquals(analysisState.getType(), deploymentLogFeedbackState.getType());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRetry() {
    deploymentLogFeedbackState.setRetryCount(1);
    String workerTaskId = UUID.randomUUID().toString();
    when(verificationJobInstanceService.getVerificationJobInstance(
             deploymentLogFeedbackState.getInputs().getVerificationJobInstanceId()))
        .thenReturn(verificationJobInstance);
    when(logAnalysisService.scheduleDeploymentLogFeedbackTask(deploymentLogFeedbackState.getInputs()))
        .thenReturn(workerTaskId);
    deploymentLogFeedbackStateExecutor.handleRetry(deploymentLogFeedbackState);
  }
}
