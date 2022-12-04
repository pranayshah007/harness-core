/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.creator.plan.steps.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStepNode;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class TerragruntPlanStepPlanCreator extends CDPMSStepPlanCreatorV2<TerragruntPlanStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.TERRAGRUNT_PLAN);
  }

  @Override
  public Class<TerragruntPlanStepNode> getFieldClass() {
    return TerragruntPlanStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TerragruntPlanStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TerragruntPlanStepNode stepElement) {
    StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String stepFqn = YamlUtils.getFullyQualifiedName(ctx.getCurrentField().getNode());
    TerragruntPlanStepParameters terragruntPlanStepParameters =
        (TerragruntPlanStepParameters) ((StepElementParameters) stepParameters).getSpec();
    terragruntPlanStepParameters.setStepFqn(stepFqn);

    return stepParameters;
  }
}
