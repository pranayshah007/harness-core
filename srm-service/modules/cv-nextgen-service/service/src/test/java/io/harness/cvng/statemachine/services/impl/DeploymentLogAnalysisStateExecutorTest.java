/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.rule.OwnerRule.NAVEEN;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.services.api.DeploymentLogAnalysisStateExecutor;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob.CanaryBlueGreenVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.TestVerificationJob.TestVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentLogAnalysisStateExecutorTest extends CategoryTest {
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  private BuilderFactory builderFactory;

  @Mock private VerificationTaskService verificationTaskService;
  @Mock private VerificationJobInstanceService verificationJobInstanceService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private LogAnalysisService logAnalysisService;
  private final DeploymentLogAnalysisStateExecutor deploymentLogAnalysisStateExecutor =
      new DeploymentLogAnalysisStateExecutor();

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(deploymentLogAnalysisStateExecutor, "verificationTaskService", verificationTaskService, true);
    FieldUtils.writeField(
        deploymentLogAnalysisStateExecutor, "verificationJobInstanceService", verificationJobInstanceService, true);
    FieldUtils.writeField(deploymentLogAnalysisStateExecutor, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(deploymentLogAnalysisStateExecutor, "logAnalysisService", logAnalysisService, true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_SUCCESS() {
    String workerTaskId = UUID.randomUUID().toString();
    DeploymentLogAnalysisState deploymentLogAnalysisState = new DeploymentLogAnalysisState();
    deploymentLogAnalysisState.setWorkerTaskId(workerTaskId);
    Map<String, ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(workerTaskId, ExecutionStatus.SUCCESS);
    when(logAnalysisService.getTaskStatus(List.of(workerTaskId))).thenReturn(taskStatusMap);

    AnalysisStatus analysisStatus = deploymentLogAnalysisStateExecutor.getExecutionStatus(deploymentLogAnalysisState);
    assertEquals(analysisStatus, AnalysisStatus.TRANSITION);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void handleTransition_logFeedbackFeatureFlagDisabled() {
    String verificationTaskId = UUID.randomUUID().toString();
    DeploymentLogAnalysisState deploymentLogAnalysisState = new DeploymentLogAnalysisState();
    deploymentLogAnalysisState.setInputs(AnalysisInput.builder()
                                             .verificationTaskId(verificationTaskId)
                                             .endTime(Instant.parse("2023-03-11T12:44:00Z"))
                                             .build());
    CanaryBlueGreenVerificationJobBuilder verificationJobInstanceBuilder =
        builderFactory.canaryVerificationJobBuilder();
    VerificationJobInstance verificationJobInstance = VerificationJobInstance.builder()
                                                          .deploymentStartTime(Instant.parse("2023-03-11T12:43:00Z"))
                                                          .startTime(Instant.now().plus(Duration.ofMinutes(2)))
                                                          .resolvedJob(verificationJobInstanceBuilder.build())
                                                          .build();
    String verificationJobInstanceId = UUID.randomUUID().toString();
    when(verificationTaskService.getVerificationJobInstanceId(
             deploymentLogAnalysisState.getInputs().getVerificationTaskId()))
        .thenReturn(verificationJobInstanceId);
    when(verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId))
        .thenReturn(verificationJobInstance);
    AnalysisState analysisState = deploymentLogAnalysisStateExecutor.handleTransition(deploymentLogAnalysisState);
    assertEquals(analysisState.getType(), StateType.DEPLOYMENT_LOG_ANALYSIS);
    assertEquals(analysisState.getStatus(), AnalysisStatus.SUCCESS);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void handleTransition_logFeedbackFeatureFlagEnabled() {
    String verificationTaskId = UUID.randomUUID().toString();
    DeploymentLogAnalysisState deploymentLogAnalysisState = new DeploymentLogAnalysisState();
    deploymentLogAnalysisState.setInputs(AnalysisInput.builder()
                                             .verificationTaskId(verificationTaskId)
                                             .endTime(Instant.parse("2023-03-11T12:44:00Z"))
                                             .build());
    CanaryBlueGreenVerificationJobBuilder verificationJobInstanceBuilder =
        builderFactory.canaryVerificationJobBuilder();
    VerificationJobInstance verificationJobInstance = VerificationJobInstance.builder()
                                                          .deploymentStartTime(Instant.parse("2023-03-11T12:30:00Z"))
                                                          .startTime(Instant.parse("2023-03-11T12:34:00Z"))
                                                          .resolvedJob(verificationJobInstanceBuilder.build())
                                                          .build();
    String verificationJobInstanceId = UUID.randomUUID().toString();
    when(verificationTaskService.getVerificationJobInstanceId(
             deploymentLogAnalysisState.getInputs().getVerificationTaskId()))
        .thenReturn(verificationJobInstanceId);
    when(verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId))
        .thenReturn(verificationJobInstance);
    when(featureFlagService.isFeatureFlagEnabled(
             verificationJobInstance.getAccountId(), FeatureName.SRM_LOG_FEEDBACK_ENABLE_UI.toString()))
        .thenReturn(true);
    AnalysisState analysisState = deploymentLogAnalysisStateExecutor.handleTransition(deploymentLogAnalysisState);
    assertEquals(analysisState.getType(), StateType.DEPLOYMENT_LOG_FEEDBACK_STATE);
    assertEquals(analysisState.getStatus(), AnalysisStatus.CREATED);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void handleTransition_logFeedbackFeatureFlagEnabled_baselineNull() {
    String verificationTaskId = UUID.randomUUID().toString();
    DeploymentLogAnalysisState deploymentLogAnalysisState = new DeploymentLogAnalysisState();
    deploymentLogAnalysisState.setInputs(AnalysisInput.builder()
                                             .verificationTaskId(verificationTaskId)
                                             .endTime(Instant.parse("2023-03-11T12:44:00Z"))
                                             .build());
    TestVerificationJobBuilder verificationJobInstanceBuilder = builderFactory.testVerificationJobBuilder();
    verificationJobInstanceBuilder.baselineVerificationJobInstanceId(null);
    VerificationJobInstance verificationJobInstance = VerificationJobInstance.builder()
                                                          .deploymentStartTime(Instant.parse("2023-03-11T12:30:00Z"))
                                                          .startTime(Instant.parse("2023-03-11T12:34:00Z"))
                                                          .resolvedJob(verificationJobInstanceBuilder.build())
                                                          .build();
    String verificationJobInstanceId = UUID.randomUUID().toString();
    when(verificationTaskService.getVerificationJobInstanceId(
             deploymentLogAnalysisState.getInputs().getVerificationTaskId()))
        .thenReturn(verificationJobInstanceId);
    when(verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId))
        .thenReturn(verificationJobInstance);
    when(featureFlagService.isFeatureFlagEnabled(
             verificationJobInstance.getAccountId(), FeatureName.SRM_LOG_FEEDBACK_ENABLE_UI.toString()))
        .thenReturn(true);
    AnalysisState analysisState = deploymentLogAnalysisStateExecutor.handleTransition(deploymentLogAnalysisState);
    assertEquals(analysisState.getType(), StateType.DEPLOYMENT_LOG_ANALYSIS);
    assertEquals(analysisState.getStatus(), AnalysisStatus.SUCCESS);
  }
}
