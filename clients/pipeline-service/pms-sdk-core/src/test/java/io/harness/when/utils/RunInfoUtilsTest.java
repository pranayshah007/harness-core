/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.when.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.when.beans.StageWhenCondition;
import io.harness.when.beans.StepWhenCondition;
import io.harness.when.beans.WhenConditionStatus;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class RunInfoUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetStageRunCondition() {
    assertThatThrownBy(()
                           -> RunInfoUtils.getRunConditionForStage(
                               ParameterField.createValueField(StageWhenCondition.builder().build())))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline Status in stage when condition cannot be empty.");
    String successRunCondition = RunInfoUtils.getRunConditionForStage(
        ParameterField.createValueField(StageWhenCondition.builder()
                                            .pipelineStatus(WhenConditionStatus.SUCCESS)
                                            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                            .build()));
    assertThat(successRunCondition).isNotEmpty();
    assertThat(successRunCondition).isEqualTo("<+OnPipelineSuccess> && (<+stage.name> == \"dev\")");

    String failureRunCondition = RunInfoUtils.getRunConditionForStage(
        ParameterField.createValueField(StageWhenCondition.builder()
                                            .pipelineStatus(WhenConditionStatus.FAILURE)
                                            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                            .build()));
    assertThat(failureRunCondition).isNotEmpty();
    assertThat(failureRunCondition).isEqualTo("<+OnPipelineFailure> && (<+stage.name> == \"dev\")");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetStepRunCondition() {
    assertThatThrownBy(
        () -> RunInfoUtils.getRunConditionForStep(ParameterField.createValueField(StepWhenCondition.builder().build())))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage Status in step when condition cannot be empty.");
    String successRunCondition = RunInfoUtils.getRunConditionForStep(
        ParameterField.createValueField(StepWhenCondition.builder()
                                            .stageStatus(WhenConditionStatus.SUCCESS)
                                            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                            .build()));
    assertThat(successRunCondition).isNotEmpty();
    assertThat(successRunCondition).isEqualTo("<+OnStageSuccess> && (<+stage.name> == \"dev\")");

    String failureRunCondition = RunInfoUtils.getRunConditionForStep(
        ParameterField.createValueField(StepWhenCondition.builder()
                                            .stageStatus(WhenConditionStatus.FAILURE)
                                            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                            .build()));
    assertThat(failureRunCondition).isNotEmpty();
    assertThat(failureRunCondition).isEqualTo("<+OnStageFailure> && (<+stage.name> == \"dev\")");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetStepRollbackRunCondition() {
    String defaultRunConditionForRollback = RunInfoUtils.getRunConditionForRollback(null);
    assertThat(defaultRunConditionForRollback).isEqualTo("<+OnRollbackModeExecution> || <+OnStageFailure>");

    assertThatThrownBy(()
                           -> RunInfoUtils.getRunConditionForRollback(
                               ParameterField.createValueField(StepWhenCondition.builder().build())))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage Status in step when condition cannot be empty.");
    String successRunCondition = RunInfoUtils.getRunConditionForRollback(
        ParameterField.createValueField(StepWhenCondition.builder()
                                            .stageStatus(WhenConditionStatus.SUCCESS)
                                            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                            .build()));
    assertThat(successRunCondition).isNotEmpty();
    assertThat(successRunCondition).isEqualTo("<+OnStageSuccess> && (<+stage.name> == \"dev\")");

    String failureRunCondition = RunInfoUtils.getRunConditionForStep(
        ParameterField.createValueField(StepWhenCondition.builder()
                                            .stageStatus(WhenConditionStatus.FAILURE)
                                            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                            .build()));
    assertThat(failureRunCondition).isNotEmpty();
    assertThat(failureRunCondition).isEqualTo("<+OnStageFailure> && (<+stage.name> == \"dev\")");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsRollbackMode() {
    assertThat(RunInfoUtils.isRollbackMode(null)).isFalse();
    assertThat(RunInfoUtils.isRollbackMode(ExecutionMode.NORMAL)).isFalse();
    assertThat(RunInfoUtils.isRollbackMode(ExecutionMode.UNDEFINED_MODE)).isFalse();
    assertThat(RunInfoUtils.isRollbackMode(ExecutionMode.PIPELINE_ROLLBACK)).isTrue();
    assertThat(RunInfoUtils.isRollbackMode(ExecutionMode.POST_EXECUTION_ROLLBACK)).isTrue();
  }
}
