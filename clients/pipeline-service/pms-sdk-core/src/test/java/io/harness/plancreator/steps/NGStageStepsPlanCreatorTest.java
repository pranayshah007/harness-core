/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class NGStageStepsPlanCreatorTest extends PmsSdkCoreTestBase {
  @Inject NGStageStepsPlanCreator NGStageStepsPlanCreator;
  YamlField stepsYamlField;
  StepsExecutionConfig stepElementConfig;
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
    YamlField executionField = Objects.requireNonNull(approvalSpecField).getNode().getField("execution");

    YamlField stepGroupYamlField =
        executionField.getNode().getField("steps").getNode().asArray().get(0).getField("stepGroup");
    assertThat(stepGroupYamlField).isNotNull();

    stepsYamlField = Objects.requireNonNull(stepGroupYamlField).getNode().getField("steps");
    stepElementConfig = YamlUtils.read(stepsYamlField.getNode().toString(), StepsExecutionConfig.class);

    context = PlanCreationContext.builder().currentField(stepsYamlField).build();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    List<YamlNode> stepsField = stepsYamlField.getNode().asArray();
    assertThat(stepsField).isNotNull();

    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        NGStageStepsPlanCreator.createPlanForChildrenNodes(context, stepElementConfig);
    assertThat(planForChildrenNodes).hasSize(2);

    YamlNode firstStep = stepsField.get(0).getField("step").getNode();
    assertThat(planForChildrenNodes.containsKey(firstStep.getUuid())).isTrue();
    PlanCreationResponse stepsResponse = planForChildrenNodes.get(firstStep.getUuid());
    assertThat(stepsResponse.getDependencies()).isNotNull();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().containsKey(firstStep.getUuid())).isTrue();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().get(firstStep.getUuid()))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[0]/stepGroup/steps/[0]/step");

    YamlNode secondStep = stepsField.get(1).getField("step").getNode();
    assertThat(planForChildrenNodes.containsKey(secondStep.getUuid())).isTrue();
    stepsResponse = planForChildrenNodes.get(secondStep.getUuid());
    assertThat(stepsResponse.getDependencies()).isNotNull();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().containsKey(secondStep.getUuid())).isTrue();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().get(secondStep.getUuid()))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[0]/stepGroup/steps/[1]/step");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    String firstChild = stepsYamlField.getNode().asArray().get(0).getField("step").getNode().getUuid();

    PlanNode planForParentNode = NGStageStepsPlanCreator.createPlanForParentNode(
        context, stepElementConfig, Collections.singletonList(firstChild));
    assertThat(planForParentNode.getUuid()).isEqualTo(stepsYamlField.getNode().getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo(YAMLFieldNameConstants.STEPS);
    assertThat(planForParentNode.getStepType()).isEqualTo(NGSectionStepWithRollbackInfo.STEP_TYPE);
    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");
  }
}
