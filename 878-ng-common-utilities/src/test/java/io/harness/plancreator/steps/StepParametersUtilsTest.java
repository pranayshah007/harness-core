/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.when.beans.StageWhenCondition;
import io.harness.when.beans.StepWhenCondition;
import io.harness.when.beans.WhenConditionStatus;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StepParametersUtilsTest extends CategoryTest {
  ParameterField<String> nullField = ParameterField.createValueField(null);
  ParameterField<String> stringField = ParameterField.createValueField("value");

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetStepParameters() {
    StepElementConfig stepElementConfig =
        StepElementConfig.builder()
            .identifier("IDENTIFIER")
            .name("NAME")
            .description("DESCRIPTION")
            .type("TYPE")
            .skipCondition(ParameterField.createValueField("SKIPCONDITION"))
            .when(StepWhenCondition.builder().stageStatus(WhenConditionStatus.SUCCESS).build())
            .build();
    StepElementParameters stepElementParameters = StepParameterCommonUtils.getStepParameters(stepElementConfig).build();
    assertThat(stepElementParameters.getIdentifier()).isEqualTo(stepElementConfig.getIdentifier());
    assertThat(stepElementParameters.getName()).isEqualTo(stepElementConfig.getName());
    assertThat(stepElementParameters.getDescription()).isEqualTo(stepElementConfig.getDescription());
    assertThat(stepElementParameters.getType()).isEqualTo(stepElementConfig.getType());
    assertThat(stepElementParameters.getSkipCondition()).isEqualTo(stepElementConfig.getSkipCondition());
    assertThat(stepElementParameters.getWhen()).isEqualTo(stepElementConfig.getWhen());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetStageParameters() {
    TestAbstractStageNode config =
        TestAbstractStageNode.builder()
            .identifier("IDENTIFIER")
            .name("NAME")
            .description(ParameterField.createValueField("DESCRIPTION"))
            .skipCondition(ParameterField.createValueField("SKIPCONDITION"))
            .when(StageWhenCondition.builder().pipelineStatus(WhenConditionStatus.SUCCESS).build())
            .build();
    StageElementParameters stageParameters = StepParameterCommonUtils.getStageParameters(config).build();
    assertThat(stageParameters.getIdentifier()).isEqualTo(config.getIdentifier());
    assertThat(stageParameters.getName()).isEqualTo(config.getName());
    assertThat(stageParameters.getDescription()).isEqualTo(config.getDescription());
    assertThat(stageParameters.getType()).isEqualTo(config.getType());
    assertThat(stageParameters.getSkipCondition()).isEqualTo(config.getSkipCondition());
    assertThat(stageParameters.getWhen()).isEqualTo(config.getWhen());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetParameterFieldHandleValueNull() {
    assertThat(SdkCoreStepUtils.getParameterFieldHandleValueNull(null)).isNull();
    assertThat(SdkCoreStepUtils.getParameterFieldHandleValueNull(nullField).getValue()).isEqualTo("");
    assertThat(SdkCoreStepUtils.getParameterFieldHandleValueNull(stringField)).isEqualTo(stringField);
  }
}
