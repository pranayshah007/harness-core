/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP_CHILD_NODE_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGStageStepsPlanCreator extends GenericStepsNodePlanCreator {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepsExecutionConfig config, List<String> childrenNodeIds) {
    String childNodeId = childrenNodeIds.get(0);
    if (ctx.getDependency() != null && ctx.getDependency().getMetadataMap().containsKey(STEP_GROUP_CHILD_NODE_ID)) {
      ByteString childNodeIdData = ctx.getDependency().getMetadataMap().get(STEP_GROUP_CHILD_NODE_ID);
      childNodeId = (String) kryoSerializer.asInflatedObject(childNodeIdData.toByteArray());
    }
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childNodeId).build();
    return PlanNode.builder()
        .uuid(ctx.getCurrentField().getNode().getUuid())
        .identifier(YAMLFieldNameConstants.STEPS)
        .stepType(NGSectionStepWithRollbackInfo.STEP_TYPE)
        .name(YAMLFieldNameConstants.STEPS)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }
}
