/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages.parallel;

import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.fork.ForkStepParameters;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.protobuf.ProtocolStringList;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ParallelPlanCreatorTest extends CategoryTest {
  YamlField parallelStagesField;
  YamlField stage0Field;
  YamlField stage1Field;
  PlanCreationContext stageContext;

  YamlField parallelStepsField;
  YamlField step0Field;
  YamlField step1Field;
  PlanCreationContext stepContext;

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
    parallelStagesField = stagesNodes.get(1).getField("parallel");
    assertThat(parallelStagesField).isNotNull();

    stageContext = PlanCreationContext.builder().currentField(parallelStagesField).build();

    stage0Field = parallelStagesField.getNode().asArray().get(0).getField("stage");
    stage1Field = parallelStagesField.getNode().asArray().get(1).getField("stage");

    YamlField approvalStageField = stagesNodes.get(0).getField("stage");
    YamlField approvalSpecField = Objects.requireNonNull(approvalStageField).getNode().getField("spec");
    YamlField approvalExecutionField = Objects.requireNonNull(approvalSpecField).getNode().getField("execution");
    YamlField approvalStepsField = Objects.requireNonNull(approvalExecutionField).getNode().getField("steps");
    List<YamlNode> stepsNodes = Objects.requireNonNull(approvalStepsField).getNode().asArray();
    parallelStepsField = stepsNodes.get(1).getField("parallel");
    assertThat(parallelStepsField).isNotNull();

    stepContext = PlanCreationContext.builder().currentField(parallelStepsField).build();

    step0Field = parallelStepsField.getNode().asArray().get(0).getField("step");
    step1Field = parallelStepsField.getNode().asArray().get(1).getField("step");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetDependencyNodeIds() {
    List<YamlField> result = PlanCreatorUtils.getDependencyNodeIdsForParallelNode(parallelStagesField);
    assertThat(result).isNotEmpty();
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isEqualTo(stage0Field);
    assertThat(result.get(1)).isEqualTo(stage1Field);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodesForParallelStages() {
    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        parallelPlanCreator.createPlanForChildrenNodes(stageContext, parallelStagesField);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(2);
    assertThat(planForChildrenNodes.containsKey(stage0Field.getNode().getUuid())).isTrue();
    assertThat(planForChildrenNodes.containsKey(stage1Field.getNode().getUuid())).isTrue();

    PlanCreationResponse planCreationResponse0 = planForChildrenNodes.get(stage0Field.getNode().getUuid());
    PlanCreationResponse planCreationResponse1 = planForChildrenNodes.get(stage1Field.getNode().getUuid());
    assertThat(planCreationResponse0.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(planCreationResponse0.getNodes()).isNullOrEmpty();
    assertThat(planCreationResponse1.getNodes()).isNullOrEmpty();

    assertThat(
        planCreationResponse0.getDependencies().getDependenciesMap().containsKey(stage0Field.getNode().getUuid()))
        .isTrue();
    assertThat(
        planCreationResponse1.getDependencies().getDependenciesMap().containsKey(stage1Field.getNode().getUuid()))
        .isTrue();

    assertThat(planCreationResponse0.getDependencies().getDependenciesMap().get(stage0Field.getNode().getUuid()))
        .isEqualTo("pipeline/stages/[1]/parallel/[0]/stage");
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(stage1Field.getNode().getUuid()))
        .isEqualTo("pipeline/stages/[1]/parallel/[1]/stage");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfoForParallelStages() {
    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    GraphLayoutResponse layoutNodeInfo = parallelPlanCreator.getLayoutNodeInfo(stageContext, parallelStagesField);
    assertThat(layoutNodeInfo).isNotNull();
    assertThat(layoutNodeInfo.getStartingNodeId()).isNull();
    Map<String, GraphLayoutNode> layoutNodes = layoutNodeInfo.getLayoutNodes();
    assertThat(layoutNodes).hasSize(3);
    assertThat(layoutNodes.containsKey(stage0Field.getNode().getUuid())).isTrue();
    assertThat(layoutNodes.containsKey(stage1Field.getNode().getUuid())).isTrue();
    assertThat(layoutNodes.containsKey(parallelStagesField.getNode().getUuid())).isTrue();

    GraphLayoutNode graphLayoutNode0 = layoutNodes.get(stage0Field.getNode().getUuid());
    assertThat(graphLayoutNode0.getNodeUUID()).isEqualTo(stage0Field.getNode().getUuid());
    assertThat(graphLayoutNode0.getNodeGroup()).isEqualTo("STAGE");
    assertThat(graphLayoutNode0.getName()).isEqualTo("d1");
    assertThat(graphLayoutNode0.getNodeType()).isEqualTo("Deployment");
    assertThat(graphLayoutNode0.getNodeIdentifier()).isEqualTo("d1");
    assertThat(graphLayoutNode0.getEdgeLayoutList()).isEqualTo(EdgeLayoutList.newBuilder().build());

    GraphLayoutNode graphLayoutNode1 = layoutNodes.get(stage1Field.getNode().getUuid());
    assertThat(graphLayoutNode1.getNodeUUID()).isEqualTo(stage1Field.getNode().getUuid());
    assertThat(graphLayoutNode1.getNodeGroup()).isEqualTo("STAGE");
    assertThat(graphLayoutNode1.getName()).isEqualTo("d2");
    assertThat(graphLayoutNode1.getNodeType()).isEqualTo("Deployment");
    assertThat(graphLayoutNode1.getNodeIdentifier()).isEqualTo("d2");
    assertThat(graphLayoutNode1.getEdgeLayoutList()).isEqualTo(EdgeLayoutList.newBuilder().build());

    GraphLayoutNode parallelGraphLayoutNode = layoutNodes.get(parallelStagesField.getNode().getUuid());
    assertThat(parallelGraphLayoutNode.getNodeUUID()).isEqualTo(parallelStagesField.getNode().getUuid());
    assertThat(parallelGraphLayoutNode.getNodeGroup()).isEqualTo("STAGE");
    assertThat(parallelGraphLayoutNode.getNodeType()).isEqualTo("parallel");
    assertThat(parallelGraphLayoutNode.getNodeIdentifier())
        .isEqualTo("parallel" + parallelStagesField.getNode().getUuid());
    ProtocolStringList nextIdsList = parallelGraphLayoutNode.getEdgeLayoutList().getCurrentNodeChildrenList();
    assertThat(nextIdsList.size()).isEqualTo(2);
    assertThat(nextIdsList.contains(stage0Field.getNode().getUuid())).isTrue();
    assertThat(nextIdsList.contains(stage1Field.getNode().getUuid())).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNodeForParallelStages() {
    List<String> childrenNodeIds = Lists.newArrayList(stage0Field.getNode().getUuid(), stage1Field.getNode().getUuid());

    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    PlanNode planForParentNode =
        parallelPlanCreator.createPlanForParentNode(stageContext, parallelStagesField, childrenNodeIds);
    assertThat(planForParentNode).isNotNull();
    assertThat(planForParentNode.getUuid()).isEqualTo(parallelStagesField.getNode().getUuid());
    assertThat(planForParentNode.getName()).isEqualTo("parallel");
    assertThat(planForParentNode.getIdentifier()).isEqualTo("parallel" + parallelStagesField.getNode().getUuid());
    assertThat(planForParentNode.getStepType())
        .isEqualTo(StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build());
    assertThat(planForParentNode.getStepParameters() instanceof ForkStepParameters).isTrue();
    assertThat(((ForkStepParameters) planForParentNode.getStepParameters()).getParallelNodeIds())
        .isEqualTo(childrenNodeIds);
    assertThat(planForParentNode.getAdviserObtainments()).hasSize(0);
    assertThat(planForParentNode.isSkipExpressionChain()).isTrue();
    assertThat(planForParentNode.isSkipUnresolvedExpressionsCheck()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodesForParallelSteps() {
    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        parallelPlanCreator.createPlanForChildrenNodes(stepContext, parallelStepsField);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(2);
    assertThat(planForChildrenNodes.containsKey(step0Field.getNode().getUuid())).isTrue();
    assertThat(planForChildrenNodes.containsKey(step1Field.getNode().getUuid())).isTrue();

    PlanCreationResponse planCreationResponse0 = planForChildrenNodes.get(step0Field.getNode().getUuid());
    PlanCreationResponse planCreationResponse1 = planForChildrenNodes.get(step1Field.getNode().getUuid());
    assertThat(planCreationResponse0.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(planCreationResponse0.getNodes()).isNullOrEmpty();
    assertThat(planCreationResponse1.getNodes()).isNullOrEmpty();

    assertThat(planCreationResponse0.getDependencies().getDependenciesMap().containsKey(step0Field.getNode().getUuid()))
        .isTrue();
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().containsKey(step1Field.getNode().getUuid()))
        .isTrue();

    assertThat(planCreationResponse0.getDependencies().getDependenciesMap().get(step0Field.getNode().getUuid()))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[1]/parallel/[0]/step");
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(step1Field.getNode().getUuid()))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[1]/parallel/[1]/step");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfoForParallelSteps() {
    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    GraphLayoutResponse layoutNodeInfo = parallelPlanCreator.getLayoutNodeInfo(stepContext, parallelStepsField);
    assertThat(layoutNodeInfo).isEqualTo(GraphLayoutResponse.builder().build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfoForParallelStagesWithPRBStage() throws IOException {
    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    String stagesYaml = "stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "  - stage:\n"
        + "      identifier: prb-abc\n"
        + "      name: Pipeline Rollback Stage\n"
        + "      type: PipelineRollback\n";
    YamlField stagesYamlField = YamlUtils.injectUuidInYamlField(stagesYaml);
    YamlField parallelField =
        stagesYamlField.getNode().getField("stages").getNode().asArray().get(0).getField("parallel");
    GraphLayoutResponse layoutNodeInfo = parallelPlanCreator.getLayoutNodeInfo(stageContext, parallelField);
    assertThat(layoutNodeInfo).isNotNull();
    for (GraphLayoutNode graphLayoutNode : layoutNodeInfo.getLayoutNodes().values()) {
      assertThat(graphLayoutNode.getEdgeLayoutList().getNextIdsList()).isNullOrEmpty();
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNodeForParallelSteps() {
    List<String> childrenNodeIds = Lists.newArrayList(step0Field.getNode().getUuid(), step1Field.getNode().getUuid());

    ParallelPlanCreator parallelPlanCreator = new ParallelPlanCreator();
    PlanNode planForParentNode =
        parallelPlanCreator.createPlanForParentNode(stepContext, parallelStepsField, childrenNodeIds);
    assertThat(planForParentNode).isNotNull();
    assertThat(planForParentNode.getUuid()).isEqualTo(parallelStepsField.getNode().getUuid());
    assertThat(planForParentNode.getName()).isEqualTo("parallel");
    assertThat(planForParentNode.getIdentifier()).isEqualTo("parallel" + parallelStepsField.getNode().getUuid());
    assertThat(planForParentNode.getStepType())
        .isEqualTo(StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build());
    assertThat(planForParentNode.getStepParameters() instanceof ForkStepParameters).isTrue();
    assertThat(((ForkStepParameters) planForParentNode.getStepParameters()).getParallelNodeIds())
        .isEqualTo(childrenNodeIds);
    assertThat(planForParentNode.getAdviserObtainments()).hasSize(0);
    assertThat(planForParentNode.isSkipExpressionChain()).isTrue();
    assertThat(planForParentNode.isSkipUnresolvedExpressionsCheck()).isTrue();
  }
}
