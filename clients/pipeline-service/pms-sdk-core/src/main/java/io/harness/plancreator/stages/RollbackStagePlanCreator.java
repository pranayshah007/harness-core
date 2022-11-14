/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class RollbackStagePlanCreator {
  public PlanCreationResponse createPlanForRollbackStage(YamlField stageYamlField, KryoSerializer kryoSerializer) {
    YamlNode stageNode = stageYamlField.getNode();
    if (Objects.equals(stageNode.getFieldName(), YAMLFieldNameConstants.PARALLEL)) {
      return createPlanForParallelBlock(stageNode, kryoSerializer);
    }
    return createPlanForSingleStage(stageNode, kryoSerializer);
  }

  PlanCreationResponse createPlanForSingleStage(YamlNode stageNode, KryoSerializer kryoSerializer) {
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
            .adviserObtainments(getAdvisor(kryoSerializer, stageNode))
            .skipExpressionChain(true)
            .build();
    return PlanCreationResponse.builder().node(rollbackStagePlanNode.getUuid(), rollbackStagePlanNode).build();
  }

  List<AdviserObtainment> getAdvisor(KryoSerializer kryoSerializer, YamlNode stageNode) {
    YamlField previousStageField = stageNode.previousSiblingFromParentArray(
        stageNode.getFieldName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
    if (previousStageField == null) {
      return Collections.emptyList();
    }
    String previousStageUuid = previousStageField.getNode().getUuid();
    if (EmptyPredicate.isEmpty(previousStageUuid)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(
        AdviserObtainment.newBuilder()
            .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()))
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                NextStepAdviserParameters.builder()
                    .nextNodeId(previousStageUuid + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
                    .build())))
            .build());
  }

  PlanCreationResponse createPlanForParallelBlock(YamlNode parallelStageNode, KryoSerializer kryoSerializer) {
    List<YamlNode> stageNodes = parallelStageNode.asArray();
    PlanCreationResponse planCreationResponse = PlanCreationResponse.builder().build();

    List<String> childNodeIDs = new ArrayList<>();
    stageNodes.forEach(stageNode -> {
      YamlNode stageNodeInternal = stageNode.getFieldOrThrow(YAMLFieldNameConstants.STAGE).getNode();
      planCreationResponse.merge(createPlanForSingleStage(stageNodeInternal, kryoSerializer));
      childNodeIDs.add(stageNodeInternal.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX);
    });
    PlanNode parallelRollbackPlanNode =
        PlanNode.builder()
            .uuid(parallelStageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
            .name("Parallel Block" + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_NODE_NAME)
            .identifier(parallelStageNode.getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
            .stepType(NGForkStep.STEP_TYPE)
            .stepParameters(ForkStepParameters.builder().parallelNodeIds(childNodeIDs).build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .adviserObtainments(getAdvisor(kryoSerializer, parallelStageNode))
            .skipExpressionChain(true)
            .build();
    PlanCreationResponse parallelBlockResponse =
        PlanCreationResponse.builder().node(parallelRollbackPlanNode.getUuid(), parallelRollbackPlanNode).build();
    planCreationResponse.merge(parallelBlockResponse);
    return planCreationResponse;
  }
}
