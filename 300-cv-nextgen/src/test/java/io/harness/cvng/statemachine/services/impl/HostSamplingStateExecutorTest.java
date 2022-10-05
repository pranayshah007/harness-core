/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.statemachine.services.api.HostSamplingStateExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HostSamplingStateExecutorTest extends CategoryTest {
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  private BuilderFactory builderFactory;

  @Mock private VerificationJobInstanceService verificationJobInstanceService;
  @Mock private TimeSeriesRecordService timeSeriesRecordService;
  @Mock private TimeSeriesAnalysisService timeSeriesAnalysisService;

  private HostSamplingState hostSamplingState;
  private HostSamplingStateExecutor hostSamplingStateExecutor = new HostSamplingStateExecutor();

  @Before
  public void setup() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);

    verificationTaskId = generateUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);

    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    hostSamplingState = new HostSamplingState();
    hostSamplingState.setInputs(input);
    FieldUtils.writeField(
        hostSamplingStateExecutor, "verificationJobInstanceService", verificationJobInstanceService, true);
    FieldUtils.writeField(hostSamplingStateExecutor, "timeSeriesRecordService", timeSeriesRecordService, true);
    FieldUtils.writeField(hostSamplingStateExecutor, "timeSeriesAnalysisService", timeSeriesAnalysisService, true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testExecute() {
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    hostSamplingState.setVerificationJobInstance(verificationJobInstance);

    Optional<TimeRange> preDeploymentTimeRange = verificationJobInstance.getResolvedJob().getPreActivityTimeRange(
        verificationJobInstance.getDeploymentStartTime());
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(hostSamplingState.getInputs().getVerificationTaskId(),
             preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime()))
        .thenReturn(new ArrayList<>());

    hostSamplingState = (HostSamplingState) hostSamplingStateExecutor.execute(hostSamplingState);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(hostSamplingState.getInputs().getVerificationTaskId(),
             hostSamplingState.getInputs().getStartTime(), hostSamplingState.getInputs().getEndTime()))
        .thenReturn(new ArrayList<>());

    assertThat(hostSamplingState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(hostSamplingState.getRetryCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_success_RUNNING() {
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    hostSamplingState.setVerificationJobInstance(verificationJobInstance);
    String taskId = generateUuid();
    hostSamplingState.setStatus(AnalysisStatus.RUNNING);
    hostSamplingState.setTestHosts(new HashSet<>());
    hostSamplingState.setControlHosts(new HashSet<>());
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.SUCCESS);
    AnalysisStatus status = hostSamplingStateExecutor.getExecutionStatus(hostSamplingState);
    assertThat(status.name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_success_TRANSITION() {
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    hostSamplingState.setVerificationJobInstance(verificationJobInstance);
    String taskId = generateUuid();
    hostSamplingState.setStatus(AnalysisStatus.RUNNING);
    hostSamplingState.setTestHosts(new HashSet<>(Collections.singleton("abc")));
    hostSamplingState.setControlHosts(new HashSet<>(Collections.singleton("def")));
    Map<String, LearningEngineTask.ExecutionStatus> taskStatusMap = new HashMap<>();
    taskStatusMap.put(taskId, LearningEngineTask.ExecutionStatus.SUCCESS);
    AnalysisStatus status = hostSamplingStateExecutor.getExecutionStatus(hostSamplingState);
    assertThat(status.name()).isEqualTo(AnalysisStatus.TRANSITION.name());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRerun() {
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    hostSamplingState.setVerificationJobInstance(verificationJobInstance);
    hostSamplingState.setRetryCount(2);
    hostSamplingState.setStatus(AnalysisStatus.FAILED);
    hostSamplingState = (HostSamplingState) hostSamplingStateExecutor.handleRerun(hostSamplingState);
    assertThat(hostSamplingState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRunning() {
    hostSamplingState.setStatus(AnalysisStatus.RUNNING);
    hostSamplingState = (HostSamplingState) hostSamplingStateExecutor.handleRunning(hostSamplingState);
    assertThat(hostSamplingState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleSuccess() {
    AnalysisState state = hostSamplingStateExecutor.handleSuccess(hostSamplingState);
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandleRetry() {
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    hostSamplingState.setVerificationJobInstance(verificationJobInstance);
    hostSamplingState.setRetryCount(1);
    hostSamplingState = (HostSamplingState) hostSamplingStateExecutor.handleRetry(hostSamplingState);

    assertThat(hostSamplingState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(hostSamplingState.getRetryCount()).isEqualTo(2);
  }
}
