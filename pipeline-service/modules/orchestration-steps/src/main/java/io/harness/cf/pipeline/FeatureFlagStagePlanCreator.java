/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cf.pipeline;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.cf.FeatureFlagStageStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.when.utils.RunInfoUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO we should implement GenericStagePlanCreator however we need to understand how
// the getSpecParameters method should behave for CF.

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CF)
public class FeatureFlagStagePlanCreator extends ChildrenPlanCreator<StageElementConfig> {
  @Inject private KryoSerializer kryoSerializer;
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StageElementConfig config) {
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    // Add dependency for execution
    YamlField executionField =
        ctx.getCurrentField().getNode().getField("spec").getNode().getField(YAMLFieldNameConstants.EXECUTION);
    if (executionField == null) {
      throw new InvalidRequestException("Execution section cannot be absent in a pipeline");
    }
    dependenciesNodeMap.put(executionField.getNode().getUuid(), executionField);

    planCreationResponseMap.put(executionField.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap))
            .build());
    return planCreationResponseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StageElementConfig config, List<String> childrenNodeIds) {
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).logMessage("Execution Element").build();

    return PlanNode.builder()
        .uuid(config.getUuid())
        .name(config.getName())
        .identifier(config.getIdentifier())
        .group(StepOutcomeGroup.STAGE.name())
        .stepParameters(stepParameters)
        .stepType(FeatureFlagStageStep.STEP_TYPE)
        .skipCondition(config.getSkipCondition() != null ? config.getSkipCondition().getValue() : null)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .whenCondition(RunInfoUtils.getRunConditionForStage(config.getWhen()))
        .adviserObtainments(getAdviserObtainmentFromMetaData(ctx.getCurrentField()))
        .build();
  }

  @Override
  public Class<StageElementConfig> getFieldClass() {
    return StageElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STAGE, Collections.singleton("FeatureFlag"));
  }

  /**
   * Adds the nextStepAdviser to the given node if it is not the end stage
   */
  private List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField stageField) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (stageField != null && stageField.getNode() != null) {
      // if parent is parallel, then we need not add nextStepAdvise as all the executions will happen in parallel
      if (stageField.checkIfParentIsParallel(STAGES)) {
        return adviserObtainments;
      }
      YamlField siblingField = stageField.getNode().nextSiblingFromParentArray(
          stageField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
      // Comparing pipelineRollbackStageId with siblingFieldUuid to add rollback stage if required
      String pipelineRollbackStageId = StrategyUtils.getPipelineRollbackStageId(stageField);
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        String siblingFieldUuid = siblingField.getNode().getUuid();
        adviserObtainments.add(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
                .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                    NextStepAdviserParameters.builder()
                        .nextNodeId(siblingFieldUuid.equals(pipelineRollbackStageId) ? null : siblingFieldUuid)
                        .build())))
                .build());
      }
    }
    return adviserObtainments;
  }
}
