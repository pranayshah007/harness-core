/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.common.NGExecutionStep;
import io.harness.steps.common.NGSectionStepParameters;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ExecutionPmsPlanCreatorTest extends CategoryTest {
  YamlField executionYamlField;
  ExecutionElementConfig executionElementConfig;
  PlanCreationContext context;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stagesNodes = stagesYamlField.getNode().asArray();
    YamlField approvalStageField = stagesNodes.get(0).getField("stage");
    YamlField approvalSpecField = Objects.requireNonNull(approvalStageField).getNode().getField("spec");
    executionYamlField = Objects.requireNonNull(approvalSpecField).getNode().getField("execution");
    assertThat(executionYamlField).isNotNull();
    executionElementConfig = YamlUtils.read(executionYamlField.getNode().toString(), ExecutionElementConfig.class);

    context = PlanCreationContext.builder().currentField(executionYamlField).build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    YamlField stepsField = executionYamlField.getNode().getField("steps");
    assertThat(stepsField).isNotNull();
    YamlField stepGroupField = stepsField.getNode().asArray().get(0).getField("stepGroup");
    assertThat(stepGroupField).isNotNull();
    YamlField parallelField = stepsField.getNode().asArray().get(1).getField("parallel");
    assertThat(parallelField).isNotNull();

    ExecutionPmsPlanCreator executionPmsPlanCreator = new ExecutionPmsPlanCreator();
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        executionPmsPlanCreator.createPlanForChildrenNodes(context, executionElementConfig);
    assertThat(planForChildrenNodes).hasSize(1);

    assertThat(planForChildrenNodes.containsKey(stepsField.getNode().getUuid())).isTrue();
    PlanCreationResponse stepsResponse = planForChildrenNodes.get(stepsField.getNode().getUuid());
    assertThat(stepsResponse.getDependencies()).isNotNull();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().containsKey(stepsField.getNode().getUuid()))
        .isTrue();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().get(stepsField.getNode().getUuid()))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    YamlField stepsField = executionYamlField.getNode().getField("steps");
    assertThat(stepsField).isNotNull();

    ExecutionPmsPlanCreator executionPmsPlanCreator = new ExecutionPmsPlanCreator();
    PlanNode planForParentNode = executionPmsPlanCreator.createPlanForParentNode(context, executionElementConfig, null);
    assertThat(planForParentNode.getUuid()).isEqualTo(executionYamlField.getNode().getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo("execution");
    assertThat(planForParentNode.getStepType()).isEqualTo(NGExecutionStep.STEP_TYPE);
    assertThat(planForParentNode.getGroup()).isEqualTo("EXECUTION");
    assertThat(planForParentNode.getName()).isEqualTo("Execution");
    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");
    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();

    assertThat(planForParentNode.getStepParameters() instanceof NGSectionStepParameters).isTrue();
    NGSectionStepParameters stepParameters = (NGSectionStepParameters) planForParentNode.getStepParameters();
    assertThat(stepParameters.getChildNodeId()).isEqualTo(stepsField.getNode().getUuid());
    assertThat(stepParameters.getLogMessage()).isEqualTo("Execution Element");
  }
}
