/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.ecs;

import static io.harness.cdng.visitor.YamlTypes.ECS_SERVICE_SETUP;
import static io.harness.cdng.visitor.YamlTypes.ECS_UPGRADE_CONTAINER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsBasicRollbackStepNode;
import io.harness.cdng.ecs.EcsBasicRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
public class EcsBasicRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsBasicRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_BASIC_ROLLBACK);
  }

  @Override
  public Class<EcsBasicRollbackStepNode> getFieldClass() {
    return EcsBasicRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsBasicRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, EcsBasicRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String ecsServiceSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), ECS_SERVICE_SETUP);
    String ecsUpgradeContainerFqn = getExecutionStepFqn(ctx.getCurrentField(), ECS_UPGRADE_CONTAINER);
    EcsBasicRollbackStepParameters ecsBasicRollbackStepParameters =
        (EcsBasicRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    ecsBasicRollbackStepParameters.setEcsServiceSetupFqn(ecsServiceSetupFqn);
    ecsBasicRollbackStepParameters.setEcsUpgradeContainerFqn(ecsUpgradeContainerFqn);
    return stepParameters;
  }
}
