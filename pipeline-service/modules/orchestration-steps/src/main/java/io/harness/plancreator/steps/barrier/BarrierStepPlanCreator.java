/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;

import java.util.Set;

public class BarrierStepPlanCreator extends PMSStepPlanCreatorV2<BarrierStepNode> {
  @Inject KryoSerializer kryoSerializer;

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
    ByteString strategyIdBytes = ctx.getDependency().getMetadataMap().get("strategyId");
    if (strategyIdBytes != null) {
      strategyId = (String) kryoSerializer.asObject(strategyIdBytes.toByteArray());
    }
    return super.createPlanForField(ctx, field);
  }
}
