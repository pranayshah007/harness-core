/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.step;

import io.harness.beans.steps.nodes.iacm.IACMTerraformPluginStepNode;
import io.harness.beans.steps.stepinfo.IACMStepInfoType;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class IACMTerraformPluginStepPlanCreator extends CIPMSStepPlanCreatorV2<IACMTerraformPluginStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(IACMStepInfoType.IACM_TERRAFORM_PLUGIN.getDisplayName());
  }

  @Override
  public Class<IACMTerraformPluginStepNode> getFieldClass() {
    return IACMTerraformPluginStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, IACMTerraformPluginStepNode stepElement) {
    if (stepElement.getStepSpecType().getStepType().getType().equals(
            IACMTerraformPluginStepNode.StepType.IACMTerraformPlugin.getName())
        && stepElement.getIacmTerraformPluginInfo().getCommand().getValue() == null) {
      throw new IACMStageExecutionException(
          "The step " + stepElement.getName() + " is missing the operations field that is required");
    }
    return super.createPlanForField(ctx, stepElement);
  }
}
