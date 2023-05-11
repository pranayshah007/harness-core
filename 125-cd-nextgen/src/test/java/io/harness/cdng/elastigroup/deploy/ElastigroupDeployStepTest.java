/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.deploy;

import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.elastigroup.ElastigroupStepCommonHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ElastigroupDeployStepTest extends CategoryTest {
  public static final StepElementParameters stepParameters =
      StepElementParameters.builder()
          .spec(ElastigroupDeployStepParameters.builder().build())
          .timeout(ParameterField.createValueField("15"))
          .build();

  @Mock ElastigroupDeployStepHelper stepHelper;
  @Mock ElastigroupStepCommonHelper elastigroupStepCommonHelper;

  @InjectMocks ElastigroupDeployStep step;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void setParameterClassShouldBeStep() {
    assertThat(step.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testShouldPrepareTasks() {
    // Given
    Ambiance ambiance = mock(Ambiance.class);
    doReturn(Arrays.asList(UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DOWN_SCALE_COMMAND_UNIT,
                 DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT))
        .when(stepHelper)
        .getExecutionUnits();

    // When
    step.obtainTaskAfterRbac(ambiance, stepParameters, null);

    // Then
    verify(stepHelper)
        .prepareTaskRequest(eq(ambiance), any(TaskData.class),
            eq(Arrays.asList(UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DOWN_SCALE_COMMAND_UNIT,
                DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT)),
            eq(TaskType.ELASTIGROUP_DEPLOY.getDisplayName()), anyList());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldHandleNullResponse() throws Exception {
    // given
    Ambiance ambiance = mock(Ambiance.class);
    ElastigroupDeployTaskResponse response = null;

    // when
    step.handleTaskResultWithSecurityContext(ambiance, stepParameters, () -> response);

    // then
    ArgumentCaptor<InvalidArgumentsException> captor = ArgumentCaptor.forClass(InvalidArgumentsException.class);
    verify(stepHelper).handleTaskFailure(eq(ambiance), eq(stepParameters), captor.capture());

    assertThat(captor.getValue())
        .isNotNull()
        .extracting(Throwable::getMessage)
        .isEqualTo("Failed to process elastigroup deploy task response");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldHandleExceptionResponse() throws Exception {
    // given
    Ambiance ambiance = mock(Ambiance.class);
    ElastigroupDeployTaskResponse response = null;

    // when
    step.handleTaskResultWithSecurityContext(
        ambiance, stepParameters, () -> { throw new IllegalStateException("test"); });

    // then
    ArgumentCaptor<IllegalStateException> captor = ArgumentCaptor.forClass(IllegalStateException.class);
    verify(stepHelper).handleTaskFailure(eq(ambiance), eq(stepParameters), captor.capture());

    assertThat(captor.getValue()).isNotNull().extracting(Throwable::getMessage).isEqualTo("test");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulResponse() throws Exception {
    // given
    Ambiance ambiance = mock(Ambiance.class);
    ElastigroupDeployTaskResponse response = ElastigroupDeployTaskResponse.builder().build();

    // when
    step.handleTaskResultWithSecurityContext(ambiance, stepParameters, () -> response);

    // then
    verify(stepHelper).handleTaskResult(eq(ambiance), eq(stepParameters), eq(response), isNull());
  }
}
