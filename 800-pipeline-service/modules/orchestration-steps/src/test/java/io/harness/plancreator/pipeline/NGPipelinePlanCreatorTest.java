/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
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
public class NGPipelinePlanCreatorTest extends CategoryTest {
  YamlField pipelineYamlField;
  PipelineInfoConfig pipelineInfoConfig;
  PlanCreationContext context;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    pipelineInfoConfig = YamlUtils.read(pipelineYamlField.getNode().toString(), PipelineInfoConfig.class);
    context = PlanCreationContext.builder().currentField(pipelineYamlField).build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    YamlField stagesField = pipelineYamlField.getNode().getField("stages");
    String stagesUuid = Objects.requireNonNull(stagesField).getNode().getUuid();

    NGPipelinePlanCreator ngPipelinePlanCreator = new NGPipelinePlanCreator();
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        ngPipelinePlanCreator.createPlanForChildrenNodes(context, pipelineInfoConfig);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(1);
    assertThat(planForChildrenNodes.containsKey(stagesUuid)).isTrue();
    assertThat(planForChildrenNodes.get(stagesUuid).getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(planForChildrenNodes.get(stagesUuid).getDependencies().getDependenciesMap().containsKey(stagesUuid))
        .isTrue();
    assertThat(planForChildrenNodes.get(stagesUuid).getDependencies().getDependenciesMap().get(stagesUuid))
        .isEqualTo("pipeline/stages");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    YamlField stagesField = pipelineYamlField.getNode().getField("stages");
    String stagesUuid = Objects.requireNonNull(stagesField).getNode().getUuid();
    List<String> childrenNodeIds = Collections.singletonList(stagesUuid);

    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(860).setExecutionUuid("executionUuid").build();
    context.setGlobalContext(Collections.singletonMap(
        "metadata", PlanCreationContextValue.newBuilder().setMetadata(executionMetadata).build()));

    NGPipelinePlanCreator ngPipelinePlanCreator = new NGPipelinePlanCreator();
    PlanNode planForParentNode =
        ngPipelinePlanCreator.createPlanForParentNode(context, pipelineInfoConfig, childrenNodeIds);

    assertThat(planForParentNode).isNotNull();

    assertThat(planForParentNode.getUuid()).isEqualTo(pipelineInfoConfig.getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo("pipeline");

    assertThat(planForParentNode.getStepType())
        .isEqualTo(StepType.newBuilder().setType("PIPELINE_SECTION").setStepCategory(StepCategory.PIPELINE).build());

    assertThat(planForParentNode.getGroup()).isEqualTo("PIPELINE");
    assertThat(planForParentNode.getName()).isEqualTo("plan creator");

    assertThat(planForParentNode.isSkipUnresolvedExpressionsCheck()).isTrue();
    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();

    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");

    assertThat(planForParentNode.getStepParameters()).isNotNull();
  }
}
