/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class BarrierStepPlanCreator extends PMSStepPlanCreatorV2<BarrierStepNode> {
  @Inject private BarrierService barrierService;
  @Inject private PmsFeatureFlagService featureFlagService;

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
    if (featureFlagService.isEnabled(
            ctx.getAccountIdentifier(), FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES)) {
      if (StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())) {
        throw new InvalidRequestException("Barrier step cannot be configured with looping strategy.");
      }
      String planExecutionId = ctx.getExecutionUuid();
      String parentInfoStrategyNodeType =
          PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STRATEGY_NODE_TYPE, ctx).getStringValue();
      String stageId = PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STAGE_ID, ctx).getStringValue();
      String stepGroupId =
          PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STEP_GROUP_ID, ctx).getStringValue();
      String strategyId =
          PlanCreatorUtilsCommon.getFromParentInfo(PlanCreatorConstants.STRATEGY_ID, ctx).getStringValue();
      barrierService.upsertBarrierExecutionInstance(
          field, planExecutionId, parentInfoStrategyNodeType, stageId, stepGroupId, strategyId);
    }
    return super.createPlanForField(ctx, field);
  }
}
