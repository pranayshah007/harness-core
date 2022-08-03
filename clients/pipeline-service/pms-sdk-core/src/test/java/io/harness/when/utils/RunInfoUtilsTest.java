/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.when.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
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
    assertThatThrownBy(() -> RunInfoUtils.getRunCondition(StageWhenCondition.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline Status in stage when condition cannot be empty.");
    String successRunCondition =
        RunInfoUtils.getRunCondition(StageWhenCondition.builder()
                                         .pipelineStatus(WhenConditionStatus.SUCCESS)
                                         .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                         .build());
    assertThat(successRunCondition).isNotEmpty();
    assertThat(successRunCondition).isEqualTo("<+OnPipelineSuccess> && (<+stage.name> == \"dev\")");

    String failureRunCondition =
        RunInfoUtils.getRunCondition(StageWhenCondition.builder()
                                         .pipelineStatus(WhenConditionStatus.FAILURE)
                                         .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                         .build());
    assertThat(failureRunCondition).isNotEmpty();
    assertThat(failureRunCondition).isEqualTo("<+OnPipelineFailure> && (<+stage.name> == \"dev\")");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetStepRunCondition() {
    assertThatThrownBy(() -> RunInfoUtils.getRunCondition(StepWhenCondition.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage Status in step when condition cannot be empty.");
    String successRunCondition =
        RunInfoUtils.getRunCondition(StepWhenCondition.builder()
                                         .stageStatus(WhenConditionStatus.SUCCESS)
                                         .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                         .build());
    assertThat(successRunCondition).isNotEmpty();
    assertThat(successRunCondition).isEqualTo("<+OnStageSuccess> && (<+stage.name> == \"dev\")");

    String failureRunCondition =
        RunInfoUtils.getRunCondition(StepWhenCondition.builder()
                                         .stageStatus(WhenConditionStatus.FAILURE)
                                         .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                         .build());
    assertThat(failureRunCondition).isNotEmpty();
    assertThat(failureRunCondition).isEqualTo("<+OnStageFailure> && (<+stage.name> == \"dev\")");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetStepRollbackRunCondition() {
    String defaultRunConditionForRollback = RunInfoUtils.getRunConditionForRollback(null);
    assertThat(defaultRunConditionForRollback).isEqualTo("<+OnStageFailure>");

    assertThatThrownBy(() -> RunInfoUtils.getRunConditionForRollback(StepWhenCondition.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage Status in step when condition cannot be empty.");
    String successRunCondition = RunInfoUtils.getRunConditionForRollback(
        StepWhenCondition.builder()
            .stageStatus(WhenConditionStatus.SUCCESS)
            .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
            .build());
    assertThat(successRunCondition).isNotEmpty();
    assertThat(successRunCondition).isEqualTo("<+OnStageSuccess> && (<+stage.name> == \"dev\")");

    String failureRunCondition =
        RunInfoUtils.getRunCondition(StepWhenCondition.builder()
                                         .stageStatus(WhenConditionStatus.FAILURE)
                                         .condition(ParameterField.createValueField("<+stage.name> == \"dev\""))
                                         .build());
    assertThat(failureRunCondition).isNotEmpty();
    assertThat(failureRunCondition).isEqualTo("<+OnStageFailure> && (<+stage.name> == \"dev\")");
  }
}
