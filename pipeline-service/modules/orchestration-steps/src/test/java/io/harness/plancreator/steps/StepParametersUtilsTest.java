/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepParametersUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.stage.ApprovalStageNode;
import io.harness.steps.shellscript.ShellScriptStepNode;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class StepParametersUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetStageParameters() {
    ApprovalStageNode approvalStageNode = new ApprovalStageNode();
    approvalStageNode.setUuid("UVid");
    approvalStageNode.setIdentifier("IDENTIFIER");
    approvalStageNode.setName("name");
    ParameterField<String> description = ParameterField.<String>builder().value("Description").build();
    ParameterField<String> skipCondition = ParameterField.<String>builder().value("Skip-Condition").build();
    approvalStageNode.setDescription(description);
    approvalStageNode.setSkipCondition(skipCondition);
    approvalStageNode.setType(ApprovalStageNode.StepType.Approval);

    StageElementParameters stageElementParameters = StepParametersUtils.getStageParameters(approvalStageNode).build();

    assertThat(stageElementParameters.getIdentifier()).isEqualTo(approvalStageNode.getIdentifier());
    assertThat(stageElementParameters.getName()).isEqualTo(approvalStageNode.getName());
    assertThat(stageElementParameters.getDescription()).isEqualTo(approvalStageNode.getDescription());
    assertThat(stageElementParameters.getType()).isEqualTo(approvalStageNode.getType());
    assertThat(stageElementParameters.getSkipCondition()).isEqualTo(approvalStageNode.getSkipCondition());
    assertThat(stageElementParameters.getWhen()).isEqualTo(approvalStageNode.getWhen());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetStepParameters() {
    ShellScriptStepNode shellScriptStepNode = new ShellScriptStepNode();
    shellScriptStepNode.setUuid("UVid");
    shellScriptStepNode.setIdentifier("IDENTIFIER");
    shellScriptStepNode.setName("name");
    ParameterField<String> skipCondition = ParameterField.<String>builder().value("Skip-Condition").build();
    shellScriptStepNode.setDescription("description");
    shellScriptStepNode.setSkipCondition(skipCondition);

    StepElementParameters stepElementParameters = StepParametersUtils.getStepParameters(shellScriptStepNode).build();

    assertThat(stepElementParameters.getIdentifier()).isEqualTo(shellScriptStepNode.getIdentifier());
    assertThat(stepElementParameters.getName()).isEqualTo(shellScriptStepNode.getName());
    assertThat(stepElementParameters.getDescription()).isEqualTo(shellScriptStepNode.getDescription());
    assertThat(stepElementParameters.getType()).isEqualTo(shellScriptStepNode.getType());
    assertThat(stepElementParameters.getSkipCondition()).isEqualTo(shellScriptStepNode.getSkipCondition());
    assertThat(stepElementParameters.getWhen()).isEqualTo(shellScriptStepNode.getWhen());
  }
}
