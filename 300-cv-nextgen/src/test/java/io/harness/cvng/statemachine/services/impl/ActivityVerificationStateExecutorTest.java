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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.analysis.services.api.HealthVerificationService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.ActivityVerificationState;
import io.harness.cvng.statemachine.services.api.ActivityVerificationStateExecutor;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ActivityVerificationStateExecutorTest extends CategoryTest {
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  @Mock private HealthVerificationService healthVerificationService;

  ActivityVerificationStateExecutor activityVerificationStateExecutor = new ActivityVerificationStateExecutor();

  private ActivityVerificationState activityVerificationState;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    verificationTaskId = generateUuid();
    startTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);

    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();
    FieldUtils.writeField(
        activityVerificationStateExecutor, "healthVerificationService", healthVerificationService, true);

    activityVerificationState = ActivityVerificationState.builder().build();
    activityVerificationState.setInputs(input);
    activityVerificationState.setHealthVerificationPeriod(HealthVerificationPeriod.PRE_ACTIVITY);
    activityVerificationState.setHealthVerificationService(healthVerificationService);
    activityVerificationState.setPreActivityVerificationStartTime(startTime);
    activityVerificationState.setPostActivityVerificationStartTime(endTime);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecute() {
    when(healthVerificationService.aggregateActivityAnalysis(
             verificationTaskId, startTime, endTime, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY))
        .thenReturn(startTime.plus(Duration.ofMinutes(5)));
    activityVerificationState =
        (ActivityVerificationState) activityVerificationStateExecutor.execute(activityVerificationState);

    assertThat(activityVerificationState.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(activityVerificationState.getAnalysisCompletedUntil()).isEqualTo(startTime.plus(Duration.ofMinutes(5)));
    verify(healthVerificationService, times(1))
        .aggregateActivityAnalysis(
            verificationTaskId, startTime, endTime, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus() {
    activityVerificationState.setStatus(AnalysisStatus.RUNNING);
    AnalysisStatus status = activityVerificationStateExecutor.getExecutionStatus(activityVerificationState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_doneExecuting() {
    activityVerificationState.setStatus(AnalysisStatus.RUNNING);
    activityVerificationState.setAnalysisCompletedUntil(startTime.plus(Duration.ofMinutes(5)));
    AnalysisStatus status = activityVerificationStateExecutor.getExecutionStatus(activityVerificationState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.TRANSITION.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetExecutionStatus_doneExecutingPostActivity() {
    activityVerificationState.setHealthVerificationPeriod(HealthVerificationPeriod.POST_ACTIVITY);
    activityVerificationState.setStatus(AnalysisStatus.RUNNING);
    activityVerificationState.setAnalysisCompletedUntil(startTime.plus(Duration.ofMinutes(5)));
    AnalysisStatus status = activityVerificationStateExecutor.getExecutionStatus(activityVerificationState);

    assertThat(status.name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleRunning() {
    activityVerificationState.setStatus(AnalysisStatus.RUNNING);
    when(healthVerificationService.aggregateActivityAnalysis(
             verificationTaskId, startTime, endTime, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY))
        .thenReturn(startTime.plus(Duration.ofMinutes(5)));
    AnalysisState state = activityVerificationStateExecutor.handleRunning(activityVerificationState);

    assertThat(state).isNotNull();
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(activityVerificationState.getAnalysisCompletedUntil()).isEqualTo(startTime.plus(Duration.ofMinutes(5)));
    verify(healthVerificationService, times(1))
        .aggregateActivityAnalysis(
            verificationTaskId, startTime, endTime, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHandleTransition_preActivity() {
    activityVerificationState.setStatus(AnalysisStatus.TRANSITION);
    activityVerificationState.setDuration(Duration.ofMinutes(5));
    AnalysisState state = activityVerificationStateExecutor.handleTransition(activityVerificationState);

    assertThat(state).isNotNull();
    assertThat(state.getStatus().name()).isEqualTo(AnalysisStatus.CREATED.name());
    assertThat(state.getInputs().getStartTime()).isEqualTo(endTime);
    assertThat(((ActivityVerificationState) state).getHealthVerificationPeriod().name())
        .isEqualTo(HealthVerificationPeriod.POST_ACTIVITY.name());
  }
}
