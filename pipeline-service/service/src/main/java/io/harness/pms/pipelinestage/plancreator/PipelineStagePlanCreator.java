/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.internal.PmsStepPlanCreatorUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.step.PipelineStageStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.when.utils.RunInfoUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineStagePlanCreator implements PartialPlanCreator<PipelineStageNode> {
  @Inject KryoSerializer kryoSerializer;
  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE));
  }

  public PipelineStageStepParameters getStepParameter(PipelineStageConfig config) {
    return PipelineStageStepParameters.builder()
        .pipeline(config.getPipeline())
        .org(config.getOrg())
        .project(config.getProject())
        .inputSetReferences(config.getInputSetReferences())
        .pipelineInputs(config.getPipelineInputs())
        .build();
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PipelineStageNode stageNode) {
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepCategory.STAGE.name())
            .stepType(PipelineStageStep.STEP_TYPE)
            .stepParameters(getStepParameter(stageNode.getPipelineStageConfig()))
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunCondition(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .adviserObtainments(
                PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(kryoSerializer, ctx.getCurrentField(), true));
    if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
      builder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }

    return PlanCreationResponse.builder().planNode(builder.build()).build();
  }
}
