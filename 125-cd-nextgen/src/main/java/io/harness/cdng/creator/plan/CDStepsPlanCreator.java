/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.advisers.RollbackCustomAdviser;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.plancreator.steps.GenericStepsNodePlanCreator;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;

import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public class CDStepsPlanCreator extends GenericStepsNodePlanCreator {
  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepsExecutionConfig config, List<String> childrenNodeIds) {
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).build();
    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(ctx.getCurrentField().getNode().getUuid())
            .identifier(YAMLFieldNameConstants.STEPS)
            .stepType(NGSectionStepWithRollbackInfo.STEP_TYPE)
            .name(YAMLFieldNameConstants.STEPS)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipGraphType(SkipType.SKIP_NODE);

    if (YamlUtils.findParentNode(ctx.getCurrentField().getNode(), STEP_GROUP) != null) {
      return planNodeBuilder.build();
    }

    // todo: correct advisor for rollback mode
    return planNodeBuilder
        .adviserObtainment(AdviserObtainment.newBuilder().setType(RollbackCustomAdviser.ADVISER_TYPE).build())
        .advisorObtainmentsForRollbackMode(Collections.singletonList(
            AdviserObtainment.newBuilder().setType(RollbackCustomAdviser.ADVISER_TYPE).build()))
        .build();
  }
}
