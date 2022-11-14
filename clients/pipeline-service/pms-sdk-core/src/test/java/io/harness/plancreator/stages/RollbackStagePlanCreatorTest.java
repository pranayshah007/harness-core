/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.fork.ForkStepParameters;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RollbackStagePlanCreatorTest extends CategoryTest {
  @Mock KryoSerializer kryoSerializer;
  YamlNode pipelineNode;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    String pipeline = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      name: s one\n"
        + "      __uuid: uuid1\n"
        + "    __uuid: s1uuid\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        __uuid: uuid2\n"
        + "      __uuid: s2uuid\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "        __uuid: uuid3\n"
        + "      __uuid: s3uuid\n"
        + "    __uuid: parallelUuid\n"
        + "  - stage:\n"
        + "      identifier: s4\n"
        + "      name: s four\n"
        + "      __uuid: uuid4\n"
        + "    __uuid: s4uuid\n";
    pipelineNode = YamlUtils.readTree(pipeline).getNode();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForSingleStage() {
    YamlNode firstStageNode = pipelineNode.getFieldOrThrow("pipeline")
                                  .getNode()
                                  .getFieldOrThrow("stages")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getFieldOrThrow("stage")
                                  .getNode();
    PlanCreationResponse planForSingleStage = RollbackStagePlanCreator.createPlanForSingleStage(firstStageNode, null);
    assertThat(planForSingleStage).isNotNull();
    Map<String, PlanNode> nodes = planForSingleStage.getNodes();
    assertThat(nodes).hasSize(1);
    PlanNode planNode = nodes.get("uuid1_rollbackStage");
    assertThat(planNode).isNotNull();
    assertThat(planNode.getUuid()).isEqualTo("uuid1_rollbackStage");
    assertThat(planNode.getName()).isEqualTo("s one (Rollback Stage)");
    assertThat(planNode.getStepType().getType()).isEqualTo("NG_SECTION");
    assertThat(((NGSectionStepParameters) planNode.getStepParameters()).getChildNodeId())
        .isEqualTo("uuid1_combinedRollback");
    assertThat(planNode.getAdviserObtainments()).hasSize(0);

    YamlNode fourthStageNode = pipelineNode.getFieldOrThrow("pipeline")
                                   .getNode()
                                   .getFieldOrThrow("stages")
                                   .getNode()
                                   .asArray()
                                   .get(2)
                                   .getFieldOrThrow("stage")
                                   .getNode();
    doReturn(new byte[9])
        .when(kryoSerializer)
        .asBytes(NextStepAdviserParameters.builder()
                     .nextNodeId("parallelUuidparallel" + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
                     .build());
    planForSingleStage = RollbackStagePlanCreator.createPlanForSingleStage(fourthStageNode, kryoSerializer);
    assertThat(planForSingleStage).isNotNull();
    nodes = planForSingleStage.getNodes();
    assertThat(nodes).hasSize(1);
    planNode = nodes.get("uuid4_rollbackStage");
    assertThat(planNode).isNotNull();
    assertThat(planNode.getUuid()).isEqualTo("uuid4_rollbackStage");
    assertThat(planNode.getName()).isEqualTo("s four (Rollback Stage)");
    assertThat(planNode.getStepType().getType()).isEqualTo("NG_SECTION");
    assertThat(((NGSectionStepParameters) planNode.getStepParameters()).getChildNodeId())
        .isEqualTo("uuid4_combinedRollback");
    assertThat(planNode.getAdviserObtainments()).hasSize(1);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetAdvisor() {
    YamlNode firstStageNode = pipelineNode.getFieldOrThrow("pipeline")
                                  .getNode()
                                  .getFieldOrThrow("stages")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getFieldOrThrow("stage")
                                  .getNode();
    assertThat(RollbackStagePlanCreator.getAdvisor(null, firstStageNode)).hasSize(0);

    YamlNode secondStageNode = pipelineNode.getFieldOrThrow("pipeline")
                                   .getNode()
                                   .getFieldOrThrow("stages")
                                   .getNode()
                                   .asArray()
                                   .get(1)
                                   .getFieldOrThrow("parallel")
                                   .getNode();
    doReturn(new byte[9])
        .when(kryoSerializer)
        .asBytes(NextStepAdviserParameters.builder()
                     .nextNodeId("uuid1" + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
                     .build());
    List<AdviserObtainment> advisors = RollbackStagePlanCreator.getAdvisor(kryoSerializer, secondStageNode);
    assertThat(advisors).hasSize(1);
    AdviserObtainment adviserObtainment = advisors.get(0);
    assertThat(adviserObtainment.getType().getType()).isEqualTo("NEXT_STAGE");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParallelBlock() {
    YamlNode parallelNode = pipelineNode.getFieldOrThrow("pipeline")
                                .getNode()
                                .getFieldOrThrow("stages")
                                .getNode()
                                .asArray()
                                .get(1)
                                .getFieldOrThrow("parallel")
                                .getNode();
    doReturn(new byte[9])
        .when(kryoSerializer)
        .asBytes(NextStepAdviserParameters.builder()
                     .nextNodeId("uuid1" + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
                     .build());
    doReturn(new byte[9])
        .when(kryoSerializer)
        .asBytes(NextStepAdviserParameters.builder()
                     .nextNodeId("uuid2" + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
                     .build());
    PlanCreationResponse planForParallelBlock =
        RollbackStagePlanCreator.createPlanForParallelBlock(parallelNode, kryoSerializer);
    assertThat(planForParallelBlock).isNotNull();
    Map<String, PlanNode> nodes = planForParallelBlock.getNodes();
    assertThat(nodes).containsOnlyKeys(
        "parallelUuidparallel_rollbackStage", "uuid2_rollbackStage", "uuid3_rollbackStage");
    PlanNode parallelPlanNode = nodes.get("parallelUuidparallel_rollbackStage");
    assertThat(parallelPlanNode.getUuid()).isEqualTo("parallelUuidparallel_rollbackStage");
    assertThat(parallelPlanNode.getName()).isEqualTo("Parallel Block(Rollback Stage)");
    assertThat(parallelPlanNode.getStepType().getType()).isEqualTo("NG_FORK");
    assertThat(((ForkStepParameters) parallelPlanNode.getStepParameters()).getParallelNodeIds())
        .containsExactly("uuid2_rollbackStage", "uuid3_rollbackStage");
    assertThat(parallelPlanNode.getAdviserObtainments()).hasSize(1);
  }
}
