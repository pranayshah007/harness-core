/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StagesStep;
import io.harness.steps.common.NGSectionStepParameters;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class StagesPlanCreatorTest extends CategoryTest {
  YamlField stagesYamlField;
  StagesConfig stagesConfig;
  PlanCreationContext context;

  @Mock KryoSerializer kryoSerializer;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    stagesConfig = YamlUtils.read(stagesYamlField.getNode().toString(), StagesConfig.class);

    context = PlanCreationContext.builder().currentField(stagesYamlField).build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    String approvalStageUuid = Objects.requireNonNull(stages.get(0).getField("stage")).getNode().getUuid();
    List<String> childrenNodeIds = Collections.singletonList(approvalStageUuid);
    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null);
    PlanNode planForParentNode = stagesPlanCreator.createPlanForParentNode(context, stagesConfig, childrenNodeIds);
    assertThat(planForParentNode).isNotNull();

    assertThat(planForParentNode.getUuid()).isEqualTo(stagesYamlField.getNode().getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo("stages");
    assertThat(planForParentNode.getStepType()).isEqualTo(StagesStep.STEP_TYPE);
    assertThat(planForParentNode.getGroup()).isEqualTo("STAGES");
    assertThat(planForParentNode.getName()).isEqualTo("stages");

    assertThat(planForParentNode.getStepParameters() instanceof NGSectionStepParameters).isTrue();
    assertThat(((NGSectionStepParameters) planForParentNode.getStepParameters()).getChildNodeId())
        .isEqualTo(approvalStageUuid);
    assertThat(((NGSectionStepParameters) planForParentNode.getStepParameters()).getLogMessage()).isEqualTo("Stages");

    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");

    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  @Ignore("CI-6025: TI team to follow up")
  public void testCreatePlanForChildrenNodes() {
    doReturn(new byte[2]).when(kryoSerializer).asBytes(any());
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();
    YamlField parallelDeploymentStages = stages.get(1).getField("parallel");
    assertThat(parallelDeploymentStages).isNotNull();
    String parallelStagesUuid = parallelDeploymentStages.getNode().getUuid();

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(kryoSerializer);
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        stagesPlanCreator.createPlanForChildrenNodes(context, stagesConfig);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(4);
    assertThat(planForChildrenNodes.containsKey(approvalStageUuid)).isTrue();
    assertThat(planForChildrenNodes.containsKey(parallelStagesUuid)).isTrue();
    assertThat(planForChildrenNodes.containsKey(approvalStageUuid + "_rollbackStage")).isTrue();
    assertThat(planForChildrenNodes.containsKey(parallelStagesUuid + "_rollbackStage")).isTrue();

    PlanCreationResponse approvalStageResponse = planForChildrenNodes.get(approvalStageUuid);
    assertThat(approvalStageResponse.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(approvalStageResponse.getDependencies().getDependenciesMap().containsKey(approvalStageUuid)).isTrue();
    assertThat(approvalStageResponse.getDependencies().getDependenciesMap().get(approvalStageUuid))
        .isEqualTo("pipeline/stages/[0]/stage");

    PlanCreationResponse parallelStagesResponse = planForChildrenNodes.get(parallelStagesUuid);
    assertThat(parallelStagesResponse.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(parallelStagesResponse.getDependencies().getDependenciesMap().containsKey(parallelStagesUuid)).isTrue();
    assertThat(parallelStagesResponse.getDependencies().getDependenciesMap().get(parallelStagesUuid))
        .isEqualTo("pipeline/stages/[1]/parallel");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo() {
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();
    YamlField parallelDeploymentStages = stages.get(1).getField("parallel");
    assertThat(parallelDeploymentStages).isNotNull();
    String parallelStagesUuid = parallelDeploymentStages.getNode().getUuid();

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(context, stagesConfig);
    assertThat(layoutNodeInfo).isNotNull();
    assertThat(layoutNodeInfo.getStartingNodeId()).isEqualTo(approvalStageUuid);
    assertThat(layoutNodeInfo.getLayoutNodes()).hasSize(1);
    assertThat(layoutNodeInfo.getLayoutNodes().containsKey(approvalStageUuid)).isTrue();

    GraphLayoutNode stageLayoutNode = layoutNodeInfo.getLayoutNodes().get(approvalStageUuid);
    assertThat(stageLayoutNode.getNodeUUID()).isEqualTo(approvalStageUuid);
    assertThat(stageLayoutNode.getNodeType()).isEqualTo("Approval");
    assertThat(stageLayoutNode.getName()).isEqualTo("a1-1");
    assertThat(stageLayoutNode.getNodeGroup()).isEqualTo("STAGE");
    assertThat(stageLayoutNode.getNodeIdentifier()).isEqualTo("a11");

    EdgeLayoutList edgeLayoutList = stageLayoutNode.getEdgeLayoutList();
    assertThat(edgeLayoutList).isNotNull();
    assertThat(edgeLayoutList.getNextIdsList()).hasSize(1);
    assertThat(edgeLayoutList.getNextIds(0)).isEqualTo(parallelStagesUuid);
  }
}
