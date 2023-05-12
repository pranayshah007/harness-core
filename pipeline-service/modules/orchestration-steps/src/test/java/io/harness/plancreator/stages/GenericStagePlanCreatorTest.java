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
import static org.mockito.Mockito.doReturn;

import io.harness.OrchestrationStepsTestBase;
import io.harness.advisers.nextstep.NextStageAdviserParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.approval.ApprovalStagePlanCreatorV2;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.approval.stage.ApprovalStageNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class GenericStagePlanCreatorTest extends OrchestrationStepsTestBase {
  YamlField approvalStageYamlField;
  PlanCreationContext approvalStageContext;
  ApprovalStageNode approvalStageConfig;

  @InjectMocks ApprovalStagePlanCreatorV2 approvalStagePlanCreator;
  @Mock KryoSerializer kryoSerializer;
  @Inject KryoSerializer kryoSerializerUnMocked;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    approvalStageYamlField = stageYamlNodes.get(0).getField("stage");
    approvalStageContext = PlanCreationContext.builder().currentField(approvalStageYamlField).build();
    approvalStageConfig = YamlUtils.read(approvalStageYamlField.getNode().toString(), ApprovalStageNode.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    YamlField sibling = approvalStageYamlField.getNode().nextSiblingFromParentArray(
        approvalStageYamlField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
    assertThat(sibling).isNotNull();
    NextStageAdviserParameters nextStepAdviserParameters =
        NextStageAdviserParameters.builder().nextNodeId(sibling.getNode().getUuid()).build();
    byte[] siblingAsBytes = kryoSerializerUnMocked.asBytes(nextStepAdviserParameters);
    doReturn(siblingAsBytes).when(kryoSerializer).asBytes(nextStepAdviserParameters);

    PlanNode approvalStagePlanNode =
        approvalStagePlanCreator.createPlanForParentNode(approvalStageContext, approvalStageConfig, null);
    assertThat(approvalStagePlanNode).isNotNull();
    assertThat(approvalStagePlanNode.getUuid()).isEqualTo(approvalStageYamlField.getNode().getUuid());
    assertThat(approvalStagePlanNode.getName()).isEqualTo("a1-1");
    assertThat(approvalStagePlanNode.getIdentifier()).isEqualTo("a11");
    assertThat(approvalStagePlanNode.getGroup()).isEqualTo("STAGE");
    assertThat(approvalStagePlanNode.getStepType())
        .isEqualTo(StepType.newBuilder().setType("APPROVAL_STAGE").setStepCategory(StepCategory.STAGE).build());
    assertThat(approvalStagePlanNode.getGroup()).isEqualTo("STAGE");
    assertThat(approvalStagePlanNode.getSkipCondition()).isNull();
    assertThat(approvalStagePlanNode.getWhenCondition()).isEqualTo("<+OnPipelineSuccess>");

    assertThat(approvalStagePlanNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(approvalStagePlanNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");

    assertThat(approvalStagePlanNode.getAdviserObtainments()).hasSize(1);
    assertThat(approvalStagePlanNode.getAdviserObtainments().get(0).getType().getType()).isEqualTo("NEXT_STAGE");
    assertThat(approvalStagePlanNode.getAdviserObtainments().get(0).getParameters())
        .isEqualTo(ByteString.copyFrom(siblingAsBytes));

    StepParameters stepParameters = approvalStagePlanNode.getStepParameters();
    assertThat(stepParameters).isNotNull();
    assertThat(stepParameters instanceof StageElementParameters).isTrue();
  }
}
