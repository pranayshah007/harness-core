/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class RollbackStagePlanCreator {
  public PlanCreationResponse createPlanForRollbackStage(YamlField stageYamlField) {
    YamlNode stageNode = stageYamlField.getNode();
    if (Objects.equals(stageNode.getFieldName(), YAMLFieldNameConstants.PARALLEL)) {
      return createPlanForParallelBlock(stageNode);
    }
    return createPlanForSingleStage(stageNode);
  }

  PlanCreationResponse createPlanForSingleStage(YamlNode stageNode) {
    // todo: create rollback node for non cd stages
    PlanNode rollbackStagePlanNode =
        PlanNode.builder()
            .uuid(stageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
            .name(stageNode.getName() + " " + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_NODE_NAME)
            .identifier(stageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
            .stepType(NGSectionStep.STEP_TYPE)
            .stepParameters(
                NGSectionStepParameters.builder()
                    .childNodeId(stageNode.getUuid() + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX)
                    .logMessage("Rollback Stage for " + stageNode.getIdentifier())
                    .build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(true)
            .build();
    return PlanCreationResponse.builder().node(rollbackStagePlanNode.getUuid(), rollbackStagePlanNode).build();
  }

  PlanCreationResponse createPlanForParallelBlock(YamlNode parallelStageNode) {
    List<YamlNode> stageNodes = parallelStageNode.asArray();
    PlanCreationResponse planCreationResponse = PlanCreationResponse.builder().build();

    List<String> childNodeIDs = new ArrayList<>();
    stageNodes.forEach(stageNode -> {
      planCreationResponse.merge(createPlanForSingleStage(stageNode));
      childNodeIDs.add(stageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX);
    });
    PlanNode parallelRollbackPlanNode =
        PlanNode.builder()
            .uuid(parallelStageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
            .name(NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_NODE_NAME)
            .identifier(parallelStageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
            .stepType(NGForkStep.STEP_TYPE)
            .stepParameters(ForkStepParameters.builder().parallelNodeIds(childNodeIDs).build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .skipExpressionChain(true)
            .build();
    PlanCreationResponse parallelBlockResponse =
        PlanCreationResponse.builder().node(parallelRollbackPlanNode.getUuid(), parallelRollbackPlanNode).build();
    planCreationResponse.merge(parallelBlockResponse);
    return planCreationResponse;
  }
}
