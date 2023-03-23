/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinerollback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.pipelinerollback.PipelineRollbackStageNode;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.EmptyStepParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(PIPELINE)
public class PipelineRollbackStagePlanCreator implements PartialPlanCreator<PipelineRollbackStageNode> {
  @Override
  public Class<PipelineRollbackStageNode> getFieldClass() {
    return PipelineRollbackStageNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE));
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PipelineRollbackStageNode stageNode) {
    PlanNode planNode =
        PlanNode.builder()
            .uuid(stageNode.getUuid())
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepCategory.STAGE.name())
            .stepType(PipelineRollbackStageStep.STEP_TYPE)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .build();
    Map<String, GraphLayoutNode> stageYamlFieldMap = Collections.singletonMap(stageNode.getUuid(),
        GraphLayoutNode.newBuilder()
            .setNodeUUID(stageNode.getUuid())
            .setNodeType(stageNode.getType())
            .setName(stageNode.getName())
            .setNodeGroup(StepOutcomeGroup.STAGE.name())
            .setNodeIdentifier(stageNode.getIdentifier())
            .build());
    return PlanCreationResponse.builder()
        .graphLayoutResponse(GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build())
        .planNode(planNode)
        .build();
  }
}
