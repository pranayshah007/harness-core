/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinerollback;

import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.pms.execution.OrchestrationFacilitatorType.ASYNC;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.pipelinerollback.PipelineRollbackStageNode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.EmptyStepParameters;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineRollbackStagePlanCreatorTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    PipelineRollbackStagePlanCreator pipelineRollbackStagePlanCreator = new PipelineRollbackStagePlanCreator();
    String prbStageYaml = "type: PipelineRollback\n"
        + "spec:\n"
        + "  __uuid: specUuid\n"
        + "name: Pipeline Rollback Stage\n"
        + "identifier: prb-uuid\n"
        + "__uuid: uuid\n";
    PipelineRollbackStageNode pipelineRollbackStageNode = YamlUtils.read(prbStageYaml, PipelineRollbackStageNode.class);
    PlanCreationResponse planForField =
        pipelineRollbackStagePlanCreator.createPlanForField(null, pipelineRollbackStageNode);
    assertThat(planForField).isNotNull();
    assertThat(planForField.getDependencies()).isNull();
    assertThat(planForField.getYamlUpdates()).isNull();
    assertThat(planForField.getContextMap()).isEmpty();
    assertThat(planForField.getStartingNodeId()).isNull();
    assertThat(planForField.getErrorMessages()).isEmpty();
    assertThat(planForField.getPreservedNodesInRollbackMode()).isNull();
    assertThat(planForField.getServiceAffinityMap()).isNull();

    PlanNode planNode = planForField.getPlanNode();
    assertThat(planNode.getUuid()).isEqualTo("uuid");
    assertThat(planNode.getName()).isEqualTo("Pipeline Rollback Stage");
    assertThat(planNode.getIdentifier()).isEqualTo("prb-uuid");
    assertThat(planNode.getGroup()).isEqualTo("STAGE");
    assertThat(planNode.getStepType().getType()).isEqualTo("PIPELINE_ROLLBACK_STAGE");
    assertThat(planNode.getStepType().getStepCategory()).isEqualTo(STAGE);
    assertThat(planNode.getStepParameters().getClass()).isEqualTo(EmptyStepParameters.class);
    assertThat(planNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo(ASYNC);
    assertThat(planNode.getAdviserObtainments()).isNullOrEmpty();
    assertThat(planNode.getAdvisorObtainmentsForExecutionMode()).isNullOrEmpty();

    Map<String, GraphLayoutNode> layoutNodes = planForField.getGraphLayoutResponse().getLayoutNodes();
    assertThat(layoutNodes).hasSize(1);
    GraphLayoutNode graphLayoutNode = layoutNodes.get("uuid");
    assertThat(graphLayoutNode.getNodeUUID()).isEqualTo("uuid");
    assertThat(graphLayoutNode.getName()).isEqualTo("Pipeline Rollback Stage");
    assertThat(graphLayoutNode.getNodeType()).isEqualTo("PipelineRollback");
    assertThat(graphLayoutNode.getNodeGroup()).isEqualTo("STAGE");
    assertThat(graphLayoutNode.getNodeIdentifier()).isEqualTo("prb-uuid");
  }
}