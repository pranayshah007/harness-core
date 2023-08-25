/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import io.harness.distribution.barrier.Barrier;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;

import java.util.List;
import java.util.Set;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

public class BarrierStepPlanCreator extends PMSStepPlanCreatorV2<BarrierStepNode> {
  @Inject KryoSerializer kryoSerializer;
  @Inject private BarrierService barrierService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.BARRIER);
  }

  @Override
  public Class<BarrierStepNode> getFieldClass() {
    return BarrierStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, BarrierStepNode field) {
    String planId = null;
    ByteString planIdBytes = ctx.getDependency().getMetadataMap().get("planId");
    if (planIdBytes != null) {
      planId = (String) kryoSerializer.asObject(planIdBytes.toByteArray());
    }
    String stageId = null;
    ByteString stageIdBytes = ctx.getDependency().getMetadataMap().get("stageId");
    if (stageIdBytes != null) {
      stageId = (String) kryoSerializer.asObject(stageIdBytes.toByteArray());
    }
    String stepGroupId = null;
    ByteString stepGroupIdBytes = ctx.getDependency().getMetadataMap().get("stepGroupId");
    if (stepGroupIdBytes != null) {
      stepGroupId = (String) kryoSerializer.asObject(stepGroupIdBytes.toByteArray());
    }
    String strategyId = null;
    String strategyNodeType = null;
    ByteString strategyIdBytes = ctx.getDependency().getMetadataMap().get("strategyId");
    if (strategyIdBytes != null) {
      strategyId = (String) kryoSerializer.asObject(strategyIdBytes.toByteArray());
      if (strategyId.equals(stepGroupId)) {
        strategyNodeType = "stepGroup";
      }
      else if (strategyId.equals(stageId)) {
        strategyNodeType = "stage";
      }
    }
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder()
            .setupInfo(
                    BarrierSetupInfo.builder()
                            .name(field.getBarrierStepInfo().getName())
                            .identifier(field.getBarrierStepInfo().getIdentifier())
                            .stages(Set.of(StageDetail.builder().identifier(stageId).build()))
                            .strategySetupId(strategyId)
                            .strategyNodeType(strategyNodeType)
                            .build()
            )
            .positionInfo(BarrierPositionInfo.builder()
                    .planExecutionId(planId)
                    .barrierPositionList(
                            List.of(BarrierPositionInfo.BarrierPosition.builder()
                                    .stageSetupId(stageId)
                                    .stepGroupSetupId(stepGroupId)
                                    .stepSetupId(field.getUuid())
                                    .stepGroupRollback(false)
//                                   TODO : Need to populate this field correctly
//                                    .stepGroupRollback(isInsideStepGroupRollback(element))
                                    .build()
                            )
                    )
                    .build())
            .name(field.getBarrierStepInfo().getName())
            .barrierState(Barrier.State.STANDING)
            .identifier(field.getBarrierStepInfo().getIdentifier())
            .planExecutionId(planId)
            .strategyExecutionId(strategyId)
            .build();
    // TODO: Add lock here.
    barrierService.upsert(barrierExecutionInstance);
    return super.createPlanForField(ctx, field);
  }
}
