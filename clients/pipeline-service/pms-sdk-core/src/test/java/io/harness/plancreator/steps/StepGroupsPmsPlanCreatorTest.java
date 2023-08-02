/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(PIPELINE)
public class StepGroupsPmsPlanCreatorTest extends PmsSdkCoreTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject @InjectMocks StepGroupPMSPlanCreator stepGroupPMSPlanCreator;
  YamlField stepGroupYamlField;
  StepGroupElementConfig stepElementConfig;
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

    stepGroupYamlField = executionField.getNode().getField("steps").getNode().asArray().get(0).getField("stepGroup");
    assertThat(stepGroupYamlField).isNotNull();

    stepElementConfig = YamlUtils.read(stepGroupYamlField.getNode().toString(), StepGroupElementConfig.class);

    context = PlanCreationContext.builder()
                  .currentField(stepGroupYamlField)
                  .globalContext("metadata",
                      PlanCreationContextValue.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                  .build();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    YamlField stepsField = stepGroupYamlField.getNode().getField("steps");
    assertThat(stepsField).isNotNull();

    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        stepGroupPMSPlanCreator.createPlanForChildrenNodes(context, stepElementConfig);
    assertThat(planForChildrenNodes).hasSize(1);

    assertThat(planForChildrenNodes.containsKey(stepsField.getNode().getUuid())).isTrue();
    PlanCreationResponse stepsResponse = planForChildrenNodes.get(stepsField.getNode().getUuid());
    assertThat(stepsResponse.getDependencies()).isNotNull();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().containsKey(stepsField.getNode().getUuid()))
        .isTrue();
    assertThat(stepsResponse.getDependencies().getDependenciesMap().get(stepsField.getNode().getUuid()))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[0]/stepGroup/steps");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    YamlField stepsField = stepGroupYamlField.getNode().getField("steps");
    assertThat(stepsField).isNotNull();

    PlanNode planForParentNode = stepGroupPMSPlanCreator.createPlanForParentNode(context, stepElementConfig, null);
    assertThat(planForParentNode.getUuid()).isEqualTo(stepGroupYamlField.getNode().getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo(stepElementConfig.getIdentifier());
    assertThat(planForParentNode.getStepType()).isEqualTo(StepGroupStep.STEP_TYPE);
    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");
    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAdviserObtainmentFromMetaData() {
    List<AdviserObtainment> adviserObtainmentList =
        stepGroupPMSPlanCreator.getAdviserObtainmentFromMetaData(kryoSerializer, stepGroupYamlField, false);
    assertThat(adviserObtainmentList).hasSize(2);
    assertThat(adviserObtainmentList.get(0).getType().toString()).isEqualTo("type: \"RETRY_STEPGROUP\"\n");
    assertThat(adviserObtainmentList.get(1).getType().toString()).isEqualTo("type: \"NEXT_STEP\"\n");
  }
}
